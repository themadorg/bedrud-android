package remote

import (
	"os"
	"strings"
)

// StopInfra tears down the remote-debug tunnel when remote-debug.yaml is configured.
func StopInfra(repo string) (bool, string, error) {
	cfg, err := Load(repo)
	if err != nil {
		if os.IsNotExist(err) || strings.Contains(err.Error(), "remote-debug.yaml") {
			return false, "", nil
		}
		return false, "", err
	}

	up, detail, err := TunnelStatus(cfg)
	if err != nil {
		return false, "", err
	}

	stopped, err := TunnelDown(cfg)
	if err != nil {
		return false, "", err
	}
	if !stopped {
		return false, "", nil
	}

	label := cfg.TunnelMode()
	if up {
		if detail != "" {
			label = detail
		}
	} else {
		label += " (cleaned up)"
	}
	return true, label, nil
}