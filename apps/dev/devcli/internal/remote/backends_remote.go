package remote

import (
	"context"
	"fmt"
	"strings"
	"time"

	"bedrud/devcli/internal/logfmt"
)

// PruneStaleRemoteBackendPorts kills orphaned sshd listeners on the debug server
// that block new SSH -R forwards to local Vite/API.
func PruneStaleRemoteBackendPorts(cfg *Config) error {
	script := fmt.Sprintf(`set -e
for port in %d %d; do
  pid=$(ss -tlnpH 2>/dev/null | grep "127.0.0.1:${port}" | sed -n 's/.*pid=\([0-9][0-9]*\).*/\1/p' | head -1)
  if [ -n "$pid" ]; then
    kill "$pid" 2>/dev/null || true
  fi
done
sleep 0.3
`,
		cfg.Tunnel.SSH.RemoteWebPort,
		cfg.Tunnel.SSH.RemoteAPIPort,
	)
	if err := SSH(cfg, "bash", "-c", script); err != nil {
		return fmt.Errorf("prune stale remote backend ports: %w", err)
	}
	logfmt.Println("ssh-tunnel", fmt.Sprintf("cleared stale remote listeners on :%d and :%d",
		cfg.Tunnel.SSH.RemoteWebPort, cfg.Tunnel.SSH.RemoteAPIPort))
	return nil
}

// WaitRemoteBackends blocks until Traefik upstream ports on the server reach local api/web.
func WaitRemoteBackends(ctx context.Context, cfg *Config, timeout time.Duration) error {
	if cfg == nil {
		return fmt.Errorf("config is nil")
	}
	targets := []struct {
		name string
		url  string
	}{
		{"web", cfg.WebBackend() + "/"},
		{"api", cfg.APIBackend() + "/api/health"},
	}
	deadline := time.Now().Add(timeout)
	var pending []string

	for {
		pending = pending[:0]
		for _, target := range targets {
			code := remoteHTTPCode(cfg, target.url)
			if code != "200" {
				pending = append(pending, target.name)
			}
		}
		if len(pending) == 0 {
			logfmt.Println("devcli", "remote backends reachable (traefik → tunnel → local)")
			return nil
		}
		if time.Now().After(deadline) {
			return fmt.Errorf("timeout after %s waiting for remote backends %v (stale SSH -R on server?)", timeout, pending)
		}
		select {
		case <-ctx.Done():
			return ctx.Err()
		case <-time.After(300 * time.Millisecond):
		}
	}
}

func remoteHTTPCode(cfg *Config, url string) string {
	cmd := fmt.Sprintf(
		"curl -sf -o /dev/null -w '%%{http_code}' --connect-timeout 2 %s 2>/dev/null || echo 000",
		shellQuote(url),
	)
	out, err := SSHOutput(cfg, cmd)
	if err != nil {
		return "000"
	}
	return strings.TrimSpace(out)
}

// VerifyPublicHealth checks the public debug URL responds.
func VerifyPublicHealth(cfg *Config) error {
	url := strings.TrimRight(cfg.URLs.PublicBase, "/") + "/api/health"
	code := remoteHTTPCode(cfg, url)
	if code == "200" {
		logfmt.Println("devcli", fmt.Sprintf("public health ok (%s)", url))
		return nil
	}
	// Traefik routes /api on the server; also try from outside via SSH curl to public host
	out, err := SSHOutput(cfg, fmt.Sprintf(
		"curl -sf -o /dev/null -w '%%{http_code}' --connect-timeout 5 %s 2>/dev/null || echo 000",
		shellQuote(url),
	))
	if err == nil && strings.TrimSpace(out) == "200" {
		logfmt.Println("devcli", fmt.Sprintf("public health ok (%s)", url))
		return nil
	}
	return fmt.Errorf("%s returned HTTP %s (backend tunnel or traefik misconfigured)", url, code)
}