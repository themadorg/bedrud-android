package remote

import "fmt"

// TunnelUp brings up the configured tunnel.
func TunnelUp(cfg *Config) error {
	switch cfg.TunnelMode() {
	case TunnelModeSSH:
		return SSHTunnelUp(cfg)
	case TunnelModeWireGuard:
		return WireGuardUp(cfg)
	default:
		return DevTunnelUp(cfg)
	}
}

// TunnelDetachedUp starts a background tunnel client (devtunnel mode).
func TunnelDetachedUp(cfg *Config) error {
	switch cfg.TunnelMode() {
	case TunnelModeSSH:
		return SSHTunnelUp(cfg)
	case TunnelModeWireGuard:
		return WireGuardUp(cfg)
	default:
		return DevTunnelDetachedUp(cfg)
	}
}

// TunnelDown tears down the configured tunnel.
func TunnelDown(cfg *Config) (bool, error) {
	switch cfg.TunnelMode() {
	case TunnelModeSSH:
		return SSHTunnelDown(cfg)
	case TunnelModeWireGuard:
		return WireGuardDown(cfg)
	default:
		return DevTunnelDetachedDown(cfg)
	}
}

// TunnelStatus reports whether the configured tunnel is up.
func TunnelStatus(cfg *Config) (bool, string, error) {
	switch cfg.TunnelMode() {
	case TunnelModeSSH:
		livekit, err := readSSHTunnelLegState(cfg, sshTunnelLegLiveKit)
		if err != nil {
			return false, "", err
		}
		backends, err := readSSHTunnelLegState(cfg, sshTunnelLegBackends)
		if err != nil {
			return false, "", err
		}
		switch {
		case livekit.Up && backends.Up:
			return true, fmt.Sprintf("livekit pid %d, backends pid %d", livekit.PID, backends.PID), nil
		case livekit.Up:
			return true, fmt.Sprintf("livekit pid %d (backends down)", livekit.PID), nil
		case backends.Up:
			return true, fmt.Sprintf("backends pid %d (livekit down)", backends.PID), nil
		default:
			return false, "down", nil
		}
	case TunnelModeWireGuard:
		st, err := WireGuardStatus(cfg)
		if err != nil {
			return false, "", err
		}
		if st.Up {
			return true, fmt.Sprintf("wireguard %s (%s)", st.Interface, cfg.WireGuard.LocalTunnelIP), nil
		}
		return false, "down", nil
	default:
		up, detail, err := DevTunnelStatus(cfg)
		if err != nil {
			return false, "", err
		}
		if up {
			return true, "devtunnel " + detail, nil
		}
		return false, "down", nil
	}
}