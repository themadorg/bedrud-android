package remote

import (
	"strings"
	"testing"
)

func TestWireguardServerConfUsesServerIPNotNetworkAddress(t *testing.T) {
	cfg := &Config{}
	cfg.applyDefaults()
	cfg.WireGuard.RemoteTunnelIP = "10.0.0.1"
	cfg.WireGuard.LocalTunnelIP = "10.0.0.2"
	cfg.Provision.WireGuardPort = 51820

	conf := wireguardServerConf(cfg, "server-priv", "client-pub", "eth0")
	if strings.Contains(conf, "Address = 10.0.0.0/24") {
		t.Fatalf("server conf must not use network address 10.0.0.0/24:\n%s", conf)
	}
	if !strings.Contains(conf, "Address = 10.0.0.1/24") {
		t.Fatalf("expected server Address = 10.0.0.1/24, got:\n%s", conf)
	}
	if !strings.Contains(conf, "AllowedIPs = 10.0.0.2/32") {
		t.Fatalf("expected client AllowedIPs = 10.0.0.2/32, got:\n%s", conf)
	}
}

func TestLivekitYAMLExcludesWireGuardInterface(t *testing.T) {
	cfg := &Config{}
	cfg.applyDefaults()
	cfg.URLs.PublicHost = "debug.example.com"
	cfg.Provision.StateDir = "/etc/bedrud-debug"
	cfg.Provision.EnableACME = ptrBool(true)

	yaml := livekitYAML(cfg, "203.0.113.10", "secret")
	for _, want := range []string{
		"interfaces:",
		"excludes:",
		"- wg0",
		"ips:",
		"includes:",
		"203.0.113.10/32",
		"tls_port: 5349",
		"relay_range_start: 30000",
		"relay_range_end: 40000",
		"/etc/bedrud-debug/turn.crt",
	} {
		if !strings.Contains(yaml, want) {
			t.Fatalf("missing %q in livekit yaml:\n%s", want, yaml)
		}
	}
}

func ptrBool(v bool) *bool {
	return &v
}