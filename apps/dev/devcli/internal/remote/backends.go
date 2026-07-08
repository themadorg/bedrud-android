package remote

import (
	"context"
	"fmt"
	"net"
	"time"

	"bedrud/devcli/internal/logfmt"
)

// WaitLocalBackends blocks until local api and web respond with HTTP 200.
func WaitLocalBackends(ctx context.Context, cfg *Config, timeout time.Duration) error {
	if cfg == nil {
		return fmt.Errorf("config is nil")
	}
	targets := []struct {
		name string
		url  string
	}{
		{"api", fmt.Sprintf("http://127.0.0.1:%d/api/health", cfg.Local.APIPort)},
		{"web", fmt.Sprintf("http://127.0.0.1:%d/", cfg.Local.WebPort)},
	}
	deadline := time.Now().Add(timeout)
	var pending []string
	loggedWait := false

	for {
		pending = pending[:0]
		for _, target := range targets {
			code, err := curlLocal(target.url)
			if err != nil || code != "200" {
				pending = append(pending, target.name)
			}
		}
		if len(pending) == 0 {
			logfmt.Println("devcli", "local backends healthy (api /api/health + web /)")
			return nil
		}
		if !loggedWait {
			logfmt.Println("devcli", fmt.Sprintf("waiting for local backends: %v", pending))
			loggedWait = true
		}
		if time.Now().After(deadline) {
			return fmt.Errorf("timeout after %s waiting for local backends %v (HTTP 200)", timeout, pending)
		}
		select {
		case <-ctx.Done():
			return ctx.Err()
		case <-time.After(200 * time.Millisecond):
		}
	}
}

func localTCPReady(port int) bool {
	conn, err := net.DialTimeout("tcp", fmt.Sprintf("127.0.0.1:%d", port), 250*time.Millisecond)
	if err != nil {
		return false
	}
	_ = conn.Close()
	return true
}