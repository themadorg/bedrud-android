package remote

import (
	"fmt"
	"strings"

	"bedrud/devcli/internal/tunnel"
)

func devTunnelCredentials(cfg *Config) error {
	if err := devTunnelToken(cfg); err != nil {
		return err
	}
	fp, err := ensureDevTunnelTLSFingerprint(cfg)
	if err != nil {
		return err
	}
	cfg.Tunnel.DevTunnel.TLSFingerprint = fp
	return nil
}

// loadDevTunnelTLSFingerprint fills the fingerprint when available (best-effort).
func loadDevTunnelTLSFingerprint(cfg *Config) {
	if fp := strings.TrimSpace(cfg.Tunnel.DevTunnel.TLSFingerprint); fp != "" {
		cfg.Tunnel.DevTunnel.TLSFingerprint = strings.ToLower(strings.TrimPrefix(fp, "sha256:"))
		return
	}
	if fp, err := fetchRemoteDevTunnelTLSFingerprint(cfg); err == nil && fp != "" {
		cfg.Tunnel.DevTunnel.TLSFingerprint = fp
	}
}

func ensureDevTunnelTLSFingerprint(cfg *Config) (string, error) {
	if fp := strings.TrimSpace(cfg.Tunnel.DevTunnel.TLSFingerprint); fp != "" {
		return strings.ToLower(strings.TrimPrefix(fp, "sha256:")), nil
	}
	fp, err := fetchRemoteDevTunnelTLSFingerprint(cfg)
	if err != nil {
		return "", fmt.Errorf("REMOTE_DEBUG_TUNNEL_TLS_FINGERPRINT missing in server/.env (run: devcli remote tunnel deploy): %w", err)
	}
	fmt.Println("deploy | fetched tunnel TLS fingerprint from server — add to server/.env:")
	fmt.Printf("  REMOTE_DEBUG_TUNNEL_TLS_FINGERPRINT=%s\n", fp)
	return fp, nil
}

func fetchRemoteDevTunnelTLSFingerprint(cfg *Config) (string, error) {
	if err := pingSSH(cfg); err != nil {
		return "", err
	}
	path := cfg.DevTunnelTLSCertPath()
	out, err := SSHOutput(cfg, fmt.Sprintf("cat %s 2>/dev/null || true", shellQuote(path)))
	if err != nil || strings.TrimSpace(out) == "" {
		return "", fmt.Errorf("tunnel cert missing at %s", path)
	}
	return tunnel.FingerprintCertPEM([]byte(out))
}

func devTunnelTLSHosts(cfg *Config) []string {
	seen := make(map[string]struct{})
	var hosts []string
	add := func(v string) {
		v = strings.TrimSpace(v)
		if v == "" {
			return
		}
		if _, ok := seen[v]; ok {
			return
		}
		seen[v] = struct{}{}
		hosts = append(hosts, v)
	}
	add(cfg.URLs.PublicHost)
	add(cfg.SSH.Host)
	return hosts
}

func clientTunnelConfig(cfg *Config) tunnel.ClientConfig {
	return tunnel.ClientConfig{
		ServerAddr:       cfg.DevTunnelServerAddr(),
		Token:            cfg.Tunnel.DevTunnel.Token,
		TLSFingerprint:   cfg.Tunnel.DevTunnel.TLSFingerprint,
		TLSServerName:    cfg.DevTunnelTLSServerName(),
		LocalWebPort:     cfg.Local.WebPort,
		LocalAPIPort:     cfg.Local.APIPort,
		LocalLiveKitPort: cfg.Tunnel.SSH.LocalLiveKitPort,
	}
}