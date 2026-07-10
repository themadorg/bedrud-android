package tunnel

import (
	"testing"
)

func TestGenerateAndPinTLS(t *testing.T) {
	certPEM, keyPEM, fp, err := GenerateServerTLS("tunnel.test", "127.0.0.1")
	if err != nil {
		t.Fatal(err)
	}
	if len(certPEM) == 0 || len(keyPEM) == 0 || fp == "" {
		t.Fatal("expected cert material")
	}
	got, err := FingerprintCertPEM(certPEM)
	if err != nil || got != fp {
		t.Fatalf("fingerprint mismatch: %s vs %s", got, fp)
	}
	if _, err := ClientTLSConfig(fp, "tunnel.test"); err != nil {
		t.Fatal(err)
	}
}