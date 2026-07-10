package remote

import (
	"crypto/rand"
	"encoding/hex"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
)

// ProvisionOptions controls remote server bootstrap.
type ProvisionOptions struct {
	Force       bool
	SkipLocalWG bool // skip writing local wg client config (e.g. Incus smoke test)
}

// Provision bootstraps a fresh Debian server with WireGuard, Traefik, and LiveKit.
func Provision(cfg *Config, opts ProvisionOptions) error {
	fmt.Println("provision | checking SSH...")
	if err := pingSSH(cfg); err != nil {
		return err
	}

	if cfg.acmeEnabled() && cfg.Provision.ACMEmail == "" {
		fmt.Println("provision | warning: ACME enabled but REMOTE_DEBUG_ACME_EMAIL unset — using admin@" + cfg.URLs.PublicHost)
	}

	apiSecret := cfg.LiveKit.APISecret
	if apiSecret == "" {
		var err error
		apiSecret, err = randomSecret(32)
		if err != nil {
			return err
		}
		fmt.Println("provision | generated LiveKit API secret — add to server/.env:")
		fmt.Printf("  REMOTE_DEBUG_LIVEKIT_API_SECRET=%s\n", apiSecret)
	}

	var clientPriv, clientPub string
	if cfg.UsesWireGuard() {
		fmt.Println("provision | generating WireGuard keys...")
		var err error
		clientPriv, clientPub, err = wgKeyPair()
		if err != nil {
			return fmt.Errorf("wireguard keys (install wireguard-tools locally): %w", err)
		}
	}

	fmt.Println("provision | checking remote OS...")
	if err := checkRemoteDebian(cfg); err != nil {
		return err
	}

	fmt.Println("provision | installing packages (wireguard, curl, ufw)...")
	if err := installRemotePackages(cfg); err != nil {
		return err
	}

	wanIface, err := detectWANIface(cfg)
	if err != nil {
		return err
	}
	nodeIP, err := detectPublicIP(cfg)
	if err != nil {
		return err
	}
	fmt.Printf("provision | WAN interface: %s  public IP: %s\n", wanIface, nodeIP)

	fmt.Println("provision | installing LiveKit...")
	if err := installLiveKitBinary(cfg); err != nil {
		return err
	}

	fmt.Println("provision | installing Traefik...")
	if err := installTraefikBinary(cfg); err != nil {
		return err
	}

	fmt.Println("provision | writing configs...")
	state := cfg.Provision.StateDir
	if err := SSHSudo(cfg, fmt.Sprintf("mkdir -p %s %s && chmod 755 %s %s",
		shellQuote(state), shellQuote(cfg.Traefik.DynamicDir),
		shellQuote(state), shellQuote(cfg.Traefik.DynamicDir))); err != nil {
		return err
	}

	if err := UploadContent(cfg, livekitYAML(cfg, nodeIP, apiSecret), state+"/livekit.yaml", "644"); err != nil {
		return err
	}
	if err := UploadContent(cfg, traefikStaticYAML(cfg), state+"/traefik.yml", "644"); err != nil {
		return err
	}
	if cfg.acmeEnabled() {
		if err := SSHSudo(cfg, fmt.Sprintf("touch %s/acme.json && chmod 600 %s/acme.json",
			state, state)); err != nil {
			return err
		}
	}
	if err := UploadContent(cfg, livekitUnit(cfg), "/etc/systemd/system/bedrud-livekit.service", "644"); err != nil {
		return err
	}
	if err := UploadContent(cfg, traefikUnit(cfg), "/etc/systemd/system/bedrud-traefik.service", "644"); err != nil {
		return err
	}

	if cfg.UsesWireGuard() {
		fmt.Println("provision | configuring WireGuard server...")
		serverPriv, serverPub, err := wgKeyPairRemote(cfg)
		if err != nil {
			return err
		}
		wgIface := cfg.Provision.WireGuardServerInterface
		wgPath := fmt.Sprintf("/etc/wireguard/%s.conf", wgIface)
		if err := UploadContent(cfg, wireguardServerConf(cfg, serverPriv, clientPub, wanIface), wgPath, "600"); err != nil {
			return err
		}
		if !opts.SkipLocalWG {
			fmt.Println("provision | writing local WireGuard client config...")
			if err := writeClientWGConfig(cfg, clientPriv, serverPub); err != nil {
				return err
			}
		} else {
			fmt.Println("provision | skipping local WireGuard client config (--skip-local-wg)")
		}
	}
	if cfg.UsesDevTunnel() {
		fmt.Println("provision | deploying devtunnel agent...")
		if err := DevTunnelDeploy(cfg); err != nil {
			return err
		}
	}

	fmt.Println("provision | configuring firewall...")
	if err := configureFirewall(cfg); err != nil {
		return err
	}

	fmt.Println("provision | enabling IP forwarding...")
	if err := SSHSudo(cfg, `grep -q '^net.ipv4.ip_forward=1' /etc/sysctl.d/99-bedrud-debug.conf 2>/dev/null || echo 'net.ipv4.ip_forward=1' | sudo tee /etc/sysctl.d/99-bedrud-debug.conf && sudo sysctl -p /etc/sysctl.d/99-bedrud-debug.conf`); err != nil {
		return err
	}

	fmt.Println("provision | starting services...")
	wgIface := cfg.Provision.WireGuardServerInterface
	if wgIface == "" {
		wgIface = "wg0"
	}
	startScript := `systemctl daemon-reload
systemctl enable bedrud-livekit bedrud-traefik
systemctl restart bedrud-livekit bedrud-traefik
`
	if cfg.UsesWireGuard() {
		startScript += fmt.Sprintf("systemctl enable wg-quick@%s\nsystemctl restart wg-quick@%s\n", wgIface, wgIface)
	}
	if cfg.UsesDevTunnel() {
		startScript += "systemctl enable bedrud-devtunnel\nsystemctl restart bedrud-devtunnel\n"
	}
	if err := SSHSudo(cfg, startScript); err != nil {
		return err
	}

	fmt.Println("provision | syncing Traefik routes...")
	if err := TraefikSync(cfg); err != nil {
		return err
	}

	printProvisionDone(cfg, apiSecret)
	return nil
}

func printProvisionDone(cfg *Config, apiSecret string) {
	fmt.Println()
	fmt.Println("✅ Remote debug server provisioned")
	fmt.Println()
	fmt.Printf("  Public URL:     %s\n", cfg.URLs.PublicBase)
	fmt.Printf("  LiveKit (browser): %s\n", cfg.URLs.LiveKitHost)
	fmt.Printf("  LiveKit API:    %s\n", cfg.URLs.LiveKitInternal)
	if cfg.UsesWireGuard() {
		fmt.Printf("  WG client:      %s\n", cfg.WireGuard.ConfigFile)
	}
	if cfg.UsesDevTunnel() {
		fmt.Printf("  devtunnel:      %s (token in server/.env)\n", cfg.DevTunnelServerAddr())
	}
	fmt.Println()
	fmt.Println("Next steps:")
	fmt.Println("  1. Point DNS", cfg.URLs.PublicHost, "→ server public IP")
	fmt.Println("  2. Add to server/.env (if not already):")
	fmt.Printf("     REMOTE_DEBUG_LIVEKIT_API_SECRET=%s\n", apiSecret)
	if cfg.UsesDevTunnel() {
		fmt.Println("     REMOTE_DEBUG_TUNNEL_TOKEN=<from deploy output>")
	}
	fmt.Println("  3. Match livekit.apiKey in remote-debug.yaml with server/config.yaml")
	step := 4
	if cfg.UsesWireGuard() {
		fmt.Printf("  %d. devcli remote wg up\n", step)
		step++
	}
	fmt.Printf("  %d. devcli remote run --yes\n", step)
	fmt.Println()
}

func checkRemoteDebian(cfg *Config) error {
	out, err := SSHOutput(cfg, "test -f /etc/debian_version && . /etc/os-release && echo ${ID}-${VERSION_ID}")
	if err != nil {
		return fmt.Errorf("remote OS check failed: %w", err)
	}
	if !strings.HasPrefix(out, "debian-") {
		return fmt.Errorf("remote OS is %q — provision supports Debian only", out)
	}
	// Debian 12+ (bookworm/trixie)
	ver := strings.TrimPrefix(out, "debian-")
	major := strings.Split(ver, ".")[0]
	if major != "12" && major != "13" && !strings.HasPrefix(ver, "13") && !strings.HasPrefix(ver, "12") {
		fmt.Printf("provision | warning: untested Debian version %s (expected 12/13)\n", ver)
	}
	if cfg.SSH.User != "root" {
		sudo, err := SSHOutput(cfg, "sudo -n true 2>/dev/null && echo ok || echo no")
		if err != nil || sudo != "ok" {
			return fmt.Errorf("passwordless sudo required on %s (or set REMOTE_DEBUG_SSH_USER=root in server/.env)", cfg.SSHTarget())
		}
	}
	return nil
}

func installRemotePackages(cfg *Config) error {
	script := `export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get install -y -qq wireguard wireguard-tools curl ca-certificates ufw iproute2 iptables
`
	return SSHSudo(cfg, script)
}

func detectWANIface(cfg *Config) (string, error) {
	out, err := SSHOutput(cfg, "ip route show default | awk '{print $5; exit}'")
	if err != nil || out == "" {
		return "", fmt.Errorf("detect WAN interface: %w", err)
	}
	return out, nil
}

func detectPublicIP(cfg *Config) (string, error) {
	out, err := SSHOutput(cfg, "curl -4 -sf --max-time 10 https://ifconfig.me/ip || hostname -I | awk '{print $1}'")
	if err != nil || out == "" {
		// Fallback: resolve public host
		out2, err2 := SSHOutput(cfg, fmt.Sprintf("getent ahostsv4 %s | awk '{print $1; exit}'", shellQuote(cfg.URLs.PublicHost)))
		if err2 != nil || out2 == "" {
			return "", fmt.Errorf("detect public IP — set DNS for %s first", cfg.URLs.PublicHost)
		}
		return out2, nil
	}
	return strings.TrimSpace(out), nil
}

func installLiveKitBinary(cfg *Config) error {
	script := `
set -e
ARCH=$(uname -m)
case "$ARCH" in
  x86_64)  LK_ARCH=amd64 ;;
  aarch64) LK_ARCH=arm64 ;;
  *) echo "unsupported arch: $ARCH" >&2; exit 1 ;;
esac
if command -v livekit-server >/dev/null && livekit-server --version >/dev/null 2>&1; then
  echo "livekit-server already installed"
  exit 0
fi
LK_URL=$(curl -sf https://api.github.com/repos/livekit/livekit/releases/latest | grep "browser_download_url" | grep "linux_${LK_ARCH}.tar.gz" | head -1 | cut -d'"' -f4)
curl -sfL "$LK_URL" -o /tmp/livekit.tar.gz
tar -xzf /tmp/livekit.tar.gz -C /tmp livekit-server
sudo install -m 755 /tmp/livekit-server /usr/local/bin/livekit-server
rm -f /tmp/livekit.tar.gz /tmp/livekit-server
livekit-server --version
`
	return SSHSudo(cfg, script)
}

func installTraefikBinary(cfg *Config) error {
	script := `
set -e
ARCH=$(uname -m)
case "$ARCH" in
  x86_64)  T_ARCH=amd64 ;;
  aarch64) T_ARCH=arm64 ;;
  *) echo "unsupported arch: $ARCH" >&2; exit 1 ;;
esac
if command -v traefik >/dev/null && traefik version >/dev/null 2>&1; then
  echo "traefik already installed"
  exit 0
fi
T_VERSION=v3.4.5
T_URL="https://github.com/traefik/traefik/releases/download/${T_VERSION}/traefik_${T_VERSION}_linux_${T_ARCH}.tar.gz"
curl -sfL "$T_URL" -o /tmp/traefik.tar.gz
tar -xzf /tmp/traefik.tar.gz -C /tmp traefik
sudo install -m 755 /tmp/traefik /usr/local/bin/traefik
rm -f /tmp/traefik.tar.gz /tmp/traefik
traefik version
`
	return SSHSudo(cfg, script)
}

func configureFirewall(cfg *Config) error {
	tunnelPort := cfg.Provision.WireGuardPort
	if cfg.UsesDevTunnel() {
		tunnelPort = cfg.Tunnel.DevTunnel.Port
	}
	script := fmt.Sprintf(`
ufw --force reset
ufw default deny incoming
ufw default allow outgoing
ufw allow %d/tcp
ufw allow 80/tcp
ufw allow 443/tcp
ufw allow %d/tcp
ufw allow 3478/udp
ufw allow %d/udp
ufw allow %d:%d/udp
ufw --force enable
ufw status
`,
		cfg.SSH.Port,
		tunnelPort,
		cfg.Provision.LiveKitRTCPort,
		cfg.Provision.LiveKitRTCStart,
		cfg.Provision.LiveKitRTCEnd,
	)
	return SSHSudo(cfg, script)
}

func wgKeyPair() (priv, pub string, err error) {
	wg, err := exec.LookPath("wg")
	if err != nil {
		return "", "", err
	}
	privOut, err := exec.Command(wg, "genkey").Output()
	if err != nil {
		return "", "", err
	}
	priv = strings.TrimSpace(string(privOut))
	pubCmd := exec.Command(wg, "pubkey")
	pubCmd.Stdin = strings.NewReader(priv + "\n")
	pubOut, err := pubCmd.Output()
	if err != nil {
		return "", "", err
	}
	pub = strings.TrimSpace(string(pubOut))
	return priv, pub, nil
}

func wgKeyPairRemote(cfg *Config) (priv, pub string, err error) {
	out, err := SSHOutput(cfg, "wg genkey | tee /tmp/bedrud-wg-server-priv | wg pubkey > /tmp/bedrud-wg-server-pub && cat /tmp/bedrud-wg-server-priv && echo --- && cat /tmp/bedrud-wg-server-pub")
	if err != nil {
		return "", "", err
	}
	parts := strings.Split(out, "---")
	if len(parts) < 2 {
		return "", "", fmt.Errorf("unexpected wg key output")
	}
	return strings.TrimSpace(parts[0]), strings.TrimSpace(parts[1]), nil
}

func writeClientWGConfig(cfg *Config, clientPriv, serverPub string) error {
	content, err := wireguardClientConf(cfg, clientPriv, serverPub)
	if err != nil {
		return err
	}
	path := cfg.WireGuard.ConfigFile
	if err := os.MkdirAll(filepath.Dir(path), 0o700); err != nil {
		return err
	}
	if err := os.WriteFile(path, []byte(content), 0o600); err != nil {
		return err
	}
	fmt.Printf("provision | wrote %s\n", path)
	return nil
}

func randomSecret(n int) (string, error) {
	b := make([]byte, n)
	if _, err := rand.Read(b); err != nil {
		return "", err
	}
	return hex.EncodeToString(b), nil
}