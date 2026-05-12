package config

import (
	"testing"

	"gopkg.in/yaml.v3"
)

func TestConfigInt_Unmarshal_Int(t *testing.T) {
	var f ConfigInt
	err := yaml.Unmarshal([]byte("30"), &f)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if f.Int() != 30 {
		t.Fatalf("expected 30, got %d", f.Int())
	}
}

func TestConfigInt_Unmarshal_String(t *testing.T) {
	var f ConfigInt
	err := yaml.Unmarshal([]byte(`"30"`), &f)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if f.Int() != 30 {
		t.Fatalf("expected 30, got %d", f.Int())
	}
}

func TestConfigInt_Unmarshal_Zero(t *testing.T) {
	var f ConfigInt
	err := yaml.Unmarshal([]byte("0"), &f)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if f.Int() != 0 {
		t.Fatalf("expected 0, got %d", f.Int())
	}
}

func TestConfigInt_Unmarshal_EmptyString(t *testing.T) {
	var f ConfigInt
	err := yaml.Unmarshal([]byte(`""`), &f)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if f.Int() != 0 {
		t.Fatalf("expected 0 for empty string, got %d", f.Int())
	}
}

func TestConfigInt_Unmarshal_LargeNumber(t *testing.T) {
	var f ConfigInt
	err := yaml.Unmarshal([]byte("10485760"), &f)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if f.Int64() != 10485760 {
		t.Fatalf("expected 10485760, got %d", f.Int64())
	}
}

func TestConfigInt_Unmarshal_LargeString(t *testing.T) {
	var f ConfigInt
	err := yaml.Unmarshal([]byte(`"10485760"`), &f)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if f.Int64() != 10485760 {
		t.Fatalf("expected 10485760, got %d", f.Int64())
	}
}

func TestConfigInt_Unmarshal_Negative(t *testing.T) {
	var f ConfigInt
	err := yaml.Unmarshal([]byte("-1"), &f)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if f.Int() != -1 {
		t.Fatalf("expected -1, got %d", f.Int())
	}
}

func TestConfigInt_Unmarshal_StringNegative(t *testing.T) {
	var f ConfigInt
	err := yaml.Unmarshal([]byte(`"-1"`), &f)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if f.Int() != -1 {
		t.Fatalf("expected -1, got %d", f.Int())
	}
}

func TestConfigInt_Unmarshal_InvalidString(t *testing.T) {
	var f ConfigInt
	err := yaml.Unmarshal([]byte(`"not-a-number"`), &f)
	if err == nil {
		t.Fatal("expected error for invalid string")
	}
}

func TestConfigInt_Unmarshal_Bool(t *testing.T) {
	var f ConfigInt
	err := yaml.Unmarshal([]byte("true"), &f)
	if err == nil {
		t.Fatal("expected error for bool input")
	}
}
