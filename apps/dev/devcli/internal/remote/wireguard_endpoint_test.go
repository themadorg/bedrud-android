package remote

import "testing"

func TestResolveWireGuardEndpointIP(t *testing.T) {
	got, err := resolveWireGuardEndpoint("203.0.113.10:51820")
	if err != nil {
		t.Fatal(err)
	}
	if got != "203.0.113.10:51820" {
		t.Fatalf("got %q", got)
	}
}