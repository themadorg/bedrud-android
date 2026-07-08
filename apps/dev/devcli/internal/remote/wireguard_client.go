package remote

import (
	"fmt"
	"net"
	"os"
	"os/exec"
	"strings"
	"time"

	"bedrud/devcli/internal/logfmt"
)

// WireGuardEnsureClientConfig writes the local WG client config when missing.
// If the server already has a peer, a new client keypair is generated and the server peer is updated.
func WireGuardEnsureClientConfig(cfg *Config) error {
	if err := wgConfigExists(cfg); err == nil {
		return normalizeClientWGConfig(cfg)
	}
	logfmt.Println("wireguard", fmt.Sprintf("client config missing — bootstrapping %s", cfg.WireGuard.ConfigFile))

	serverPub, err := serverWGPublicKey(cfg)
	if err != nil {
		return err
	}
	clientPriv, clientPub, err := wgKeyPair()
	if err != nil {
		return fmt.Errorf("generate client keys: %w", err)
	}
	if err := updateServerWGPeer(cfg, clientPub); err != nil {
		return err
	}
	return writeClientWGConfig(cfg, clientPriv, serverPub)
}

func serverWGPublicKey(cfg *Config) (string, error) {
	iface := cfg.Provision.WireGuardServerInterface
	if iface == "" {
		iface = "wg0"
	}
	out, err := SSHOutput(cfg, fmt.Sprintf("wg show %s public-key 2>/dev/null || true", iface))
	if err == nil {
		if pub := strings.TrimSpace(out); pub != "" {
			return pub, nil
		}
	}
	privLine, err := SSHOutput(cfg, fmt.Sprintf(
		"grep '^PrivateKey' /etc/wireguard/%s.conf 2>/dev/null | head -1 | cut -d= -f2 | tr -d ' '",
		iface,
	))
	if err != nil || strings.TrimSpace(privLine) == "" {
		return "", fmt.Errorf("read server WireGuard public key (is wg0 provisioned?)")
	}
	wg, err := exec.LookPath("wg")
	if err != nil {
		return "", fmt.Errorf("wg not found in PATH")
	}
	cmd := exec.Command(wg, "pubkey")
	cmd.Stdin = strings.NewReader(strings.TrimSpace(privLine) + "\n")
	pubOut, err := cmd.Output()
	if err != nil {
		return "", fmt.Errorf("derive server public key: %w", err)
	}
	return strings.TrimSpace(string(pubOut)), nil
}

func updateServerWGPeer(cfg *Config, clientPub string) error {
	iface := cfg.Provision.WireGuardServerInterface
	if iface == "" {
		iface = "wg0"
	}
	clientIP := strings.TrimSpace(cfg.WireGuard.LocalTunnelIP)
	if clientIP == "" {
		clientIP = "10.0.0.2"
	}
	path := fmt.Sprintf("/etc/wireguard/%s.conf", iface)
	script := fmt.Sprintf(`set -euo pipefail
iface=%s
path=%s
newpub=%s
client_ip=%s
oldpub=$(grep '^PublicKey' "$path" 2>/dev/null | head -1 | cut -d= -f2 | tr -d ' ' || true)
if systemctl is-active --quiet wg-quick@"$iface" 2>/dev/null; then
  if [ -n "$oldpub" ]; then
    wg set "$iface" peer "$oldpub" remove 2>/dev/null || true
  fi
  wg set "$iface" peer "$newpub" allowed-ips "${client_ip}/32"
fi
sed -i "s|^PublicKey = .*|PublicKey = ${newpub}|" "$path"
systemctl restart wg-quick@"$iface"
`,
		shellQuote(iface),
		shellQuote(path),
		shellQuote(clientPub),
		shellQuote(clientIP),
	)
	if err := SSHSudo(cfg, script); err != nil {
		return fmt.Errorf("update server WireGuard peer: %w", err)
	}
	logfmt.Println("wireguard", fmt.Sprintf("server %s peer updated (%s/32)", iface, clientIP))
	return nil
}

func normalizeClientWGConfig(cfg *Config) error {
	data, err := os.ReadFile(cfg.WireGuard.ConfigFile)
	if err != nil {
		return err
	}
	content := string(data)
	remoteIP := strings.TrimSpace(cfg.WireGuard.RemoteTunnelIP)
	if remoteIP == "" {
		remoteIP = "10.0.0.1"
	}
	wantAllowed := "AllowedIPs = " + remoteIP + "/32"
	endpointOK := true
	if ep := parseWGEndpointLine(content); ep != "" {
		host, _, err := net.SplitHostPort(ep)
		endpointOK = err == nil && net.ParseIP(host) != nil
	}
	if !strings.Contains(content, "DNS =") && strings.Contains(content, wantAllowed) && endpointOK {
		return nil
	}
	var priv, pub string
	for _, line := range strings.Split(content, "\n") {
		line = strings.TrimSpace(line)
		if strings.HasPrefix(line, "PrivateKey =") {
			priv = strings.TrimSpace(strings.TrimPrefix(line, "PrivateKey ="))
		}
		if strings.HasPrefix(line, "PublicKey =") {
			pub = strings.TrimSpace(strings.TrimPrefix(line, "PublicKey ="))
		}
	}
	if priv == "" || pub == "" {
		return fmt.Errorf("cannot normalize %s (missing keys — run: devcli remote wg sync after deleting file)", cfg.WireGuard.ConfigFile)
	}
	logfmt.Println("wireguard", "normalizing client config (split-tunnel, IP endpoint, no DNS)")
	return writeClientWGConfig(cfg, priv, pub)
}

func parseWGEndpointLine(content string) string {
	for _, line := range strings.Split(content, "\n") {
		line = strings.TrimSpace(line)
		if strings.HasPrefix(line, "Endpoint =") {
			return strings.TrimSpace(strings.TrimPrefix(line, "Endpoint ="))
		}
	}
	return ""
}

// WaitWireGuardReady blocks until the tunnel can reach remote LiveKit over WireGuard.
func WaitWireGuardReady(cfg *Config, timeout time.Duration) error {
	iface := cfg.WireGuard.Interface
	if iface == "" {
		iface = interfaceFromConfig(cfg.WireGuard.ConfigFile)
	}
	target := strings.TrimRight(cfg.URLs.LiveKitInternal, "/") + "/"
	deadline := time.Now().Add(timeout)
	for time.Now().Before(deadline) {
		code, err := curlLocal(target)
		if err == nil && code == "200" {
			logfmt.Println("wireguard", fmt.Sprintf("%s ready (%s → OK)", iface, target))
			return nil
		}
		time.Sleep(300 * time.Millisecond)
	}
	return fmt.Errorf("wireguard %s: %s not reachable after %s", iface, target, timeout)
}