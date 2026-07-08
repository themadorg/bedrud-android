package ports

import (
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strconv"
	"strings"
	"syscall"
	"time"
)

// StopDev terminates Bedrud dev stack processes (parents first, then port listeners).
func StopDev(repo string) (bool, error) {
	repo, err := filepath.Abs(repo)
	if err != nil {
		return false, err
	}
	serverDir := filepath.Join(repo, "server")
	webDir := filepath.Join(repo, "apps", "web")

	stopped := false

	// Parents before port kills — otherwise air respawns ./tmp/server on :7071.
	for _, pid := range findPIDs("air") {
		if procCwd(pid) == serverDir {
			if signalPID(pid, syscall.SIGTERM) {
				stopped = true
			}
		}
	}
	for _, pid := range findPIDs("livekit-server") {
		if strings.Contains(procCmdline(pid), filepath.Join(repo, "server", "livekit.yaml")) {
			if signalPID(pid, syscall.SIGTERM) {
				stopped = true
			}
		}
	}
	for _, pid := range findPIDs("bun") {
		cmd := procCmdline(pid)
		if strings.Contains(cmd, "run dev") && strings.Contains(procCwd(pid), webDir) {
			if signalPID(pid, syscall.SIGTERM) {
				stopped = true
			}
		}
	}

	time.Sleep(300 * time.Millisecond)

	// Orphan API binary (air child) and anything still bound to dev ports.
	for _, port := range allStopPorts() {
		for _, pid := range listenersOnPort(port) {
			if signalPID(pid, syscall.SIGTERM) {
				stopped = true
			}
		}
	}

	// tmp/server in repo even if not listening yet.
	for _, pid := range findPIDs("server") {
		exe, _ := os.Readlink(fmt.Sprintf("/proc/%d/exe", pid))
		if strings.HasPrefix(exe, filepath.Join(serverDir, "tmp", "server")) ||
			procCwd(pid) == serverDir && strings.Contains(procCmdline(pid), "tmp/server") {
			if signalPID(pid, syscall.SIGTERM) {
				stopped = true
			}
		}
	}

	time.Sleep(400 * time.Millisecond)

	for _, port := range allStopPorts() {
		for _, pid := range listenersOnPort(port) {
			if signalPID(pid, syscall.SIGKILL) {
				stopped = true
			}
		}
	}
	for _, pid := range findPIDs("air") {
		if procCwd(pid) == serverDir && signalPID(pid, syscall.SIGKILL) {
			stopped = true
		}
	}
	for _, pid := range findPIDs("livekit-server") {
		if strings.Contains(procCmdline(pid), filepath.Join(repo, "server", "livekit.yaml")) &&
			signalPID(pid, syscall.SIGKILL) {
			stopped = true
		}
	}
	for _, pid := range findPIDs("bun") {
		cmd := procCmdline(pid)
		if strings.Contains(cmd, "run dev") && strings.Contains(procCwd(pid), webDir) &&
			signalPID(pid, syscall.SIGKILL) {
			stopped = true
		}
	}
	for _, pid := range findPIDs("server") {
		exe, _ := os.Readlink(fmt.Sprintf("/proc/%d/exe", pid))
		if strings.HasPrefix(exe, filepath.Join(serverDir, "tmp", "server")) &&
			signalPID(pid, syscall.SIGKILL) {
			stopped = true
		}
	}

	for _, pid := range findRepoVitePIDs(repo) {
		if signalPID(pid, syscall.SIGTERM) {
			stopped = true
		}
	}

	time.Sleep(200 * time.Millisecond)

	for _, pid := range findRepoVitePIDs(repo) {
		if signalPID(pid, syscall.SIGKILL) {
			stopped = true
		}
	}

	return stopped, nil
}

// StopOrchestrator terminates devcli run/remote run parents for this repo.
func StopOrchestrator(repo string) bool {
	return StopOrchestratorExcept(0, repo)
}

// StopOrchestratorExcept terminates other devcli orchestrators, skipping exceptPID (0 = none).
func StopOrchestratorExcept(exceptPID int, repo string) bool {
	repo, err := filepath.Abs(repo)
	if err != nil {
		return false
	}

	stopped := false
	for _, pid := range findRepoDevCLIPIDs(repo) {
		if exceptPID > 0 && pid == exceptPID {
			continue
		}
		if signalPID(pid, syscall.SIGTERM) {
			stopped = true
		}
	}
	if !stopped {
		return false
	}

	time.Sleep(200 * time.Millisecond)
	for _, pid := range findRepoDevCLIPIDs(repo) {
		if exceptPID > 0 && pid == exceptPID {
			continue
		}
		if signalPID(pid, syscall.SIGKILL) {
			stopped = true
		}
	}
	return stopped
}

func findRepoDevCLIPIDs(repo string) []int {
	devcliBin := filepath.Join(repo, "apps", "dev", "devcli", "bin", "devcli")
	var pids []int
	for _, pid := range findPIDsByPattern(devcliBin) {
		if isBedrudDevCLI(pid, devcliBin) {
			pids = append(pids, pid)
		}
	}
	return pids
}

func findRepoVitePIDs(repo string) []int {
	webDir := filepath.Join(repo, "apps", "web")
	marker := filepath.Join(webDir, "node_modules")
	var pids []int
	for _, pid := range findPIDsByPattern(marker) {
		cmd := procCmdlineFull(pid)
		if strings.Contains(cmd, marker) && strings.Contains(cmd, "vite") {
			pids = append(pids, pid)
		}
	}
	return pids
}

func isBedrudDevCLI(pid int, devcliBin string) bool {
	cmd := procCmdlineFull(pid)
	if !strings.Contains(cmd, devcliBin) {
		return false
	}
	return strings.Contains(cmd, " run") || strings.Contains(cmd, " remote run")
}

func findPIDsByPattern(pattern string) []int {
	out, err := exec.Command("pgrep", "-f", pattern).Output()
	if err != nil {
		return nil
	}
	var pids []int
	for _, line := range strings.Split(strings.TrimSpace(string(out)), "\n") {
		if line == "" {
			continue
		}
		if pid, err := strconv.Atoi(line); err == nil && pid > 0 {
			pids = append(pids, pid)
		}
	}
	return pids
}

func procCmdlineFull(pid int) string {
	data, err := os.ReadFile(fmt.Sprintf("/proc/%d/cmdline", pid))
	if err != nil {
		return ""
	}
	return strings.ReplaceAll(string(data), "\x00", " ")
}

func allStopPorts() []int {
	ports := append([]int{}, DevTCPPorts...)
	for p := RTCStart; p <= RTCEnd; p++ {
		ports = append(ports, p)
	}
	return ports
}

func findPIDs(name string) []int {
	out, err := exec.Command("pgrep", "-x", name).Output()
	if err != nil {
		return nil
	}
	var pids []int
	for _, line := range strings.Split(strings.TrimSpace(string(out)), "\n") {
		if line == "" {
			continue
		}
		if pid, err := strconv.Atoi(line); err == nil && pid > 0 {
			pids = append(pids, pid)
		}
	}
	return pids
}

func listenersOnPort(port int) []int {
	seen := map[int]struct{}{}
	var pids []int
	add := func(pid int) {
		if pid <= 0 {
			return
		}
		if _, ok := seen[pid]; ok {
			return
		}
		seen[pid] = struct{}{}
		pids = append(pids, pid)
	}

	if path, err := exec.LookPath("lsof"); err == nil {
		out, err := exec.Command(path, "-nP", "-iTCP:"+strconv.Itoa(port), "-sTCP:LISTEN", "-t").Output()
		if err == nil {
			for _, line := range strings.Split(strings.TrimSpace(string(out)), "\n") {
				pid, _ := strconv.Atoi(strings.TrimSpace(line))
				add(pid)
			}
		}
	}
	if path, err := exec.LookPath("fuser"); err == nil {
		out, err := exec.Command(path, fmt.Sprintf("%d/tcp", port)).CombinedOutput()
		if err == nil {
			for _, field := range strings.Fields(string(out)) {
				pid, _ := strconv.Atoi(strings.TrimSuffix(field, "\n"))
				add(pid)
			}
		}
	}
	return pids
}

func procCwd(pid int) string {
	cwd, err := os.Readlink(fmt.Sprintf("/proc/%d/cwd", pid))
	if err != nil {
		return ""
	}
	abs, err := filepath.Abs(cwd)
	if err != nil {
		return cwd
	}
	return abs
}

func signalPID(pid int, sig syscall.Signal) bool {
	// Prefer killing the whole process group (air/bun children).
	if pgid, err := syscall.Getpgid(pid); err == nil && pgid > 0 {
		if err := syscall.Kill(-pgid, sig); err == nil {
			return true
		}
	}
	p, err := os.FindProcess(pid)
	if err != nil {
		return false
	}
	return p.Signal(sig) == nil
}