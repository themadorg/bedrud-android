package config

import "testing"

func TestServerConfig_TLSMode_ManualWinsOverACME(t *testing.T) {
	s := ServerConfig{
		EnableTLS: true,
		UseACME:   true,
		Domain:    "example.com",
		CertFile:  "/etc/ssl/fullchain.pem",
		KeyFile:   "/etc/ssl/privkey.pem",
	}
	if got := s.TLSMode(); got != TLSModeManual {
		t.Fatalf("TLSMode()=%q, want manual (explicit cert files must win over useACME)", got)
	}
}

func TestServerConfig_TLSMode_ACMEWhenNoCertFiles(t *testing.T) {
	s := ServerConfig{
		EnableTLS: true,
		UseACME:   true,
		Domain:    "example.com",
	}
	if got := s.TLSMode(); got != TLSModeACME {
		t.Fatalf("TLSMode()=%q, want acme", got)
	}
}

func TestServerConfig_TLSMode_NoneWhenDisabled(t *testing.T) {
	s := ServerConfig{EnableTLS: false, UseACME: true, Domain: "x.com"}
	if got := s.TLSMode(); got != TLSModeNone {
		t.Fatalf("TLSMode()=%q, want none", got)
	}
	s = ServerConfig{EnableTLS: true, DisableTLS: true, CertFile: "a", KeyFile: "b"}
	if got := s.TLSMode(); got != TLSModeNone {
		t.Fatalf("DisableTLS: TLSMode()=%q, want none", got)
	}
}

func TestServerConfig_TLSMode_ManualDefaults(t *testing.T) {
	// enableTLS without ACME and without paths → manual (defaults under /etc/bedrud)
	s := ServerConfig{EnableTLS: true}
	if got := s.TLSMode(); got != TLSModeManual {
		t.Fatalf("TLSMode()=%q, want manual", got)
	}
	cert, key := s.ResolveCertPaths()
	if cert != "/etc/bedrud/cert.pem" || key != "/etc/bedrud/key.pem" {
		t.Fatalf("defaults: %q %q", cert, key)
	}
}

func TestServerConfig_HasExplicitCertFiles(t *testing.T) {
	s := ServerConfig{CertFile: "c.pem"}
	if s.HasExplicitCertFiles() {
		t.Fatal("key missing")
	}
	s.KeyFile = "k.pem"
	if !s.HasExplicitCertFiles() {
		t.Fatal("both set")
	}
}
