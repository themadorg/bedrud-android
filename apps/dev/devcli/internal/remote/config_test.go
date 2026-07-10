package remote

import (
	"fmt"
	"os"
	"path/filepath"
	"testing"
)

func writeRemoteDebugYAML(t *testing.T, dir, content string) {
	t.Helper()
	path := filepath.Join(dir, "server", "remote-debug.yaml")
	if err := os.MkdirAll(filepath.Dir(path), 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(path, []byte(content), 0o644); err != nil {
		t.Fatal(err)
	}
}

func writeRemoteEnv(t *testing.T, dir string) {
	t.Helper()
	path := filepath.Join(dir, "server", ".env")
	if err := os.MkdirAll(filepath.Dir(path), 0o755); err != nil {
		t.Fatal(err)
	}
	content := `REMOTE_DEBUG_SSH_HOST=debug.example.com
REMOTE_DEBUG_SSH_USER=root
REMOTE_DEBUG_SSH_PORT=22
`
	if err := os.WriteFile(path, []byte(content), 0o600); err != nil {
		t.Fatal(err)
	}
}

func TestLoadSSHTunnelMode(t *testing.T) {
	dir := t.TempDir()
	writeRemoteEnv(t, dir)
	writeRemoteDebugYAML(t, dir, `
tunnel:
  mode: ssh
urls:
  publicHost: debug.example.com
traefik:
  dynamicDir: /etc/traefik/dynamic/bedrud-debug
local:
  webPort: 7070
  apiPort: 7071
`)

	cfg, err := Load(dir)
	if err != nil {
		t.Fatal(err)
	}
	if !cfg.UsesSSHTunnel() {
		t.Fatal("expected ssh tunnel mode")
	}
	if got, want := cfg.WebBackend(), "http://127.0.0.1:7070"; got != want {
		t.Fatalf("WebBackend: got %q want %q", got, want)
	}
	if got, want := cfg.APIBackend(), "http://127.0.0.1:7071"; got != want {
		t.Fatalf("APIBackend: got %q want %q", got, want)
	}
	if got, want := cfg.URLs.LiveKitInternal, "http://127.0.0.1:17072"; got != want {
		t.Fatalf("LiveKitInternal: got %q want %q", got, want)
	}
	env := cfg.WebEnv()
	if len(env) != 1 || env[0] != "BEDRUD_ALLOWED_HOSTS=debug.example.com" {
		t.Fatalf("WebEnv: got %v", env)
	}
}

func TestWebEnvWireGuardSetsIceRelay(t *testing.T) {
	dir := t.TempDir()
	writeRemoteEnv(t, dir)
	wgPath := filepath.Join(dir, "wg.conf")
	if err := os.WriteFile(wgPath, []byte("[Interface]\nPrivateKey=x\n[Peer]\nPublicKey=y\n"), 0o600); err != nil {
		t.Fatal(err)
	}
	writeRemoteDebugYAML(t, dir, fmt.Sprintf(`
tunnel:
  mode: wireguard
wireguard:
  configFile: %s
  localTunnelIP: 10.0.0.2
  remoteTunnelIP: 10.0.0.1
urls:
  publicHost: debug.example.com
traefik:
  dynamicDir: /etc/traefik/dynamic/bedrud-debug
`, wgPath))

	cfg, err := Load(dir)
	if err != nil {
		t.Fatal(err)
	}
	env := cfg.WebEnv()
	want := []string{
		"BEDRUD_ALLOWED_HOSTS=debug.example.com",
		"VITE_LIVEKIT_ICE_RELAY=1",
	}
	if len(env) != len(want) {
		t.Fatalf("WebEnv: got %v want %v", env, want)
	}
	for i := range want {
		if env[i] != want[i] {
			t.Fatalf("WebEnv[%d]: got %q want %q", i, env[i], want[i])
		}
	}
}

func TestLoadDevTunnelMode(t *testing.T) {
	dir := t.TempDir()
	writeRemoteEnv(t, dir)
	writeRemoteDebugYAML(t, dir, `
tunnel:
  mode: devtunnel
  devtunnel:
    port: 7079
urls:
  publicHost: debug.example.com
traefik:
  dynamicDir: /etc/traefik/dynamic/bedrud-debug
local:
  webPort: 7070
  apiPort: 7071
`)

	cfg, err := Load(dir)
	if err != nil {
		t.Fatal(err)
	}
	if !cfg.UsesDevTunnel() {
		t.Fatal("expected devtunnel mode")
	}
	if got, want := cfg.WebBackend(), "http://127.0.0.1:7070"; got != want {
		t.Fatalf("WebBackend: got %q want %q", got, want)
	}
	if got, want := cfg.DevTunnelServerAddr(), "debug.example.com:7079"; got != want {
		t.Fatalf("DevTunnelServerAddr: got %q want %q", got, want)
	}
}

func TestLoadWireGuardModeLiveKitInternal(t *testing.T) {
	dir := t.TempDir()
	writeRemoteEnv(t, dir)
	writeRemoteDebugYAML(t, dir, `
tunnel:
  mode: wireguard
wireguard:
  configFile: /tmp/wg.conf
  localTunnelIP: 10.0.0.2
  remoteTunnelIP: 10.0.0.1
urls:
  publicHost: debug.example.com
traefik:
  dynamicDir: /etc/traefik/dynamic/bedrud-debug
local:
  webPort: 7070
  apiPort: 7071
`)
	if err := os.WriteFile("/tmp/wg.conf", []byte("[Interface]\nPrivateKey=x\n[Peer]\nPublicKey=y\n"), 0o600); err != nil {
		t.Fatal(err)
	}

	cfg, err := Load(dir)
	if err != nil {
		t.Fatal(err)
	}
	if got, want := cfg.URLs.LiveKitInternal, "http://127.0.0.1:17072"; got != want {
		t.Fatalf("LiveKitInternal: got %q want %q", got, want)
	}
	if got, want := cfg.WebBackend(), "http://10.0.0.2:7070"; got != want {
		t.Fatalf("WebBackend: got %q want %q", got, want)
	}
}

func TestLoadWireGuardModeAppliesDefaults(t *testing.T) {
	dir := t.TempDir()
	writeRemoteEnv(t, dir)
	wgPath := filepath.Join(dir, "wg.conf")
	if err := os.WriteFile(wgPath, []byte("[Interface]\nPrivateKey=x\n[Peer]\nPublicKey=y\n"), 0o600); err != nil {
		t.Fatal(err)
	}
	writeRemoteDebugYAML(t, dir, fmt.Sprintf(`
tunnel:
  mode: wireguard
wireguard:
  configFile: %s
urls:
  publicHost: debug.example.com
traefik:
  dynamicDir: /etc/traefik/dynamic/bedrud-debug
`, wgPath))

	cfg, err := Load(dir)
	if err != nil {
		t.Fatal(err)
	}
	if cfg.WireGuard.LocalTunnelIP != "10.0.0.2" || cfg.WireGuard.RemoteTunnelIP != "10.0.0.1" {
		t.Fatalf("defaults: local=%s remote=%s", cfg.WireGuard.LocalTunnelIP, cfg.WireGuard.RemoteTunnelIP)
	}
}

func TestBuildSSHTunnelArgs(t *testing.T) {
	cfg := &Config{}
	cfg.SSH.Host = "1.2.3.4"
	cfg.SSH.User = "root"
	cfg.SSH.Port = 22
	cfg.Tunnel.Mode = TunnelModeSSH
	cfg.Local.WebPort = 7070
	cfg.Local.APIPort = 7071
	cfg.Tunnel.SSH.RemoteWebPort = 7070
	cfg.Tunnel.SSH.RemoteAPIPort = 7071
	cfg.Tunnel.SSH.LocalLiveKitPort = 17072
	cfg.Traefik.LiveKitPort = 7072
	cfg.applyDefaults()

	args := buildSSHTunnelArgs(cfg)
	joined := stringsJoin(args)
	for _, want := range []string{
		"-R", "127.0.0.1:7070:127.0.0.1:7070",
		"-R", "127.0.0.1:7071:127.0.0.1:7071",
		"-L", "127.0.0.1:17072:127.0.0.1:7072",
		"root@1.2.3.4",
	} {
		if !containsToken(args, want) {
			t.Fatalf("missing %q in args: %s", want, joined)
		}
	}
}

func stringsJoin(parts []string) string {
	out := ""
	for i, p := range parts {
		if i > 0 {
			out += " "
		}
		out += p
	}
	return out
}

func containsToken(args []string, token string) bool {
	for _, arg := range args {
		if arg == token {
			return true
		}
	}
	return false
}