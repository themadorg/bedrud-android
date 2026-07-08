package remote

import (
	"fmt"
	"strings"
	"time"

	"bedrud/devcli/internal/logfmt"
)

// LiveKitSync uploads livekit.yaml (RTC + TURN for remote peers) and restarts only when changed.
func LiveKitSync(cfg *Config) error {
	if err := pingSSH(cfg); err != nil {
		return err
	}
	nodeIP, err := detectPublicIP(cfg)
	if err != nil {
		return err
	}
	apiSecret := cfg.LiveKit.APISecret
	if apiSecret == "" {
		return fmt.Errorf("livekit API secret missing — set REMOTE_DEBUG_LIVEKIT_API_SECRET in server/.env")
	}
	state := cfg.Provision.StateDir
	if cfg.acmeEnabled() {
		if err := syncTurnCerts(cfg, state); err != nil {
			return err
		}
	}
	if cfg.UsesWireGuard() {
		if err := repairWireGuardServer(cfg); err != nil {
			return err
		}
		if err := ensureWireGuardServer(cfg); err != nil {
			return err
		}
	}
	if cfg.UsesSSHTunnel() || cfg.UsesDevTunnel() {
		if err := disableWireGuardWhenUnused(cfg); err != nil {
			return err
		}
	}
	content := livekitYAML(cfg, nodeIP, apiSecret)
	configPath := state + "/livekit.yaml"
	hashPath := state + "/.livekit.yaml.sha256"
	newHash := contentSHA256(content)
	configChanged := remoteStoredHash(cfg, hashPath) != newHash

	if configChanged {
		if err := UploadContent(cfg, content, configPath, "644"); err != nil {
			return fmt.Errorf("upload livekit config: %w", err)
		}
		if err := storeRemoteHash(cfg, newHash, hashPath); err != nil {
			return fmt.Errorf("upload livekit config hash: %w", err)
		}
		logfmt.Println("livekit", fmt.Sprintf("config → %s/livekit.yaml (node_ip=%s, turn=on)", state, nodeIP))
	} else {
		logfmt.Println("livekit", "config unchanged — skipping upload")
	}

	// Ensure media ports are open (idempotent).
	fw := fmt.Sprintf(`
ufw allow %d/tcp comment 'LiveKit ICE/TCP' >/dev/null 2>&1 || true
ufw allow 5349/tcp comment 'LiveKit TURN/TLS' >/dev/null 2>&1 || true
ufw allow 30000:40000/udp comment 'LiveKit TURN relay' >/dev/null 2>&1 || true
ufw allow %d:%d/udp comment 'LiveKit ICE/UDP' >/dev/null 2>&1 || true
`,
		cfg.Provision.LiveKitRTCPort,
		cfg.Provision.LiveKitRTCStart,
		cfg.Provision.LiveKitRTCEnd,
	)
	if err := SSHSudo(cfg, fw); err != nil {
		return err
	}
	if configChanged {
		if err := SSHSudo(cfg, "systemctl restart bedrud-livekit"); err != nil {
			return fmt.Errorf("restart livekit: %w", err)
		}
		logfmt.Println("livekit", "restarted (bedrud-livekit)")
	} else {
		logfmt.Println("livekit", "config unchanged — skipping restart")
	}
	return ensureLiveKitService(cfg)
}

func ensureLiveKitService(cfg *Config) error {
	status, err := SSHOutput(cfg, "systemctl is-active bedrud-livekit 2>/dev/null || echo inactive")
	if err != nil {
		return err
	}
	status = strings.TrimSpace(status)
	if status == "active" {
		logfmt.Println("livekit", "server service running (bedrud-livekit)")
		return waitLiveKitHTTP(cfg, 15*time.Second)
	}
	logfmt.Println("livekit", "starting bedrud-livekit on server")
	if err := SSHSudo(cfg, "systemctl start bedrud-livekit"); err != nil {
		return fmt.Errorf("start livekit: %w", err)
	}
	return waitLiveKitHTTP(cfg, 30*time.Second)
}

// LiveKitStatus prints LiveKit service health on the remote server.
func LiveKitStatus(cfg *Config) (bool, error) {
	if err := pingSSH(cfg); err != nil {
		return false, err
	}
	report := HealthReport{Results: probeLiveKit(cfg)}
	ok := PrintHealthReport("LiveKit status", report)
	return ok, nil
}

func waitLiveKitHTTP(cfg *Config, timeout time.Duration) error {
	deadline := time.Now().Add(timeout)
	url := cfg.LiveKitBackend() + "/"
	for time.Now().Before(deadline) {
		out, err := SSHOutput(cfg, fmt.Sprintf(
			"curl -sf --connect-timeout 2 %s 2>/dev/null || true",
			shellQuote(url),
		))
		if err == nil && strings.TrimSpace(out) == "OK" {
			logfmt.Println("livekit", fmt.Sprintf("remote HTTP ok (%s)", url))
			return nil
		}
		time.Sleep(300 * time.Millisecond)
	}
	return fmt.Errorf("livekit not responding at %s after %s", url, timeout)
}

// syncTurnCerts extracts the Traefik ACME cert for the public host into PEM files for LiveKit TURN/TLS.
func syncTurnCerts(cfg *Config, state string) error {
	acmePath := state + "/acme.json"
	certPath := state + "/turn.crt"
	keyPath := state + "/turn.key"
	script := fmt.Sprintf(`set -e
if [ ! -f %s ]; then
  echo "missing %s (run traefik sync after ACME)" >&2
  exit 1
fi
python3 - <<'PY'
import base64, json, sys
domain = %q
acme_path = %q
cert_path = %q
key_path = %q
with open(acme_path, encoding="utf-8") as fh:
    data = json.load(fh)
certs = data.get("letsencrypt", {}).get("Certificates", [])
match = None
for entry in certs:
    main = entry.get("domain", {}).get("main", "")
    sans = entry.get("domain", {}).get("sans") or []
    if main == domain or domain in sans:
        match = entry
        break
if not match:
    print(f"no ACME cert for {domain} in {acme_path}", file=sys.stderr)
    sys.exit(1)
with open(cert_path, "wb") as fh:
    fh.write(base64.b64decode(match["certificate"]))
with open(key_path, "wb") as fh:
    fh.write(base64.b64decode(match["key"]))
PY
chmod 600 %s %s
`, shellQuote(acmePath), acmePath, cfg.URLs.PublicHost, acmePath, certPath, keyPath, shellQuote(certPath), shellQuote(keyPath))
	if err := SSHSudo(cfg, script); err != nil {
		return fmt.Errorf("sync TURN TLS certs: %w", err)
	}
	logfmt.Println("livekit", fmt.Sprintf("TURN TLS certs → %s/turn.crt", state))
	return nil
}

// repairWireGuardServer fixes a historical bug that set the server tunnel address to 10.0.0.0/24.
func repairWireGuardServer(cfg *Config) error {
	iface := cfg.Provision.WireGuardServerInterface
	if iface == "" {
		iface = "wg0"
	}
	path := fmt.Sprintf("/etc/wireguard/%s.conf", iface)
	serverIP := strings.TrimSpace(cfg.WireGuard.RemoteTunnelIP)
	if serverIP == "" {
		serverIP = "10.0.0.1"
	}
	script := fmt.Sprintf(`set -e
if [ ! -f %s ]; then
  exit 0
fi
if grep -q '^Address = 10.0.0.0/24' %s; then
  sed -i 's|^Address = 10.0.0.0/24|Address = %s/24|' %s
  systemctl restart wg-quick@%s
  echo "wireguard | repaired server Address → %s/24"
fi
`, shellQuote(path), shellQuote(path), serverIP, shellQuote(path), iface, serverIP)
	if err := SSHSudo(cfg, script); err != nil {
		return fmt.Errorf("repair wireguard: %w", err)
	}
	logfmt.Println("wireguard", fmt.Sprintf("checked %s (server IP %s)", path, serverIP))
	return nil
}

// ensureWireGuardServer starts wg0 on the server when tunnel.mode is wireguard.
func ensureWireGuardServer(cfg *Config) error {
	if cfg.UsesSSHTunnel() {
		return nil
	}
	iface := cfg.Provision.WireGuardServerInterface
	if iface == "" {
		iface = "wg0"
	}
	status, err := SSHOutput(cfg, fmt.Sprintf("systemctl is-active wg-quick@%s 2>/dev/null || echo inactive", iface))
	if err != nil {
		return err
	}
	status = strings.TrimSpace(status)
	if status == "active" {
		logfmt.Println("wireguard", fmt.Sprintf("server %s active", iface))
		return nil
	}
	logfmt.Println("wireguard", fmt.Sprintf("starting server %s (wireguard tunnel mode)", iface))
	if err := SSHSudo(cfg, fmt.Sprintf("systemctl start wg-quick@%s", iface)); err != nil {
		return fmt.Errorf("start server wireguard: %w", err)
	}
	return nil
}

// disableWireGuardWhenUnused stops wg0 when tunnel mode does not use WireGuard.
// A misconfigured wg0 address pollutes LiveKit ICE candidates.
func disableWireGuardWhenUnused(cfg *Config) error {
	if cfg.UsesWireGuard() {
		return nil
	}
	iface := cfg.Provision.WireGuardServerInterface
	if iface == "" {
		iface = "wg0"
	}
	if err := SSHSudo(cfg, fmt.Sprintf("systemctl stop wg-quick@%s 2>/dev/null || true", iface)); err != nil {
		return err
	}
	logfmt.Println("wireguard", fmt.Sprintf("stopped %s (not used by %s tunnel)", iface, cfg.TunnelMode()))
	return nil
}