package remote

import (
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strings"

	"bedrud/devcli/internal/logfmt"
	"bedrud/devcli/internal/tunnel"
)

// DevTunnelDeploy installs the devtunnel server agent on the remote debug host.
func DevTunnelDeploy(cfg *Config) error {
	if err := pingSSH(cfg); err != nil {
		return err
	}
	token, generated, err := ensureDevTunnelToken(cfg)
	if err != nil {
		return err
	}
	if generated {
		fmt.Println("deploy | generated tunnel token — add to server/.env:")
		fmt.Printf("  REMOTE_DEBUG_TUNNEL_TOKEN=%s\n", token)
	}
	cfg.Tunnel.DevTunnel.Token = token

	tlsFP, localCert, localKey, tlsCleanup, tlsGenerated, err := prepareDevTunnelTLSFiles(cfg)
	if err != nil {
		return err
	}
	defer tlsCleanup()
	cfg.Tunnel.DevTunnel.TLSFingerprint = tlsFP
	if tlsGenerated || strings.TrimSpace(os.Getenv("REMOTE_DEBUG_TUNNEL_TLS_FINGERPRINT")) == "" {
		fmt.Println("deploy | tunnel TLS fingerprint — add to server/.env:")
		fmt.Printf("  REMOTE_DEBUG_TUNNEL_TLS_FINGERPRINT=%s\n", tlsFP)
	}

	binary, err := buildDevCLILinux(cfg)
	if err != nil {
		return err
	}
	defer os.Remove(binary)

	if err := SCP(cfg, []string{binary, localCert, localKey}, "/tmp"); err != nil {
		return fmt.Errorf("upload devcli: %w", err)
	}
	state := cfg.Provision.StateDir
	tokenPath := state + "/tunnel.token"
	certPath := cfg.DevTunnelTLSCertPath()
	keyPath := cfg.DevTunnelTLSKeyPath()
	unitPath := "/etc/systemd/system/bedrud-devtunnel.service"
	script := fmt.Sprintf(`set -euo pipefail
sudo install -m 755 /tmp/devcli-linux /usr/local/bin/devcli
sudo mkdir -p %s
printf '%%s' %s | sudo tee %s >/dev/null
sudo chmod 600 %s
sudo install -m 644 /tmp/tunnel.crt %s
sudo install -m 600 /tmp/tunnel.key %s
printf '%%s' %s | sudo tee %s >/dev/null
sudo systemctl daemon-reload
sudo systemctl enable bedrud-devtunnel
sudo systemctl restart bedrud-devtunnel
`,
		shellQuote(state),
		shellQuote(token),
		shellQuote(tokenPath),
		shellQuote(tokenPath),
		shellQuote(certPath),
		shellQuote(keyPath),
		shellQuote(devTunnelUnit(cfg, tokenPath, certPath, keyPath)),
		shellQuote(unitPath),
	)
	if err := SSH(cfg, "bash", "-c", script); err != nil {
		return fmt.Errorf("install devtunnel agent: %w", err)
	}
	if err := ensureDevTunnelFirewall(cfg); err != nil {
		return err
	}
	logfmt.Println("deploy", fmt.Sprintf("devtunnel agent listening on :%d", cfg.Tunnel.DevTunnel.Port))
	return nil
}

func ensureDevTunnelToken(cfg *Config) (string, bool, error) {
	if token := strings.TrimSpace(cfg.Tunnel.DevTunnel.Token); token != "" {
		return token, false, nil
	}
	token, err := randomSecret(24)
	if err != nil {
		return "", false, err
	}
	return token, true, nil
}

func buildDevCLILinux(cfg *Config) (string, error) {
	repo, err := findRepoForDetached(cfg)
	if err != nil {
		return "", err
	}
	out := filepath.Join(os.TempDir(), "devcli-linux")
	cmd := exec.Command("go", "build", "-o", out, "./cmd/devcli")
	cmd.Dir = filepath.Join(repo, "apps/dev/devcli")
	cmd.Env = append(os.Environ(), "GOOS=linux", "GOARCH=amd64", "CGO_ENABLED=0")
	_ = runtime.GOOS
	outBytes, err := cmd.CombinedOutput()
	if err != nil {
		return "", fmt.Errorf("build devcli for linux/amd64: %w\n%s", err, string(outBytes))
	}
	return out, nil
}

func devTunnelUnit(cfg *Config, tokenPath, certPath, keyPath string) string {
	return fmt.Sprintf(`[Unit]
Description=Bedrud devtunnel agent (remote debug)
After=network-online.target bedrud-livekit.service
Wants=network-online.target

[Service]
Type=simple
ExecStart=/usr/local/bin/devcli tunnel-server --listen :%d --token-file %s --tls-cert %s --tls-key %s --web-port %d --api-port %d --livekit-port %d
Restart=on-failure
RestartSec=3

[Install]
WantedBy=multi-user.target
`,
		cfg.Tunnel.DevTunnel.Port,
		tokenPath,
		certPath,
		keyPath,
		cfg.Tunnel.SSH.RemoteWebPort,
		cfg.Tunnel.SSH.RemoteAPIPort,
		cfg.Traefik.LiveKitPort,
	)
}

func prepareDevTunnelTLSFiles(cfg *Config) (fingerprint, localCert, localKey string, cleanup func(), generated bool, err error) {
	noop := func() {}
	if fp, err := fetchRemoteDevTunnelTLSFingerprint(cfg); err == nil && fp != "" {
		tmpDir, err := os.MkdirTemp("", "bedrud-tunnel-tls-*")
		if err != nil {
			return "", "", "", noop, false, err
		}
		certPath := cfg.DevTunnelTLSCertPath()
		keyPath := cfg.DevTunnelTLSKeyPath()
		certPEM, err := SSHOutput(cfg, fmt.Sprintf("sudo cat %s", shellQuote(certPath)))
		if err != nil || strings.TrimSpace(certPEM) == "" {
			_ = os.RemoveAll(tmpDir)
			return "", "", "", noop, false, fmt.Errorf("read remote tunnel cert at %s", certPath)
		}
		keyPEM, err := SSHOutput(cfg, fmt.Sprintf("sudo cat %s", shellQuote(keyPath)))
		if err != nil || strings.TrimSpace(keyPEM) == "" {
			_ = os.RemoveAll(tmpDir)
			return "", "", "", noop, false, fmt.Errorf("read remote tunnel key at %s", keyPath)
		}
		localCert = filepath.Join(tmpDir, "tunnel.crt")
		localKey = filepath.Join(tmpDir, "tunnel.key")
		if err := os.WriteFile(localCert, []byte(certPEM+"\n"), 0o644); err != nil {
			_ = os.RemoveAll(tmpDir)
			return "", "", "", noop, false, err
		}
		if err := os.WriteFile(localKey, []byte(keyPEM+"\n"), 0o600); err != nil {
			_ = os.RemoveAll(tmpDir)
			return "", "", "", noop, false, err
		}
		return fp, localCert, localKey, func() { _ = os.RemoveAll(tmpDir) }, false, nil
	}

	certPEM, keyPEM, fp, err := tunnel.GenerateServerTLS(devTunnelTLSHosts(cfg)...)
	if err != nil {
		return "", "", "", noop, false, err
	}

	tmpDir, err := os.MkdirTemp("", "bedrud-tunnel-tls-*")
	if err != nil {
		return "", "", "", noop, false, err
	}
	localCert = filepath.Join(tmpDir, "tunnel.crt")
	localKey = filepath.Join(tmpDir, "tunnel.key")
	if err := os.WriteFile(localCert, certPEM, 0o644); err != nil {
		_ = os.RemoveAll(tmpDir)
		return "", "", "", noop, false, err
	}
	if err := os.WriteFile(localKey, keyPEM, 0o600); err != nil {
		_ = os.RemoveAll(tmpDir)
		return "", "", "", noop, false, err
	}
	return fp, localCert, localKey, func() { _ = os.RemoveAll(tmpDir) }, true, nil
}

func ensureDevTunnelFirewall(cfg *Config) error {
	script := fmt.Sprintf(`sudo ufw allow %d/tcp >/dev/null 2>&1 || true`, cfg.Tunnel.DevTunnel.Port)
	return SSH(cfg, "bash", "-c", script)
}

// DevTunnelEnsureAgent verifies the remote agent is deployed and reachable.
func DevTunnelEnsureAgent(cfg *Config) error {
	if err := devTunnelToken(cfg); err != nil {
		return err
	}
	loadDevTunnelTLSFingerprint(cfg)
	if devTunnelAgentReachable(cfg) {
		return devTunnelCredentials(cfg)
	}
	logfmt.Println("devtunnel", "agent not reachable — deploying")
	if err := DevTunnelDeploy(cfg); err != nil {
		return err
	}
	loadDevTunnelTLSFingerprint(cfg)
	if !devTunnelAgentReachable(cfg) {
		return fmt.Errorf("devtunnel agent still unreachable at %s", cfg.DevTunnelServerAddr())
	}
	return devTunnelCredentials(cfg)
}