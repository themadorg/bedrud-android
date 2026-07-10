package remote

import (
	"context"
	"net"
	"net/http"
	"testing"
	"time"
)

func startTestHTTP(t *testing.T, handler http.HandlerFunc) (port int, close func()) {
	t.Helper()
	ln, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	srv := &http.Server{Handler: handler}
	go func() { _ = srv.Serve(ln) }()
	return ln.Addr().(*net.TCPAddr).Port, func() {
		_ = srv.Close()
	}
}

func TestWaitLocalBackendsReady(t *testing.T) {
	apiPort, closeAPI := startTestHTTP(t, func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusOK)
	})
	defer closeAPI()
	webPort, closeWeb := startTestHTTP(t, func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusOK)
	})
	defer closeWeb()

	cfg := &Config{}
	cfg.Local.WebPort = webPort
	cfg.Local.APIPort = apiPort

	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
	defer cancel()

	if err := WaitLocalBackends(ctx, cfg, time.Second); err != nil {
		t.Fatalf("WaitLocalBackends: %v", err)
	}
}

func TestWaitLocalBackendsTimeout(t *testing.T) {
	webPort, closeWeb := startTestHTTP(t, func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusOK)
	})
	defer closeWeb()

	cfg := &Config{}
	cfg.Local.WebPort = webPort
	cfg.Local.APIPort = 1 // nothing listens here

	ctx := context.Background()
	err := WaitLocalBackends(ctx, cfg, 300*time.Millisecond)
	if err == nil {
		t.Fatal("expected timeout error")
	}
}