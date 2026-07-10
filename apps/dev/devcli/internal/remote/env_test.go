package remote

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestLoadSSHFromEnv(t *testing.T) {
	dir := t.TempDir()
	envPath := filepath.Join(dir, "server", ".env")
	if err := os.MkdirAll(filepath.Dir(envPath), 0o755); err != nil {
		t.Fatal(err)
	}
	content := `REMOTE_DEBUG_SSH_HOST=debug.test
REMOTE_DEBUG_SSH_USER=alice
REMOTE_DEBUG_SSH_PORT=2222
REMOTE_DEBUG_SSH_IDENTITY_FILE=~/.ssh/debug
`
	if err := os.WriteFile(envPath, []byte(content), 0o600); err != nil {
		t.Fatal(err)
	}

	cfg := &Config{}
	if err := loadSSHFromEnv(dir, cfg); err != nil {
		t.Fatal(err)
	}
	if cfg.SSH.Host != "debug.test" {
		t.Fatalf("host: got %q", cfg.SSH.Host)
	}
	if cfg.SSH.User != "alice" {
		t.Fatalf("user: got %q", cfg.SSH.User)
	}
	if cfg.SSH.Port != 2222 {
		t.Fatalf("port: got %d", cfg.SSH.Port)
	}
	if cfg.SSH.IdentityFile != "~/.ssh/debug" {
		t.Fatalf("identity: got %q", cfg.SSH.IdentityFile)
	}
}

func TestProcessEnvOverridesDotEnv(t *testing.T) {
	dir := t.TempDir()
	envPath := filepath.Join(dir, "server", ".env")
	if err := os.MkdirAll(filepath.Dir(envPath), 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(envPath, []byte("REMOTE_DEBUG_SSH_HOST=file.host\nREMOTE_DEBUG_SSH_USER=file\n"), 0o600); err != nil {
		t.Fatal(err)
	}
	t.Setenv("REMOTE_DEBUG_SSH_HOST", "env.host")
	t.Setenv("REMOTE_DEBUG_SSH_USER", "envuser")

	cfg := &Config{}
	if err := loadSSHFromEnv(dir, cfg); err != nil {
		t.Fatal(err)
	}
	if cfg.SSH.Host != "env.host" {
		t.Fatalf("host: got %q", cfg.SSH.Host)
	}
	if cfg.SSH.User != "envuser" {
		t.Fatalf("user: got %q", cfg.SSH.User)
	}
}

func TestUpsertDotEnv(t *testing.T) {
	dir := t.TempDir()
	envPath := filepath.Join(dir, "server", ".env")
	if err := os.MkdirAll(filepath.Dir(envPath), 0o755); err != nil {
		t.Fatal(err)
	}
	initial := "# header\nREMOTE_DEBUG_SSH_HOST=old\nREMOTE_DEBUG_SSH_USER=root\n"
	if err := os.WriteFile(envPath, []byte(initial), 0o600); err != nil {
		t.Fatal(err)
	}

	if err := UpsertDotEnv(dir, "REMOTE_DEBUG_SSH_HOST", "new.host"); err != nil {
		t.Fatal(err)
	}
	if err := UpsertDotEnv(dir, "REMOTE_DEBUG_TUNNEL_TOKEN", "abc123"); err != nil {
		t.Fatal(err)
	}

	vals, err := parseDotEnv(envPath)
	if err != nil {
		t.Fatal(err)
	}
	if vals["REMOTE_DEBUG_SSH_HOST"] != "new.host" {
		t.Fatalf("host: got %q", vals["REMOTE_DEBUG_SSH_HOST"])
	}
	if vals["REMOTE_DEBUG_SSH_USER"] != "root" {
		t.Fatalf("user: got %q", vals["REMOTE_DEBUG_SSH_USER"])
	}
	if vals["REMOTE_DEBUG_TUNNEL_TOKEN"] != "abc123" {
		t.Fatalf("token: got %q", vals["REMOTE_DEBUG_TUNNEL_TOKEN"])
	}

	raw, err := os.ReadFile(envPath)
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(string(raw), "# header") {
		t.Fatalf("expected comments preserved, got:\n%s", raw)
	}
}