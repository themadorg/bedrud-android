package ports

import (
	"bufio"
	"fmt"
	"net"
	"os"
	"os/exec"
	"regexp"
	"strconv"
	"strings"
	"time"
)

// Binding maps a devcli service to its primary TCP listen port.
type Binding struct {
	Service string
	Port    int
}

// DevBindings are checked before starting the dev stack.
var DevBindings = []Binding{
	{Service: "livekit", Port: LiveKit},
	{Service: "api", Port: API},
	{Service: "web", Port: Web},
}

// RemoteBindings are checked before starting remote debug (no local LiveKit).
var RemoteBindings = []Binding{
	{Service: "api", Port: API},
	{Service: "web", Port: Web},
}

// Conflict describes a port already in use by another process.
type Conflict struct {
	Service string
	Port    int
	PID     int
	Process string
}

// FindConflicts returns ports required by the dev stack that are already bound.
func FindConflicts() ([]Conflict, error) {
	var out []Conflict
	for _, b := range DevBindings {
		if !portBusy(b.Port) {
			continue
		}
		pid, proc := lookupProcess(b.Port)
		out = append(out, Conflict{
			Service: b.Service,
			Port:    b.Port,
			PID:     pid,
			Process: proc,
		})
	}
	return out, nil
}

func portBusy(port int) bool {
	ln, err := net.Listen("tcp", fmt.Sprintf(":%d", port))
	if err != nil {
		return true
	}
	_ = ln.Close()
	return false
}

var ssPIDRe = regexp.MustCompile(`pid=(\d+)`)
var ssNameRe = regexp.MustCompile(`\("([^"]+)"`)

func lookupProcess(port int) (pid int, process string) {
	if p, n := lookupViaSS(port); p > 0 {
		return p, n
	}
	if p, n := lookupViaLsof(port); p > 0 {
		return p, n
	}
	if p, n := lookupViaFuser(port); p > 0 {
		return p, n
	}
	return 0, "(unknown)"
}

func lookupViaSS(port int) (int, string) {
	path, err := exec.LookPath("ss")
	if err != nil {
		return 0, ""
	}
	out, err := exec.Command(path, "-ltnp", fmt.Sprintf("sport = :%d", port)).Output()
	if err != nil || len(out) == 0 {
		return 0, ""
	}
	line := string(out)
	pid := 0
	if m := ssPIDRe.FindStringSubmatch(line); len(m) > 1 {
		pid, _ = strconv.Atoi(m[1])
	}
	name := ""
	if m := ssNameRe.FindStringSubmatch(line); len(m) > 1 {
		name = m[1]
	}
	if pid > 0 && name == "" {
		name = procCmdline(pid)
	}
	return pid, name
}

func lookupViaLsof(port int) (int, string) {
	path, err := exec.LookPath("lsof")
	if err != nil {
		return 0, ""
	}
	out, err := exec.Command(path, "-nP", "-iTCP:"+strconv.Itoa(port), "-sTCP:LISTEN", "-t").Output()
	if err != nil || len(out) == 0 {
		return 0, ""
	}
	pid, err := strconv.Atoi(strings.TrimSpace(string(out)))
	if err != nil || pid <= 0 {
		return 0, ""
	}
	return pid, procCmdline(pid)
}

func lookupViaFuser(port int) (int, string) {
	path, err := exec.LookPath("fuser")
	if err != nil {
		return 0, ""
	}
	out, err := exec.Command(path, fmt.Sprintf("%d/tcp", port)).CombinedOutput()
	if err != nil || len(out) == 0 {
		return 0, ""
	}
	fields := strings.Fields(string(out))
	if len(fields) == 0 {
		return 0, ""
	}
	pid, err := strconv.Atoi(strings.TrimSuffix(fields[len(fields)-1], "\n"))
	if err != nil || pid <= 0 {
		return 0, ""
	}
	return pid, procCmdline(pid)
}

func procCmdline(pid int) string {
	data, err := os.ReadFile(fmt.Sprintf("/proc/%d/cmdline", pid))
	if err != nil {
		return ""
	}
	s := strings.TrimSpace(strings.ReplaceAll(string(data), "\x00", " "))
	if len(s) > 120 {
		s = s[:117] + "..."
	}
	return s
}

// FormatConflicts renders a human-readable conflict report.
func FormatConflicts(conflicts []Conflict) string {
	var b strings.Builder
	b.WriteString("dev ports already in use:\n")
	for _, c := range conflicts {
		if c.PID > 0 {
			fmt.Fprintf(&b, "  • %s :%d — pid %d (%s)\n", c.Service, c.Port, c.PID, c.Process)
		} else {
			fmt.Fprintf(&b, "  • %s :%d — process unknown\n", c.Service, c.Port)
		}
	}
	b.WriteString("\nstop them with: make dev-stop-all\n")
	b.WriteString("             or: devcli stop\n")
	return b.String()
}

// FindRemoteConflicts returns port conflicts for remote debug mode.
func FindRemoteConflicts() ([]Conflict, error) {
	var out []Conflict
	for _, b := range RemoteBindings {
		if !portBusy(b.Port) {
			continue
		}
		pid, proc := lookupProcess(b.Port)
		out = append(out, Conflict{
			Service: b.Service,
			Port:    b.Port,
			PID:     pid,
			Process: proc,
		})
	}
	return out, nil
}

// ResolveRemoteConflicts checks api/web ports for remote debug mode.
func ResolveRemoteConflicts(repo string, autoStop bool) error {
	return resolveBindings(FindRemoteConflicts, repo, autoStop)
}

// PrepareRemoteDev stops stale devcli/api/web processes before remote debug startup.
// Call once before starting the tunnel — not from the runner (avoids killing a live tunnel mid-boot).
func PrepareRemoteDev(repo string, autoStop bool) error {
	if repo != "" && autoStop {
		if StopOrchestratorExcept(os.Getpid(), repo) {
			fmt.Fprintln(os.Stderr, "devcli | stopped previous devcli session")
			time.Sleep(400 * time.Millisecond)
		}
		if stopped, err := StopDev(repo); err != nil {
			return fmt.Errorf("failed to stop dev processes: %w", err)
		} else if stopped {
			fmt.Fprintln(os.Stderr, "✅ Dev processes stopped")
		}
		time.Sleep(200 * time.Millisecond)
	}
	return resolveBindings(FindRemoteConflicts, repo, autoStop)
}

func resolveBindings(find func() ([]Conflict, error), repo string, autoStop bool) error {
	conflicts, err := find()
	if err != nil {
		return err
	}
	if len(conflicts) == 0 {
		return nil
	}
	fmt.Fprint(os.Stderr, FormatConflicts(conflicts))
	if !autoStop && !stdinIsTTY() {
		return fmt.Errorf("aborting: free the ports above, then retry")
	}
	if !autoStop {
		fmt.Fprint(os.Stderr, "Stop conflicting processes and continue? [y/N] ")
		reader := bufio.NewReader(os.Stdin)
		answer, _ := reader.ReadString('\n')
		answer = strings.TrimSpace(strings.ToLower(answer))
		if answer != "y" && answer != "yes" {
			return fmt.Errorf("aborting: ports still in use")
		}
	} else {
		fmt.Fprintln(os.Stderr, "devcli | auto-stopping conflicting processes...")
	}
	if repo != "" {
		stopped, err := StopDev(repo)
		if err != nil {
			return fmt.Errorf("failed to stop dev processes: %w", err)
		}
		if stopped {
			fmt.Fprintln(os.Stderr, "✅ Dev processes stopped")
		}
	}
	conflicts, err = find()
	if err != nil {
		return err
	}
	if len(conflicts) > 0 {
		fmt.Fprint(os.Stderr, FormatConflicts(conflicts))
		return fmt.Errorf("ports still in use after stop — free them manually")
	}
	return nil
}

// ResolveConflicts checks ports and optionally stops conflicting processes.
func ResolveConflicts(repo string, autoStop bool) error {
	return resolveBindings(FindConflicts, repo, autoStop)
}

func stdinIsTTY() bool {
	fi, err := os.Stdin.Stat()
	if err != nil {
		return false
	}
	return (fi.Mode() & os.ModeCharDevice) != 0
}