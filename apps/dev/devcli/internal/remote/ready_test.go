package remote

import "testing"

func TestCriticalLiveKitProbe(t *testing.T) {
	cfg := &Config{}
	cfg.Provision.EnableACME = boolPtr(true)

	for _, name := range []string{
		"livekit-service", "livekit-http", "livekit-turn-relay-fw", "livekit-public",
	} {
		if !criticalLiveKitProbe(name, cfg) {
			t.Fatalf("%s should be critical", name)
		}
	}
	if !criticalLiveKitProbe("livekit-turn-tls", cfg) {
		t.Fatal("livekit-turn-tls should be critical when ACME enabled")
	}

	cfg.Provision.EnableACME = boolPtr(false)
	if criticalLiveKitProbe("livekit-turn-tls", cfg) {
		t.Fatal("livekit-turn-tls should be optional without ACME")
	}
}

func TestProbeLocalBackendsDown(t *testing.T) {
	cfg := &Config{}
	cfg.Local.WebPort = 1
	cfg.Local.APIPort = 2
	for _, res := range probeLocalBackends(cfg) {
		if res.OK {
			t.Fatalf("expected %s down, got %+v", res.Name, res)
		}
	}
}

func boolPtr(v bool) *bool { return &v }