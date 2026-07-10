package clioutput

import (
	"bytes"
	"encoding/json"
	"testing"
)

func TestSuccessJSON(t *testing.T) {
	var out bytes.Buffer
	SetWriters(&out, &bytes.Buffer{})
	defer ResetWriters()
	SetJSON(true)
	defer SetJSON(false)

	if err := Success("done", map[string]string{"id": "1"}); err != nil {
		t.Fatal(err)
	}

	var r Result
	if err := json.Unmarshal(out.Bytes(), &r); err != nil {
		t.Fatalf("unmarshal: %v\nraw: %s", err, out.String())
	}
	if !r.OK || r.Message != "done" {
		t.Fatalf("unexpected result: %+v", r)
	}
	data, ok := r.Data.(map[string]any)
	if !ok || data["id"] != "1" {
		t.Fatalf("unexpected data: %+v", r.Data)
	}
}

func TestSuccessTextMode(t *testing.T) {
	var out bytes.Buffer
	SetWriters(&out, &bytes.Buffer{})
	defer ResetWriters()
	SetJSON(false)

	if err := Success("hello world", nil); err != nil {
		t.Fatal(err)
	}
	if out.String() != "hello world\n" {
		t.Fatalf("got %q", out.String())
	}
}

func TestEmitError(t *testing.T) {
	var errBuf bytes.Buffer
	SetWriters(&bytes.Buffer{}, &errBuf)
	defer ResetWriters()
	SetJSON(true)
	defer SetJSON(false)

	EmitError(bytes.ErrTooLarge)

	var r Result
	if err := json.Unmarshal(errBuf.Bytes(), &r); err != nil {
		t.Fatal(err)
	}
	if r.OK || r.Message == "" {
		t.Fatalf("expected failed result, got %+v", r)
	}
}

func TestPrintfSuppressedInJSONMode(t *testing.T) {
	var out bytes.Buffer
	SetWriters(&out, &bytes.Buffer{})
	defer ResetWriters()
	SetJSON(true)
	defer SetJSON(false)

	Printf("should not appear\n")
	if out.Len() != 0 {
		t.Fatalf("expected no output, got %q", out.String())
	}
}
