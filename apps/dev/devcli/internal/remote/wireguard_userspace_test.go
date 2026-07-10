package remote

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestWGConfigToIpcSet(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "wg.conf")
	content := `# Split-tunnel
[Interface]
Address = 10.0.0.2/32
PrivateKey = mABwfS6kv3LbTNXMD18K2IpBs3aU1uy8PYWzDelRFFY=

[Peer]
PublicKey = kpdZgVkjNl4dpkqEBXR/xGIjl+ipw4RCp8yJQDCtxiI=
Endpoint = 1.2.3.4:51820
AllowedIPs = 10.0.0.1/32
PersistentKeepalive = 25
`
	if err := os.WriteFile(path, []byte(content), 0o600); err != nil {
		t.Fatal(err)
	}
	ipc, err := wgConfigToIpcSet(path)
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(ipc, "allowed_ip=10.0.0.1/32") {
		t.Fatalf("expected split-tunnel allowed_ip:\n%s", ipc)
	}
}

func TestWireguardClientConfSplitTunnel(t *testing.T) {
	cfg := &Config{}
	cfg.SSH.Host = "1.2.3.4"
	cfg.applyDefaults()
	conf, err := wireguardClientConf(cfg, "priv", "pub")
	if err != nil {
		t.Fatal(err)
	}
	if strings.Contains(conf, "DNS =") {
		t.Fatal("client conf must not set DNS")
	}
	if strings.Count(conf, "AllowedIPs") != 1 || !strings.Contains(conf, "AllowedIPs = 10.0.0.1/32") {
		t.Fatalf("expected single remote AllowedIPs:\n%s", conf)
	}
}