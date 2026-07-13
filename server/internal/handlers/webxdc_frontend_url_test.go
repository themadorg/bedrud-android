package handlers

import (
	"testing"

	"bedrud/config"
)

func TestHFrontendURL_ProductionDomain(t *testing.T) {
	cfg := &config.Config{}
	cfg.Server.Domain = "bedrud.xyz"
	cfg.Server.EnableTLS = true
	got := hFrontendURL(cfg)
	if got != "https://bedrud.xyz" {
		t.Fatalf("got %q want https://bedrud.xyz", got)
	}
}

func TestHFrontendURL_ExplicitFrontendWins(t *testing.T) {
	cfg := &config.Config{}
	cfg.Auth.FrontendURL = "https://app.example.com/"
	cfg.Server.Domain = "bedrud.xyz"
	got := hFrontendURL(cfg)
	if got != "https://app.example.com" {
		t.Fatalf("got %q", got)
	}
}

func TestHFrontendURL_DevDefault(t *testing.T) {
	cfg := &config.Config{}
	got := hFrontendURL(cfg)
	if got != "http://localhost:7070" {
		t.Fatalf("got %q", got)
	}
}
