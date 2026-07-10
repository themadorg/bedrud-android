package tunnel

import (
	"context"
	"crypto/tls"
	"fmt"
	"io"
	"net"
	"strings"
	"sync"
	"time"

	"github.com/hashicorp/yamux"
)

// ClientConfig configures the local devtunnel client.
type ClientConfig struct {
	ServerAddr       string
	Token            string
	TLSFingerprint   string
	TLSServerName    string
	LocalWebPort     int
	LocalAPIPort     int
	LocalLiveKitPort int
	OnConnected      func()
	OnDisconnected   func()
}

// RunClient maintains an outbound tunnel to the remote agent until ctx is cancelled.
func RunClient(ctx context.Context, cfg ClientConfig) error {
	lkLn, err := net.Listen("tcp", fmt.Sprintf("127.0.0.1:%d", cfg.LocalLiveKitPort))
	if err != nil {
		return fmt.Errorf("listen 127.0.0.1:%d: %w", cfg.LocalLiveKitPort, err)
	}
	defer lkLn.Close()

	var (
		sessionMu sync.RWMutex
		session   *yamux.Session
	)
	go forwardLiveKitPersistent(ctx, lkLn, func() *yamux.Session {
		sessionMu.RLock()
		defer sessionMu.RUnlock()
		return session
	})

	backoff := 200 * time.Millisecond
	for {
		err := runClientOnce(ctx, cfg, func(sess *yamux.Session) {
			sessionMu.Lock()
			session = sess
			sessionMu.Unlock()
		}, func(sess *yamux.Session) {
			sessionMu.Lock()
			if session == sess {
				session = nil
			}
			sessionMu.Unlock()
		})
		if err == nil {
			return nil
		}
		{
			select {
			case <-ctx.Done():
				return ctx.Err()
			default:
			}
			if cfg.OnDisconnected != nil {
				cfg.OnDisconnected()
			}
			select {
			case <-ctx.Done():
				return ctx.Err()
			case <-time.After(backoff):
			}
			if backoff < 5*time.Second {
				backoff *= 2
			}
			continue
		}
	}
}

func runClientOnce(ctx context.Context, cfg ClientConfig, onSession, onSessionEnd func(*yamux.Session)) error {
	if strings.TrimSpace(cfg.TLSFingerprint) == "" {
		return fmt.Errorf("TLS fingerprint is required")
	}
	dialer := net.Dialer{Timeout: 10 * time.Second}
	raw, err := dialer.DialContext(ctx, "tcp", cfg.ServerAddr)
	if err != nil {
		return fmt.Errorf("dial %s: %w", cfg.ServerAddr, err)
	}
	defer raw.Close()
	tlsCfg, err := ClientTLSConfig(cfg.TLSFingerprint, cfg.TLSServerName)
	if err != nil {
		return err
	}
	conn := tls.Client(raw, tlsCfg)
	if err := conn.HandshakeContext(ctx); err != nil {
		return fmt.Errorf("tls handshake %s: %w", cfg.ServerAddr, err)
	}
	defer conn.Close()

	if err := HandshakeClient(conn, cfg.Token); err != nil {
		return err
	}
	sess, err := yamux.Client(conn, yamuxConfig())
	if err != nil {
		return err
	}
	onSession(sess)
	defer func() {
		_ = sess.Close()
		onSessionEnd(sess)
	}()

	if cfg.OnConnected != nil {
		cfg.OnConnected()
	}

	ports := map[byte]int{
		streamWeb: cfg.LocalWebPort,
		streamAPI: cfg.LocalAPIPort,
	}
	return acceptReverse(ctx, sess, ports)
}

func acceptReverse(ctx context.Context, session *yamux.Session, ports map[byte]int) error {
	for {
		select {
		case <-ctx.Done():
			return ctx.Err()
		default:
		}
		stream, err := session.AcceptStream()
		if err != nil {
			return err
		}
		go func(st net.Conn) {
			defer st.Close()
			buf := make([]byte, 1)
			if _, err := io.ReadFull(st, buf); err != nil {
				return
			}
			localPort, ok := ports[buf[0]]
			if !ok {
				return
			}
			local, err := net.Dial("tcp", fmt.Sprintf("127.0.0.1:%d", localPort))
			if err != nil {
				return
			}
			defer local.Close()
			pipe(st, local)
		}(stream)
	}
}

func forwardLiveKitPersistent(ctx context.Context, ln net.Listener, activeSession func() *yamux.Session) {
	go func() {
		<-ctx.Done()
		_ = ln.Close()
	}()

	for {
		local, err := ln.Accept()
		if err != nil {
			select {
			case <-ctx.Done():
				return
			default:
			}
			return
		}
		go func(conn net.Conn) {
			defer conn.Close()
			session := activeSession()
			if session == nil {
				return
			}
			stream, err := session.OpenStream()
			if err != nil {
				return
			}
			defer stream.Close()
			if _, err := stream.Write([]byte{streamLiveKit}); err != nil {
				return
			}
			pipe(conn, stream)
		}(local)
	}
}