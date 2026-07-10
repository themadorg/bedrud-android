package remote

import (
	"fmt"
	"os"
	"os/exec"
	"strings"

	"bedrud/devcli/internal/logfmt"
)

// WGStatus reports whether the WireGuard tunnel is up.
type WGStatus struct {
	Up              bool
	Interface       string
	LocalIP         string
	Endpoint        string
	LatestHandshake string
	Detail          string
	Raw             string
}

// WireGuardUp brings up the tunnel (userspace wireguard-go or kernel wg-quick).
func WireGuardUp(cfg *Config) error {
	if err := wgConfigExists(cfg); err != nil {
		return err
	}
	if st, _ := WireGuardStatus(cfg); st != nil && st.Up {
		logfmt.Println("wireguard", fmt.Sprintf("%s already up (%s)", cfg.WireGuard.Interface, cfg.WireGuard.LocalTunnelIP))
		return nil
	}
	if cfg.WireGuardUserspace() {
		return wireguardUserspaceUp(cfg)
	}
	return run("wg-quick", "up", cfg.WireGuard.ConfigFile)
}

func wireguardUserspaceImpl() (string, error) {
	for _, name := range []string{"wireguard", "wireguard-go", "amneziawg-go"} {
		if path, err := exec.LookPath(name); err == nil {
			return path, nil
		}
	}
	return "", fmt.Errorf("userspace WireGuard binary not found (run: go install golang.zx2c4.com/wireguard@latest)")
}

// WireGuardDown tears down the tunnel.
func WireGuardDown(cfg *Config) (bool, error) {
	iface := cfg.WireGuard.Interface
	if iface == "" {
		iface = interfaceFromConfig(cfg.WireGuard.ConfigFile)
	}
	if st, _ := WireGuardStatus(cfg); st != nil && !st.Up {
		return false, nil
	}
	var err error
	if cfg.WireGuardUserspace() {
		return wireguardUserspaceDown(cfg)
	}
	err = run("wg-quick", "down", iface)
	if err != nil {
		err = run("wg-quick", "down", cfg.WireGuard.ConfigFile)
	}
	if err != nil {
		return false, err
	}
	logfmt.Println("wireguard", iface+" down")
	return true, nil
}

// WireGuardStatus inspects the tunnel via embedded device or `wg show`.
func WireGuardStatus(cfg *Config) (*WGStatus, error) {
	iface := cfg.WireGuard.Interface
	if iface == "" {
		iface = interfaceFromConfig(cfg.WireGuard.ConfigFile)
	}
	if cfg.WireGuardUserspace() && embeddedWGUp() {
		return &WGStatus{
			Up:        true,
			Interface: iface,
			LocalIP:   cfg.WireGuard.LocalTunnelIP,
			Detail:    "netstack (no kernel TUN)",
		}, nil
	}
	path, err := exec.LookPath("wg")
	if err != nil {
		return nil, fmt.Errorf("wg not found in PATH")
	}
	out, err := exec.Command(path, "show", iface).CombinedOutput()
	raw := strings.TrimSpace(string(out))
	if err != nil {
		if len(raw) == 0 ||
			strings.Contains(raw, "does not exist") ||
			strings.Contains(raw, "Operation not permitted") ||
			strings.Contains(raw, "Unable to access interface") {
			return &WGStatus{Up: false, Interface: iface}, nil
		}
		return nil, fmt.Errorf("wg show %s: %s", iface, raw)
	}
	st := &WGStatus{
		Up:        raw != "",
		Interface: iface,
		LocalIP:   cfg.WireGuard.LocalTunnelIP,
		Raw:       raw,
	}
	for _, line := range strings.Split(raw, "\n") {
		line = strings.TrimSpace(line)
		if strings.HasPrefix(line, "endpoint:") {
			st.Endpoint = strings.TrimSpace(strings.TrimPrefix(line, "endpoint:"))
		}
		if strings.HasPrefix(line, "latest handshake:") {
			st.LatestHandshake = strings.TrimSpace(strings.TrimPrefix(line, "latest handshake:"))
		}
	}
	return st, nil
}

func wgConfigExists(cfg *Config) error {
	if _, err := os.Stat(cfg.WireGuard.ConfigFile); err != nil {
		return fmt.Errorf("wireguard config missing: %s (run: devcli remote wg sync)", cfg.WireGuard.ConfigFile)
	}
	return nil
}

func interfaceFromConfig(path string) string {
	base := path
	if idx := strings.LastIndex(path, "/"); idx >= 0 {
		base = path[idx+1:]
	}
	return strings.TrimSuffix(base, ".conf")
}

