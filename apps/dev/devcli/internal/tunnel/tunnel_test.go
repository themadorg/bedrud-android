package tunnel

import (
	"context"
	"fmt"
	"io"
	"net"
	"net/http"
	"os"
	"path/filepath"
	"sync"
	"testing"
	"time"
)

func TestDevTunnelRoundTrip(t *testing.T) {
	token := "test-token"
	certPEM, keyPEM, fp, err := GenerateServerTLS("127.0.0.1")
	if err != nil {
		t.Fatal(err)
	}

	tmpDir, err := os.MkdirTemp("", "bedrud-tunnel-test-*")
	if err != nil {
		t.Fatal(err)
	}
	defer os.RemoveAll(tmpDir)
	certFile := filepath.Join(tmpDir, "tunnel.crt")
	keyFile := filepath.Join(tmpDir, "tunnel.key")
	if err := os.WriteFile(certFile, certPEM, 0o644); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(keyFile, keyPEM, 0o600); err != nil {
		t.Fatal(err)
	}

	webLn, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	defer webLn.Close()
	localWebPort := webLn.Addr().(*net.TCPAddr).Port
	go func() {
		for {
			conn, err := webLn.Accept()
			if err != nil {
				return
			}
			go func(c net.Conn) {
				defer c.Close()
				_, _ = io.WriteString(c, "HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nok")
			}(conn)
		}
	}()

	webProxyLn, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	webProxyPort := webProxyLn.Addr().(*net.TCPAddr).Port
	_ = webProxyLn.Close()

	apiProxyLn, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	apiProxyPort := apiProxyLn.Addr().(*net.TCPAddr).Port
	_ = apiProxyLn.Close()

	lkLn, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	liveKitPort := lkLn.Addr().(*net.TCPAddr).Port
	_ = lkLn.Close()

	agentLn, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	agentAddr := agentLn.Addr().String()
	_ = agentLn.Close()

	connected := make(chan struct{})
	var connectedOnce sync.Once
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	srv := &Server{}
	srv.SetConfig(ServerConfig{
		ListenAddr:    agentAddr,
		Token:         token,
		TLSCertFile:   certFile,
		TLSKeyFile:    keyFile,
		RemoteWebPort: webProxyPort,
		RemoteAPIPort: apiProxyPort,
		LiveKitPort:   liveKitPort,
		OnClientConnect: func() {
			connectedOnce.Do(func() { close(connected) })
		},
	})
	go func() { _ = srv.ListenAndServe(ctx) }()

	clientCtx, clientCancel := context.WithCancel(context.Background())
	defer clientCancel()
	go func() {
		_ = RunClient(clientCtx, ClientConfig{
			ServerAddr:       agentAddr,
			Token:            token,
			TLSFingerprint:   fp,
			TLSServerName:    "127.0.0.1",
			LocalWebPort:     localWebPort,
			LocalAPIPort:     localWebPort,
			LocalLiveKitPort: liveKitPort,
		})
	}()

	select {
	case <-connected:
	case <-time.After(5 * time.Second):
		t.Fatal("client never connected to agent")
	}

	deadline := time.Now().Add(5 * time.Second)
	var resp *http.Response
	target := fmt.Sprintf("http://127.0.0.1:%d/", webProxyPort)
	for time.Now().Before(deadline) {
		resp, err = http.Get(target)
		if err == nil && resp.StatusCode == 200 {
			break
		}
		if resp != nil {
			_ = resp.Body.Close()
		}
		time.Sleep(100 * time.Millisecond)
	}
	if resp == nil || resp.StatusCode != 200 {
		t.Fatalf("reverse proxy failed: %v", err)
	}
	_ = resp.Body.Close()
}