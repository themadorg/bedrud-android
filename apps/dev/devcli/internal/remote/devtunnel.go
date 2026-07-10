package remote

import (
	"context"
	"fmt"
	"net"
	"os"
	"os/exec"
	"path/filepath"
	"strconv"
	"strings"
	"sync"
	"syscall"
	"time"

	"bedrud/devcli/internal/logfmt"
	"bedrud/devcli/internal/tunnel"
)

const defaultDevTunnelPIDFile = "~/.config/bedrud/devtunnel.pid"

var (
	devTunnelMu      sync.Mutex
	devTunnelCancel  context.CancelFunc
	devTunnelRunning bool
)

// DevTunnelUp starts the local devtunnel client (in-process for remote run).
func DevTunnelUp(cfg *Config) error {
	if err := devTunnelCredentials(cfg); err != nil {
		return err
	}

	devTunnelMu.Lock()
	defer devTunnelMu.Unlock()
	if devTunnelRunning {
		logfmt.Println("devtunnel", "already connected")
		return nil
	}

	ctx, cancel := context.WithCancel(context.Background())
	ready := make(chan struct{})
	var readyOnce sync.Once
	var logOnce sync.Once
	clientCfg := clientTunnelConfig(cfg)
	clientCfg.OnConnected = func() {
		logOnce.Do(func() {
			logfmt.Println("devtunnel", fmt.Sprintf("connected → %s (reverse :%d/: %d, livekit :%d)",
				cfg.DevTunnelServerAddr(),
				cfg.Tunnel.SSH.RemoteWebPort,
				cfg.Tunnel.SSH.RemoteAPIPort,
				cfg.Tunnel.SSH.LocalLiveKitPort,
			))
		})
		readyOnce.Do(func() { close(ready) })
	}

	go func() {
		_ = tunnel.RunClient(ctx, clientCfg)
		devTunnelMu.Lock()
		devTunnelRunning = false
		devTunnelMu.Unlock()
	}()

	devTunnelCancel = cancel
	devTunnelRunning = true
	_ = writeDevTunnelPID(cfg, os.Getpid())

	select {
	case <-ready:
		return nil
	case <-time.After(15 * time.Second):
		cancel()
		devTunnelRunning = false
		return fmt.Errorf("devtunnel: connect timeout (%s)", cfg.DevTunnelServerAddr())
	}
}

// DevTunnelDetachedUp starts the tunnel client as a background devcli subprocess.
func DevTunnelDetachedUp(cfg *Config) error {
	if st, _ := readDevTunnelPIDState(cfg); st != nil && st.Up {
		logfmt.Println("devtunnel", fmt.Sprintf("already up (pid %d)", st.PID))
		return nil
	}
	_, _ = DevTunnelDetachedDown(cfg)

	self, err := os.Executable()
	if err != nil {
		return err
	}
	repo, err := findRepoForDetached(cfg)
	if err != nil {
		return err
	}

	cmd := exec.Command(self, "remote", "tunnel-client", "--repo", repo)
	cmd.Stdout = nil
	cmd.Stderr = nil
	if err := cmd.Start(); err != nil {
		return fmt.Errorf("devtunnel client: %w", err)
	}
	pid := cmd.Process.Pid
	if err := writeDevTunnelPID(cfg, pid); err != nil {
		_ = cmd.Process.Kill()
		return err
	}
	_ = cmd.Process.Release()

	deadline := time.Now().Add(30 * time.Second)
	lkPort := cfg.Tunnel.SSH.LocalLiveKitPort
	for time.Now().Before(deadline) {
		if !devTunnelProcessAlive(pid) {
			_ = os.Remove(devTunnelPIDPath(cfg))
			return fmt.Errorf("devtunnel client exited (check token and server agent)")
		}
		if localTCPReady(lkPort) {
			logfmt.Println("devtunnel", fmt.Sprintf("up pid %d", pid))
			return nil
		}
		time.Sleep(300 * time.Millisecond)
	}
	return fmt.Errorf("devtunnel: local listener 127.0.0.1:%d not ready after 30s", lkPort)
}

func findRepoForDetached(cfg *Config) (string, error) {
	if repo := strings.TrimSpace(os.Getenv("BEDRUD_REPO")); repo != "" {
		return repo, nil
	}
	wd, err := os.Getwd()
	if err != nil {
		return "", err
	}
	for dir := wd; ; dir = filepath.Dir(dir) {
		if _, err := os.Stat(filepath.Join(dir, "server", "remote-debug.yaml")); err == nil {
			return dir, nil
		}
		if dir == "/" || dir == "." {
			break
		}
	}
	return "", fmt.Errorf("bedrud repo not found (set BEDRUD_REPO or run from repo)")
}

// DevTunnelDetachedDown stops a background tunnel client subprocess.
func DevTunnelDetachedDown(cfg *Config) (bool, error) {
	devTunnelMu.Lock()
	if devTunnelRunning && devTunnelCancel != nil {
		devTunnelCancel()
		devTunnelRunning = false
		devTunnelCancel = nil
		devTunnelMu.Unlock()
		_ = os.Remove(devTunnelPIDPath(cfg))
		logfmt.Println("devtunnel", "down")
		return true, nil
	}
	devTunnelMu.Unlock()

	pid, pidFile, err := readDevTunnelPID(cfg)
	if err != nil {
		if os.IsNotExist(err) {
			return false, nil
		}
		return false, err
	}
	changed := false
	if pid > 0 && devTunnelProcessAlive(pid) {
		changed = true
		proc, _ := os.FindProcess(pid)
		if proc != nil {
			_ = proc.Signal(syscall.SIGTERM)
			deadline := time.Now().Add(3 * time.Second)
			for time.Now().Before(deadline) {
				if !devTunnelProcessAlive(pid) {
					break
				}
				time.Sleep(100 * time.Millisecond)
			}
			if devTunnelProcessAlive(pid) {
				_ = proc.Signal(syscall.SIGKILL)
			}
		}
	}
	_ = os.Remove(pidFile)
	if changed {
		logfmt.Println("devtunnel", "down")
	}
	return changed, nil
}

// DevTunnelStatus reports whether the tunnel client is running.
func DevTunnelStatus(cfg *Config) (bool, string, error) {
	devTunnelMu.Lock()
	embedded := devTunnelRunning
	devTunnelMu.Unlock()
	if embedded {
		return true, "embedded (remote run)", nil
	}
	st, err := readDevTunnelPIDState(cfg)
	if err != nil {
		return false, "", err
	}
	if st.Up {
		return true, fmt.Sprintf("pid %d", st.PID), nil
	}
	return false, "down", nil
}

// RunDevTunnelClient is the long-running local client entrypoint.
func RunDevTunnelClient(cfg *Config) error {
	if err := devTunnelCredentials(cfg); err != nil {
		return err
	}
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	devTunnelMu.Lock()
	devTunnelCancel = cancel
	devTunnelRunning = true
	devTunnelMu.Unlock()
	defer func() {
		devTunnelMu.Lock()
		devTunnelRunning = false
		devTunnelMu.Unlock()
	}()

	_ = writeDevTunnelPID(cfg, os.Getpid())
	clientCfg := clientTunnelConfig(cfg)
	clientCfg.OnConnected = func() {
		logfmt.Println("devtunnel", "connected")
	}
	clientCfg.OnDisconnected = func() {
		logfmt.Println("devtunnel", "disconnected — reconnecting")
	}
	return tunnel.RunClient(ctx, clientCfg)
}

// WaitDevTunnelReady blocks until the LiveKit local proxy responds.
func WaitDevTunnelReady(cfg *Config, timeout time.Duration) error {
	target := strings.TrimRight(cfg.URLs.LiveKitInternal, "/") + "/"
	deadline := time.Now().Add(timeout)
	for time.Now().Before(deadline) {
		code, err := curlLocal(target)
		if err == nil && code == "200" {
			logfmt.Println("devtunnel", fmt.Sprintf("ready (%s → OK)", target))
			return nil
		}
		time.Sleep(300 * time.Millisecond)
	}
	return fmt.Errorf("devtunnel: %s not reachable after %s", target, timeout)
}

func devTunnelToken(cfg *Config) error {
	if strings.TrimSpace(cfg.Tunnel.DevTunnel.Token) == "" {
		return fmt.Errorf("REMOTE_DEBUG_TUNNEL_TOKEN missing in server/.env (run: devcli remote tunnel deploy)")
	}
	return nil
}

func devTunnelPIDPath(cfg *Config) string {
	if path := strings.TrimSpace(cfg.Tunnel.DevTunnel.PIDFile); path != "" {
		return expandHome(path)
	}
	return expandHome(defaultDevTunnelPIDFile)
}

func writeDevTunnelPID(cfg *Config, pid int) error {
	path := devTunnelPIDPath(cfg)
	if err := os.MkdirAll(filepath.Dir(path), 0o755); err != nil {
		return err
	}
	return os.WriteFile(path, []byte(strconv.Itoa(pid)+"\n"), 0o644)
}

func readDevTunnelPID(cfg *Config) (int, string, error) {
	path := devTunnelPIDPath(cfg)
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

type devTunnelPIDState struct {
	Up  bool
	PID int
}

func readDevTunnelPIDState(cfg *Config) (*devTunnelPIDState, error) {
	pid, _, err := readDevTunnelPID(cfg)
	if err != nil {
		if os.IsNotExist(err) {
			return &devTunnelPIDState{}, nil
		}
		return nil, err
	}
	return &devTunnelPIDState{
		Up:  pid > 0 && devTunnelProcessAlive(pid),
		PID: pid,
	}, nil
}

func devTunnelProcessAlive(pid int) bool {
	if pid <= 0 {
		return false
	}
	proc, err := os.FindProcess(pid)
	if err != nil {
		return false
	}
	if proc.Signal(syscall.Signal(0)) != nil {
		return false
	}
	cmdline, err := os.ReadFile(fmt.Sprintf("/proc/%d/cmdline", pid))
	if err != nil {
		return true
	}
	return strings.Contains(string(cmdline), "devcli")
}

func devTunnelAgentReachable(cfg *Config) bool {
	fp := strings.TrimSpace(cfg.Tunnel.DevTunnel.TLSFingerprint)
	if fp == "" {
		conn, err := net.DialTimeout("tcp", cfg.DevTunnelServerAddr(), 2*time.Second)
		if err != nil {
			return false
		}
		_ = conn.Close()
		return true
	}
	conn, err := tunnel.DialTLS(cfg.DevTunnelServerAddr(), fp, cfg.DevTunnelTLSServerName())
	if err != nil {
		return false
	}
	_ = conn.Close()
	return true
}