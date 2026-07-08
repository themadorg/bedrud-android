package remote

import "testing"

func TestHealthReportAllOK(t *testing.T) {
	ok := HealthReport{Results: []HealthResult{
		{Name: "a", OK: true},
		{Name: "b", OK: true},
	}}
	if !ok.AllOK() {
		t.Fatal("expected all ok")
	}
	bad := HealthReport{Results: []HealthResult{
		{Name: "a", OK: true},
		{Name: "b", OK: false},
	}}
	if bad.AllOK() {
		t.Fatal("expected failure")
	}
	if len(bad.Failed()) != 1 {
		t.Fatalf("Failed() = %d, want 1", len(bad.Failed()))
	}
}

func TestTurnDetail(t *testing.T) {
	if turnDetail("") != ":5349 not listening" {
		t.Fatalf("empty: %q", turnDetail(""))
	}
	if turnDetail("0.0.0.0:5349") != "listening on :5349" {
		t.Fatalf("listen: %q", turnDetail("0.0.0.0:5349"))
	}
}