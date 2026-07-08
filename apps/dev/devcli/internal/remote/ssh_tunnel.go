package remote

import (
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strconv"
	"strings"
	"syscall"
	"time"

	"bedrud/devcli/internal/logfmt"
)

const (
	defaultSSHTunnelPIDFile         = "~/.config/bedrud/ssh-tunnel.pid"
	defaultSSHTunnelLiveKitPIDFile  = "~/.config/bedrud/ssh-tunnel-livekit.pid"
	defaultSSHTunnelBackendsPIDFile = "~/.config/bedrud/ssh-tunnel-backends.pid"
)

type sshTunnelLeg string

const (
	sshTunnelLegLiveKit  sshTunnelLeg = "livekit"
	sshTunnelLegBackends sshTunnelLeg = "backends"
)

// SSHTunnelState reports whether a background SSH port-forward session is running.
type SSHTunnelState struct {
	Up      bool
	PID     int
	PIDFile string
}

// SSHTunnelUp starts LiveKit and backend SSH forwards for remote debug.
func SSHTunnelUp(cfg *Config) error {
	if err := pingSSH(cfg); err != nil {
		return err
	}
	if err := sshTunnelLiveKitUp(cfg); err != nil {
		return err
	}
	return sshTunnelBackendsUp(cfg)
}

// TunnelEnsureLiveKit brings up the local LiveKit forward before the API starts.
func TunnelEnsureLiveKit(cfg *Config) error {
	if err := pingSSH(cfg); err != nil {
		return err
	}
	return sshTunnelLiveKitUp(cfg)
}

// SSHTunnelRefresh tears down and restarts backend reverse forwards after local api/web listen.
func SSHTunnelRefresh(cfg *Config) error {
	if err := pingSSH(cfg); err != nil {
		return err
	}
	_, _ = sshTunnelLegDown(cfg, sshTunnelLegBackends)
	if err := PruneStaleRemoteBackendPorts(cfg); err != nil {
		return err
	}
	if err := sshTunnelBackendsUp(cfg); err != nil {
		return err
	}
	return waitSSHProcessReady(readSSHTunnelLegPID, cfg, sshTunnelLegBackends, 5*time.Second)
}

// TunnelEnsureBackends brings up reverse forwards to local Vite/API.
func TunnelEnsureBackends(cfg *Config) error {
	return SSHTunnelRefresh(cfg)
}

func sshTunnelLiveKitUp(cfg *Config) error {
	if st, _ := readSSHTunnelLegState(cfg, sshTunnelLegLiveKit); st != nil && st.Up {
		logfmt.Println("ssh-tunnel", fmt.Sprintf("livekit forward already up (pid %d)", st.PID))
		return nil
	}
	_, _ = sshTunnelLegDown(cfg, sshTunnelLegLiveKit)
	args := buildSSHTunnelLiveKitArgs(cfg)
	pid, err := sshTunnelLegStart(cfg, sshTunnelLegLiveKit, args)
	if err != nil {
		return err
	}
	logfmt.Println("ssh-tunnel", fmt.Sprintf("livekit forward up pid %d (127.0.0.1:%d → remote :%d)",
		pid, cfg.Tunnel.SSH.LocalLiveKitPort, cfg.Traefik.LiveKitPort))
	return nil
}

func sshTunnelBackendsUp(cfg *Config) error {
	if st, _ := readSSHTunnelLegState(cfg, sshTunnelLegBackends); st != nil && st.Up {
		logfmt.Println("ssh-tunnel", fmt.Sprintf("backends already up (pid %d)", st.PID))
		return nil
	}
	_, _ = sshTunnelLegDown(cfg, sshTunnelLegBackends)
	args := buildSSHTunnelBackendsArgs(cfg)
	pid, err := sshTunnelLegStart(cfg, sshTunnelLegBackends, args)
	if err != nil {
		return err
	}
	logfmt.Println("ssh-tunnel", fmt.Sprintf("backends up pid %d (remote traefik → 127.0.0.1:%d web, 127.0.0.1:%d api)",
		pid, cfg.Tunnel.SSH.RemoteWebPort, cfg.Tunnel.SSH.RemoteAPIPort))
	return nil
}

func sshTunnelLegStart(cfg *Config, leg sshTunnelLeg, args []string) (int, error) {
	cmd := exec.Command("ssh", args...)
	cmd.Stdout = nil
	cmd.Stderr = nil
	if err := cmd.Start(); err != nil {
		return 0, fmt.Errorf("ssh %s tunnel: %w", leg, err)
	}

	pid := cmd.Process.Pid
	if err := writeSSHTunnelLegPID(cfg, leg, pid); err != nil {
		_ = cmd.Process.Kill()
		return 0, err
	}
	_ = cmd.Process.Release()

	deadline := time.Now().Add(2 * time.Second)
	for time.Now().Before(deadline) {
		if sshProcessAlive(pid) {
			return pid, nil
		}
		time.Sleep(150 * time.Millisecond)
	}
	_ = os.Remove(sshtunnelLegPIDPath(cfg, leg))
	if leg == sshTunnelLegBackends {
		return 0, fmt.Errorf("ssh backends tunnel exited (remote :%d/: %d likely held by stale forward — retry or run: devcli remote tunnel down && make dev-remote)",
			cfg.Tunnel.SSH.RemoteWebPort, cfg.Tunnel.SSH.RemoteAPIPort)
	}
	return 0, fmt.Errorf("ssh %s tunnel exited immediately (check SSH auth and remote ports)", leg)
}

func waitSSHProcessReady(readPID func(*Config, sshTunnelLeg) (int, string, error), cfg *Config, leg sshTunnelLeg, timeout time.Duration) error {
	deadline := time.Now().Add(timeout)
	for time.Now().Before(deadline) {
		pid, _, err := readPID(cfg, leg)
		if err == nil && sshProcessAlive(pid) {
			return nil
		}
		time.Sleep(150 * time.Millisecond)
	}
	return fmt.Errorf("ssh %s tunnel not running after %s", leg, timeout)
}

// SSHTunnelDown stops all SSH port-forward sessions.
func SSHTunnelDown(cfg *Config) (bool, error) {
	changed := false
	for _, leg := range []sshTunnelLeg{sshTunnelLegLiveKit, sshTunnelLegBackends} {
		stopped, err := sshTunnelLegDown(cfg, leg)
		if err != nil {
			return changed, err
		}
		if stopped {
			changed = true
		}
	}
	// Legacy single-session pid file from older devcli versions.
	if stopped, err := sshTunnelLegacyDown(cfg); err != nil {
		return changed, err
	} else if stopped {
		changed = true
	}
	return changed, nil
}

func sshTunnelLegDown(cfg *Config, leg sshTunnelLeg) (bool, error) {
	pid, pidFile, err := readSSHTunnelLegPID(cfg, leg)
	if err != nil {
		if os.IsNotExist(err) {
			return false, nil
		}
		return false, err
	}

	changed := false
	if pid == 0 {
		_ = os.Remove(pidFile)
		return false, nil
	}
	if sshProcessAlive(pid) {
		changed = true
		proc, err := os.FindProcess(pid)
		if err == nil {
			_ = proc.Signal(syscall.SIGTERM)
			deadline := time.Now().Add(3 * time.Second)
			for time.Now().Before(deadline) {
				if !sshProcessAlive(pid) {
					break
				}
				time.Sleep(100 * time.Millisecond)
			}
			if sshProcessAlive(pid) {
				_ = proc.Signal(syscall.SIGKILL)
			}
		}
	}
	if _, err := os.Stat(pidFile); err == nil {
		_ = os.Remove(pidFile)
		if !changed {
			changed = true
		}
	}
	if changed {
		logfmt.Println("ssh-tunnel", string(leg)+" down")
	}
	return changed, nil
}

func sshTunnelLegacyDown(cfg *Config) (bool, error) {
	pid, pidFile, err := readSSHTunnelPID(cfg)
	if err != nil {
		if os.IsNotExist(err) {
			return false, nil
		}
		return false, err
	}
	changed := false
	if pid > 0 && sshProcessAlive(pid) {
		changed = true
		proc, _ := os.FindProcess(pid)
		if proc != nil {
			_ = proc.Signal(syscall.SIGTERM)
			time.Sleep(300 * time.Millisecond)
			if sshProcessAlive(pid) {
				_ = proc.Signal(syscall.SIGKILL)
			}
		}
	}
	if _, err := os.Stat(pidFile); err == nil {
		_ = os.Remove(pidFile)
		if !changed {
			changed = true
		}
	}
	if changed {
		logfmt.Println("ssh-tunnel", "legacy session down")
	}
	return changed, nil
}

func readSSHTunnelState(cfg *Config) (*SSHTunnelState, error) {
	livekit, err := readSSHTunnelLegState(cfg, sshTunnelLegLiveKit)
	if err != nil {
		return nil, err
	}
	backends, err := readSSHTunnelLegState(cfg, sshTunnelLegBackends)
	if err != nil {
		return nil, err
	}
	if livekit.Up {
		return livekit, nil
	}
	if backends.Up {
		return backends, nil
	}
	return &SSHTunnelState{Up: false, PIDFile: livekit.PIDFile}, nil
}

func readSSHTunnelLegState(cfg *Config, leg sshTunnelLeg) (*SSHTunnelState, error) {
	pid, pidFile, err := readSSHTunnelLegPID(cfg, leg)
	if err != nil {
		if os.IsNotExist(err) {
			return &SSHTunnelState{Up: false, PIDFile: pidFile}, nil
		}
		return nil, err
	}
	up := false
	if pid > 0 {
		if _, err := os.Stat(pidFile); err == nil {
			up = sshProcessAlive(pid)
			if !up {
				_ = os.Remove(pidFile)
			}
		}
	}
	return &SSHTunnelState{
		Up:      up,
		PID:     pid,
		PIDFile: pidFile,
	}, nil
}

func buildSSHTunnelLiveKitArgs(cfg *Config) []string {
	args := cfg.sshBaseArgs(true)
	args = append(args,
		"-N",
		"-o", "LogLevel=ERROR",
		"-o", "ExitOnForwardFailure=yes",
		"-o", "ServerAliveInterval=30",
		"-o", "ServerAliveCountMax=3",
		"-L", fmt.Sprintf("127.0.0.1:%d:127.0.0.1:%d", cfg.Tunnel.SSH.LocalLiveKitPort, cfg.Traefik.LiveKitPort),
		cfg.SSHTarget(),
	)
	return args
}

func buildSSHTunnelBackendsArgs(cfg *Config) []string {
	args := cfg.sshBaseArgs(true)
	args = append(args,
		"-N",
		"-o", "LogLevel=ERROR",
		"-o", "ExitOnForwardFailure=yes",
		"-o", "ServerAliveInterval=30",
		"-o", "ServerAliveCountMax=3",
		"-R", fmt.Sprintf("127.0.0.1:%d:127.0.0.1:%d", cfg.Tunnel.SSH.RemoteWebPort, cfg.Local.WebPort),
		"-R", fmt.Sprintf("127.0.0.1:%d:127.0.0.1:%d", cfg.Tunnel.SSH.RemoteAPIPort, cfg.Local.APIPort),
		cfg.SSHTarget(),
	)
	return args
}

func buildSSHTunnelArgs(cfg *Config) []string {
	args := buildSSHTunnelLiveKitArgs(cfg)
	target := args[len(args)-1]
	args = args[:len(args)-1]
	args = append(args,
		"-R", fmt.Sprintf("127.0.0.1:%d:127.0.0.1:%d", cfg.Tunnel.SSH.RemoteWebPort, cfg.Local.WebPort),
		"-R", fmt.Sprintf("127.0.0.1:%d:127.0.0.1:%d", cfg.Tunnel.SSH.RemoteAPIPort, cfg.Local.APIPort),
		target,
	)
	return args
}

func sshtunnelLegPIDPath(cfg *Config, leg sshTunnelLeg) string {
	if base := strings.TrimSpace(cfg.Tunnel.SSH.PIDFile); base != "" {
		base = expandHome(base)
		name := "ssh-tunnel-livekit.pid"
		if leg == sshTunnelLegBackends {
			name = "ssh-tunnel-backends.pid"
		}
		return filepath.Join(filepath.Dir(base), name)
	}
	if leg == sshTunnelLegLiveKit {
		return expandHome(defaultSSHTunnelLiveKitPIDFile)
	}
	return expandHome(defaultSSHTunnelBackendsPIDFile)
}

func sshtunnelPIDPath(cfg *Config) string {
	if path := strings.TrimSpace(cfg.Tunnel.SSH.PIDFile); path != "" {
		return expandHome(path)
	}
	return expandHome(defaultSSHTunnelPIDFile)
}

func writeSSHTunnelLegPID(cfg *Config, leg sshTunnelLeg, pid int) error {
	path := sshtunnelLegPIDPath(cfg, leg)
	if err := os.MkdirAll(filepath.Dir(path), 0o755); err != nil {
		return err
	}
	return os.WriteFile(path, []byte(strconv.Itoa(pid)+"\n"), 0o644)
}

func readSSHTunnelLegPID(cfg *Config, leg sshTunnelLeg) (int, string, error) {
	path := sshtunnelLegPIDPath(cfg, leg)
	data, err := os.ReadFile(path)
	if err != nil {
		return 0, path, err
	}
	pid, err := strconv.Atoi(strings.TrimSpace(string(data)))
	if err != nil {
		return 0, path, fmt.Errorf("invalid pid in %s: %w", path, err)
	}
	return pid, path, nil
}

func writeSSHTunnelPID(cfg *Config, pid int) error {
	path := sshtunnelPIDPath(cfg)
	if err := os.MkdirAll(filepath.Dir(path), 0o755); err != nil {
		return err
	}
	return os.WriteFile(path, []byte(strconv.Itoa(pid)+"\n"), 0o644)
}

func readSSHTunnelPID(cfg *Config) (int, string, error) {
	path := sshtunnelPIDPath(cfg)
	data, err := os.ReadFile(path)
	if err != nil {
		return 0, path, err
	}
	pid, err := strconv.Atoi(strings.TrimSpace(string(data)))
	if err != nil {
		return 0, path, fmt.Errorf("invalid pid in %s: %w", path, err)
	}
	return pid, path, nil
}

func sshProcessAlive(pid int) bool {
	if pid <= 0 {
		return false
	}
	data, err := os.ReadFile(fmt.Sprintf("/proc/%d/stat", pid))
	if err != nil {
		return false
	}
	fields := strings.Fields(string(data))
	if len(fields) > 2 && fields[2] == "Z" {
		return false
	}
	cmdline, err := os.ReadFile(fmt.Sprintf("/proc/%d/cmdline", pid))
	if err != nil || !strings.Contains(string(cmdline), "ssh") {
		return false
	}
	return processAlive(pid)
}

func processAlive(pid int) bool {
	if pid <= 0 {
		return false
	}
	proc, err := os.FindProcess(pid)
	if err != nil {
		return false
	}
	return proc.Signal(syscall.Signal(0)) == nil
}