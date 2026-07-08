package remote

import (
	"os"
	"path/filepath"
	"testing"
)

func TestSSHTunnelPIDRoundTrip(t *testing.T) {
	dir := t.TempDir()
	cfg := &Config{}
	cfg.Tunnel.SSH.PIDFile = filepath.Join(dir, "tunnel.pid")

	if err := writeSSHTunnelPID(cfg, 4242); err != nil {
		t.Fatal(err)
	}
	pid, path, err := readSSHTunnelPID(cfg)
	if err != nil {
		t.Fatal(err)
	}
	if pid != 4242 {
		t.Fatalf("pid: got %d", pid)
	}
	if path != cfg.Tunnel.SSH.PIDFile {
		t.Fatalf("path: got %q", path)
	}
}

func TestSSHTunnelStatusMissingPIDFile(t *testing.T) {
	cfg := &Config{}
	cfg.Tunnel.SSH.PIDFile = filepath.Join(t.TempDir(), "ssh-tunnel.pid")

	st, err := readSSHTunnelState(cfg)
	if err != nil {
		t.Fatal(err)
	}
	if st.Up {
		t.Fatal("expected down when pid file missing")
	}
}

func TestProcessAliveCurrentPID(t *testing.T) {
	if !processAlive(os.Getpid()) {
		t.Fatal("expected current process to be alive")
	}
	if processAlive(999999999) {
		t.Fatal("expected bogus pid to be dead")
	}
}