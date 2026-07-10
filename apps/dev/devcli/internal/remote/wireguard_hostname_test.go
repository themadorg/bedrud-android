package remote

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestWGConfigToIpcSetHostname(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "wg.conf")
	// Use localhost so resolution works offline/CI without external DNS.
	content := `[Interface]
PrivateKey = mABwfS6kv3LbTNXMD18K2IpBs3aU1uy8PYWzDelRFFY=

[Peer]
PublicKey = kpdZgVkjNl4dpkqEBXR/xGIjl+ipw4RCp8yJQDCtxiI=
Endpoint = localhost:51820
AllowedIPs = 10.0.0.1/32
`
	if err := os.WriteFile(path, []byte(content), 0o600); err != nil {
		t.Fatal(err)
	}
	ipc, err := wgConfigToIpcSet(path)
	if err != nil {
		t.Fatal(err)
	}
	if strings.Contains(ipc, "localhost") {
		t.Fatalf("hostname must be resolved in IPC:\n%s", ipc)
	}
	if !strings.Contains(ipc, "endpoint=") {
		t.Fatalf("missing endpoint in IPC:\n%s", ipc)
	}
	// Expect IPv4 loopback form (127.0.0.1:51820).
	if !strings.Contains(ipc, "endpoint=127.0.0.1:51820") && !strings.Contains(ipc, "endpoint=[::1]:51820") {
		t.Fatalf("expected resolved loopback endpoint in IPC:\n%s", ipc)
	}
}
