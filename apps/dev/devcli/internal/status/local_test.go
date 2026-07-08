package status

import (
	"net"
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestCheckLocalLivekitOK(t *testing.T) {
	ln, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	defer ln.Close()
	port := ln.Addr().(*net.TCPAddr).Port
	go func() {
		_ = http.Serve(ln, http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
			_, _ = w.Write([]byte("OK"))
		}))
	}()

	// Override ports by probing directly
	res := probeLocalHTTP("livekit", port, "http://127.0.0.1:%d/", "OK", "hint")
	if !res.OK {
		t.Fatalf("expected ok, got %+v", res)
	}
}

func TestCheckLocalAPIUsesHealthPath(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		_, _ = w.Write([]byte(`{"status":"ok"}`))
	}))
	defer srv.Close()

	port := srv.Listener.Addr().(*net.TCPAddr).Port
	res := probeLocalHTTP("api", port, "http://127.0.0.1:%d/", "200", "hint")
	if !res.OK {
		t.Fatalf("expected ok, got %+v", res)
	}
}