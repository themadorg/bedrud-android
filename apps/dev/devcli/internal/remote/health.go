package remote

import (
	"fmt"
	"net/http"
	"strings"
	"time"
)

func curlLocal(url string) (string, error) {
	client := &http.Client{Timeout: 2 * time.Second}
	resp, err := client.Get(url)
	if err != nil {
		return "000", err
	}
	defer resp.Body.Close()
	return fmt.Sprint(resp.StatusCode), nil
}

// HealthResult is one service probe outcome.
type HealthResult struct {
	Name   string
	OK     bool
	Detail string
	Hint   string
}

// HealthReport aggregates probe results.
type HealthReport struct {
	Results []HealthResult
}

func (r HealthReport) AllOK() bool {
	for _, res := range r.Results {
		if !res.OK {
			return false
		}
	}
	return true
}

func (r HealthReport) Failed() []HealthResult {
	var out []HealthResult
	for _, res := range r.Results {
		if !res.OK {
			out = append(out, res)
		}
	}
	return out
}

// CheckRemoteHealth probes SSH, tunnel, LiveKit, backends, Traefik, and public URLs.
func CheckRemoteHealth(cfg *Config) HealthReport {
	var report HealthReport

	// SSH
	if err := pingSSH(cfg); err != nil {
		report.Results = append(report.Results, HealthResult{
			Name: "ssh", OK: false, Detail: err.Error(),
			Hint: "check server/.env REMOTE_DEBUG_SSH_* and network",
		})
	} else {
		report.Results = append(report.Results, HealthResult{
			Name: "ssh", OK: true, Detail: cfg.SSHTarget() + " reachable",
		})
	}

	// Tunnel
	tunnelUp, tunnelDetail, tunnelErr := TunnelStatus(cfg)
	if tunnelErr != nil {
		report.Results = append(report.Results, HealthResult{
			Name: "tunnel", OK: false, Detail: tunnelErr.Error(),
			Hint: "devcli remote tunnel up",
		})
	} else if !tunnelUp {
		report.Results = append(report.Results, HealthResult{
			Name: "tunnel", OK: false, Detail: "down",
			Hint: "devcli remote tunnel up",
		})
	} else {
		report.Results = append(report.Results, HealthResult{
			Name: "tunnel", OK: true, Detail: fmt.Sprintf("%s (%s)", tunnelDetail, cfg.TunnelMode()),
		})
	}

	report.Results = append(report.Results, probeLocalBackends(cfg)...)
	report.Results = append(report.Results, probeLiveKit(cfg)...)
	report.Results = append(report.Results, probeTraefikService(cfg)...)

	// Local backends via tunnel (only when tunnel is up)
	if tunnelUp {
		report.Results = append(report.Results,
			probeHTTPFromRemote(cfg, "web-backend", cfg.WebBackend()+"/", "200", "devcli remote run --yes"),
			probeHTTPFromRemote(cfg, "api-backend", cfg.APIBackend()+"/api/health", "200", "devcli remote run --yes"),
		)
		if cfg.UsesSSHTunnel() || cfg.UsesDevTunnel() {
			code, err := curlLocal(cfg.URLs.LiveKitInternal + "/")
			ok := err == nil && code == "200"
			detail := "local proxy " + cfg.URLs.LiveKitInternal
			if !ok {
				detail = fmt.Sprintf("HTTP %s (%v)", code, err)
			}
			report.Results = append(report.Results, HealthResult{
				Name: "livekit-tunnel", OK: ok, Detail: detail,
				Hint: "devcli remote run --yes",
			})
		} else if cfg.UsesWireGuard() {
			code, err := curlLocal(cfg.URLs.LiveKitInternal + "/")
			ok := err == nil && code == "200"
			detail := "wireguard " + cfg.URLs.LiveKitInternal
			if !ok {
				detail = fmt.Sprintf("HTTP %s (%v)", code, err)
			}
			report.Results = append(report.Results, HealthResult{
				Name: "livekit-wg", OK: ok, Detail: detail,
				Hint: "devcli remote run --yes (netstack proxy)",
			})
		}
	} else {
		report.Results = append(report.Results,
			HealthResult{Name: "web-backend", OK: false, Detail: "skipped (tunnel down)", Hint: "devcli remote tunnel up"},
			HealthResult{Name: "api-backend", OK: false, Detail: "skipped (tunnel down)", Hint: "devcli remote tunnel up"},
		)
		if cfg.UsesSSHTunnel() || cfg.UsesDevTunnel() {
			report.Results = append(report.Results, HealthResult{
				Name: "livekit-tunnel", OK: false, Detail: "skipped (tunnel down)", Hint: "devcli remote tunnel up",
			})
		}
	}

	report.Results = append(report.Results, probePublic(cfg)...)
	return report
}

func probeLiveKit(cfg *Config) []HealthResult {
	var out []HealthResult

	svc, _ := SSHOutput(cfg, "systemctl is-active bedrud-livekit 2>/dev/null || echo inactive")
	svc = strings.TrimSpace(svc)
	svcOK := svc == "active"
	out = append(out, HealthResult{
		Name: "livekit-service", OK: svcOK,
		Detail: "bedrud-livekit " + svc,
		Hint:   "devcli remote livekit sync",
	})

	body, _ := SSHOutput(cfg, fmt.Sprintf("curl -sf %s/ 2>/dev/null || echo fail", shellQuote(cfg.LiveKitBackend())))
	httpOK := strings.TrimSpace(body) == "OK"
	out = append(out, HealthResult{
		Name: "livekit-http", OK: httpOK,
		Detail: fmt.Sprintf("%s → %s", cfg.LiveKitBackend(), strings.TrimSpace(body)),
		Hint:   "devcli remote livekit sync",
	})

	turnListen, _ := SSHOutput(cfg, "ss -tlnH 2>/dev/null | awk '$4 ~ /:5349$/ {print}' | head -1")
	turnOK := strings.TrimSpace(turnListen) != ""
	out = append(out, HealthResult{
		Name: "livekit-turn-tls", OK: turnOK,
		Detail: turnDetail(turnListen),
		Hint:   "devcli remote livekit sync (needs ACME certs for TURN/TLS)",
	})

	relayFW, _ := SSHOutput(cfg, "ufw status 2>/dev/null | grep '30000:40000/udp' | head -1")
	relayOK := strings.Contains(relayFW, "30000:40000")
	out = append(out, HealthResult{
		Name: "livekit-turn-relay-fw", OK: relayOK,
		Detail: strings.TrimSpace(relayFW),
		Hint:   "devcli remote livekit sync (opens TURN relay UDP 30000-40000)",
	})

	pubCode, _ := SSHOutput(cfg, fmt.Sprintf(
		"curl -sf -o /dev/null -w '%%{http_code}' --connect-timeout 5 %s 2>/dev/null || echo 000",
		shellQuote(strings.TrimRight(cfg.URLs.PublicBase, "/")+"/livekit/"),
	))
	pubCode = strings.TrimSpace(pubCode)
	pubOK := pubCode == "200" || pubCode == "404" || pubCode == "426" // routed; WS upgrade may differ
	out = append(out, HealthResult{
		Name: "livekit-public", OK: pubOK,
		Detail: fmt.Sprintf("%s/livekit/ HTTP %s", cfg.URLs.PublicBase, pubCode),
		Hint:   "devcli remote traefik sync",
	})

	out = append(out, HealthResult{
		Name: "livekit-browser-url", OK: cfg.URLs.LiveKitHost != "",
		Detail: cfg.URLs.LiveKitHost,
	})

	return out
}

func turnDetail(listen string) string {
	s := strings.TrimSpace(listen)
	if s == "" {
		return ":5349 not listening"
	}
	return "listening on :5349"
}

func probeTraefikService(cfg *Config) []HealthResult {
	for _, unit := range []string{"bedrud-traefik", "traefik"} {
		status, _ := SSHOutput(cfg, fmt.Sprintf("systemctl is-active %s 2>/dev/null || true", unit))
		status = strings.TrimSpace(status)
		if status == "active" {
			return []HealthResult{{
				Name: "traefik-service", OK: true,
				Detail: unit + " active",
			}}
		}
	}
	return []HealthResult{{
		Name: "traefik-service", OK: false,
		Detail: "bedrud-traefik/traefik not active",
		Hint:   "devcli remote traefik sync",
	}}
}

func probeHTTPFromRemote(cfg *Config, name, url, wantCode, hint string) HealthResult {
	code := remoteHTTPCode(cfg, url)
	ok := code == wantCode
	detail := fmt.Sprintf("%s HTTP %s", url, code)
	if code == "000" {
		detail = url + " unreachable"
	}
	return HealthResult{Name: name, OK: ok, Detail: detail, Hint: hint}
}

func probePublic(cfg *Config) []HealthResult {
	var out []HealthResult

	apiURL := strings.TrimRight(cfg.URLs.PublicBase, "/") + "/api/health"
	code := remoteHTTPCode(cfg, apiURL)
	out = append(out, HealthResult{
		Name: "public-api", OK: code == "200",
		Detail: fmt.Sprintf("%s HTTP %s", apiURL, code),
		Hint:   "devcli remote run --yes && devcli remote traefik sync",
	})

	client := &http.Client{Timeout: 5 * time.Second}
	resp, err := client.Get(apiURL)
	if err != nil {
		out = append(out, HealthResult{
			Name: "public-api-local", OK: false,
			Detail: err.Error(),
			Hint:   "check DNS / TLS / tunnel",
		})
	} else {
		_ = resp.Body.Close()
		out = append(out, HealthResult{
			Name: "public-api-local", OK: resp.StatusCode == 200,
			Detail: fmt.Sprintf("HTTP %d (this machine)", resp.StatusCode),
		})
	}

	return out
}

// PrintHealthReport renders probe results and returns whether all passed.
func PrintHealthReport(title string, report HealthReport) bool {
	fmt.Println(title)
	fmt.Println()
	for _, res := range report.Results {
		mark := "ok"
		if !res.OK {
			mark = "x"
		}
		fmt.Printf("  %-22s %s  %s\n", res.Name, mark, res.Detail)
		if !res.OK && res.Hint != "" {
			fmt.Printf("  %-22s     → %s\n", "", res.Hint)
		}
	}
	fmt.Println()
	failed := report.Failed()
	if len(failed) == 0 {
		fmt.Println("All checks passed.")
		return true
	}
	fmt.Printf("%d check(s) failed.\n", len(failed))
	return false
}