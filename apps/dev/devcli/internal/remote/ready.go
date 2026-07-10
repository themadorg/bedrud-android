package remote

import (
	"fmt"
	"strings"
	"time"

	"bedrud/devcli/internal/logfmt"
)

// ReadyOptions controls which checks VerifyDevRemoteReady runs.
type ReadyOptions struct {
	RequireTunnel  bool
	RequireTraefik bool
}

// RequireRemoteLiveKit verifies LiveKit is deployed and healthy on the remote server.
func RequireRemoteLiveKit(cfg *Config) error {
	if cfg == nil {
		return fmt.Errorf("config is nil")
	}
	logfmt.Println("devcli", "verifying remote LiveKit on server")
	for _, res := range probeLiveKit(cfg) {
		if !res.OK && criticalLiveKitProbe(res.Name, cfg) {
			return probeError(res)
		}
	}
	logfmt.Println("devcli", "remote LiveKit ok (service + HTTP + TURN + public route)")
	return nil
}

// VerifyDevRemoteReady runs critical health probes and fails startup when any check fails.
func VerifyDevRemoteReady(cfg *Config, opts ReadyOptions) error {
	if cfg == nil {
		return fmt.Errorf("config is nil")
	}
	logfmt.Println("devcli", "verifying dev-remote stack")

	for _, res := range probeLocalBackends(cfg) {
		if !res.OK {
			return probeError(res)
		}
	}

	for _, res := range probeLiveKit(cfg) {
		if !res.OK && criticalLiveKitProbe(res.Name, cfg) {
			return probeError(res)
		}
	}

	if opts.RequireTraefik {
		for _, res := range probeTraefikService(cfg) {
			if !res.OK {
				return probeError(res)
			}
		}
	}

	if opts.RequireTunnel {
		tunnelUp, _, err := TunnelStatus(cfg)
		if err != nil {
			return fmt.Errorf("tunnel: %w", err)
		}
		if !tunnelUp {
			return fmt.Errorf("tunnel: down (devcli remote tunnel up)")
		}
		for _, spec := range []struct {
			name string
			url  string
		}{
			{"web-backend", cfg.WebBackend() + "/"},
			{"api-backend", cfg.APIBackend() + "/api/health"},
		} {
			var res HealthResult
			for attempt := 0; attempt < 8; attempt++ {
				res = probeHTTPFromRemote(cfg, spec.name, spec.url, "200", "devcli remote run --yes")
				if res.OK {
					break
				}
				time.Sleep(500 * time.Millisecond)
			}
			if !res.OK {
				return probeError(res)
			}
		}
		if cfg.UsesSSHTunnel() || cfg.UsesDevTunnel() || cfg.UsesWireGuard() {
			code, err := curlLocal(cfg.URLs.LiveKitInternal + "/")
			ok := err == nil && code == "200"
			if !ok {
				return fmt.Errorf("livekit-tunnel: HTTP %s (%v) — %s", code, err, cfg.URLs.LiveKitInternal)
			}
		}
	}

	for _, spec := range []struct {
		name string
		run  func() []HealthResult
	}{
		{"public", func() []HealthResult { return probePublic(cfg) }},
	} {
		var failed HealthResult
		for attempt := 0; attempt < 8; attempt++ {
			failed = HealthResult{}
			for _, res := range spec.run() {
				if !res.OK {
					failed = res
					break
				}
			}
			if failed.Name == "" {
				break
			}
			time.Sleep(500 * time.Millisecond)
		}
		if failed.Name != "" {
			return probeError(failed)
		}
	}

	logfmt.Println("devcli", "dev-remote stack verified (server LiveKit + local api/web + public URLs)")
	return nil
}

func probeLocalBackends(cfg *Config) []HealthResult {
	return []HealthResult{
		probeLocalHTTP(cfg.Local.APIPort, "local-api", "/api/health", "200"),
		probeLocalHTTP(cfg.Local.WebPort, "local-web", "/", "200"),
	}
}

func probeLocalHTTP(port int, name, path, wantCode string) HealthResult {
	if !localTCPReady(port) {
		return HealthResult{
			Name: name, OK: false,
			Detail: fmt.Sprintf("127.0.0.1:%d not listening", port),
			Hint:   "devcli remote run --yes",
		}
	}
	url := fmt.Sprintf("http://127.0.0.1:%d%s", port, path)
	code, err := curlLocal(url)
	ok := err == nil && code == wantCode
	detail := fmt.Sprintf("%s HTTP %s", url, code)
	if err != nil {
		detail = fmt.Sprintf("%s (%v)", url, err)
	}
	return HealthResult{Name: name, OK: ok, Detail: detail, Hint: "devcli remote run --yes"}
}

func criticalLiveKitProbe(name string, cfg *Config) bool {
	switch name {
	case "livekit-service", "livekit-http", "livekit-turn-relay-fw", "livekit-public", "livekit-browser-url":
		return true
	case "livekit-turn-tls":
		return cfg.acmeEnabled()
	default:
		return false
	}
}

func probeError(res HealthResult) error {
	msg := fmt.Sprintf("%s: %s", res.Name, res.Detail)
	if res.Hint != "" {
		msg += " — " + res.Hint
	}
	return fmt.Errorf("%s", strings.TrimSpace(msg))
}