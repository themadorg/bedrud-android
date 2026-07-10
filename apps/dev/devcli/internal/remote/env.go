package remote

import (
	"bufio"
	"fmt"
	"os"
	"path/filepath"
	"strconv"
	"strings"
)

const (
	envSSHHost              = "REMOTE_DEBUG_SSH_HOST"
	envSSHUser              = "REMOTE_DEBUG_SSH_USER"
	envSSHPort              = "REMOTE_DEBUG_SSH_PORT"
	envSSHIdentityFile      = "REMOTE_DEBUG_SSH_IDENTITY_FILE"
	envWGEndpoint           = "REMOTE_DEBUG_WG_ENDPOINT"
	envLiveKitAPISecret     = "REMOTE_DEBUG_LIVEKIT_API_SECRET"
	envACMEEmail            = "REMOTE_DEBUG_ACME_EMAIL"
	envTunnelToken          = "REMOTE_DEBUG_TUNNEL_TOKEN"
	envTunnelTLSFingerprint = "REMOTE_DEBUG_TUNNEL_TLS_FINGERPRINT"
)

// EnvPath returns server/.env under repo root.
func EnvPath(repo string) string {
	return filepath.Join(repo, "server", ".env")
}

func loadSSHFromEnv(repo string, cfg *Config) error {
	vals := map[string]string{}

	// Process environment takes precedence over .env file.
	for _, key := range []string{envSSHHost, envSSHUser, envSSHPort, envSSHIdentityFile} {
		if v := strings.TrimSpace(os.Getenv(key)); v != "" {
			vals[key] = v
		}
	}

	path := EnvPath(repo)
	fileVals, err := parseDotEnv(path)
	if err != nil {
		return err
	}
	for k, v := range fileVals {
		if _, set := vals[k]; !set && v != "" {
			vals[k] = v
		}
	}

	if host := vals[envSSHHost]; host != "" {
		cfg.SSH.Host = host
	}
	if user := vals[envSSHUser]; user != "" {
		cfg.SSH.User = user
	}
	if port := vals[envSSHPort]; port != "" {
		n, err := strconv.Atoi(port)
		if err != nil {
			return fmt.Errorf("%s must be a number (got %q)", envSSHPort, port)
		}
		cfg.SSH.Port = n
	}
	if id := vals[envSSHIdentityFile]; id != "" {
		cfg.SSH.IdentityFile = id
	}

	return nil
}

func loadProvisionFromEnv(repo string, cfg *Config) error {
	vals := map[string]string{}
	for _, key := range []string{envWGEndpoint, envLiveKitAPISecret, envACMEEmail, envTunnelToken, envTunnelTLSFingerprint} {
		if v := strings.TrimSpace(os.Getenv(key)); v != "" {
			vals[key] = v
		}
	}
	fileVals, err := parseDotEnv(EnvPath(repo))
	if err != nil {
		return err
	}
	for k, v := range fileVals {
		if _, set := vals[k]; !set && v != "" {
			vals[k] = v
		}
	}
	if ep := vals[envWGEndpoint]; ep != "" {
		cfg.Provision.WGEndpoint = ep
	}
	if secret := vals[envLiveKitAPISecret]; secret != "" {
		cfg.LiveKit.APISecret = secret
	}
	if email := vals[envACMEEmail]; email != "" {
		cfg.Provision.ACMEmail = email
	}
	if token := vals[envTunnelToken]; token != "" {
		cfg.Tunnel.DevTunnel.Token = token
	}
	if fp := vals[envTunnelTLSFingerprint]; fp != "" {
		cfg.Tunnel.DevTunnel.TLSFingerprint = fp
	}
	return nil
}

func parseDotEnv(path string) (map[string]string, error) {
	f, err := os.Open(path)
	if err != nil {
		if os.IsNotExist(err) {
			return nil, nil
		}
		return nil, err
	}
	defer f.Close()

	out := make(map[string]string)
	scanner := bufio.NewScanner(f)
	for scanner.Scan() {
		line := strings.TrimSpace(scanner.Text())
		if line == "" || strings.HasPrefix(line, "#") {
			continue
		}
		if strings.HasPrefix(line, "export ") {
			line = strings.TrimSpace(strings.TrimPrefix(line, "export "))
		}
		key, val, ok := strings.Cut(line, "=")
		if !ok {
			continue
		}
		key = strings.TrimSpace(key)
		val = strings.TrimSpace(val)
		val = strings.Trim(val, `"'`)
		if key != "" {
			out[key] = val
		}
	}
	if err := scanner.Err(); err != nil {
		return nil, fmt.Errorf("read %s: %w", path, err)
	}
	return out, nil
}