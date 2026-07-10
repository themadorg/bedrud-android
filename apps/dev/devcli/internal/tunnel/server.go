package tunnel

import (
	"context"
	"fmt"
	"io"
	"net"
	"sync"

	"github.com/hashicorp/yamux"
)

// ServerConfig configures the remote devtunnel agent.
type ServerConfig struct {
	ListenAddr      string
	Token           string
	TLSCertFile     string
	TLSKeyFile      string
	RemoteWebPort   int
	RemoteAPIPort   int
	LiveKitPort     int
	OnClientConnect func()
	OnClientLost    func()
}

// Server accepts authenticated devtunnel clients and proxies traffic.
type Server struct {
	cfg           ServerConfig
	ln            net.Listener
	mu            sync.Mutex
	cancel        context.CancelFunc
	session       *yamux.Session
	sessionCancel context.CancelFunc
}

// SetConfig replaces the server configuration before ListenAndServe.
func (s *Server) SetConfig(cfg ServerConfig) {
	s.cfg = cfg
}

// ListenAndServe runs until ctx is cancelled.
func (s *Server) ListenAndServe(ctx context.Context) error {
	if s.cfg.TLSCertFile == "" || s.cfg.TLSKeyFile == "" {
		return fmt.Errorf("TLS cert and key are required")
	}
	ln, err := ListenTLS(s.cfg.ListenAddr, s.cfg.TLSCertFile, s.cfg.TLSKeyFile)
	if err != nil {
		return fmt.Errorf("listen %s: %w", s.cfg.ListenAddr, err)
	}
	s.ln = ln

	ctx, cancel := context.WithCancel(ctx)
	s.cancel = cancel
	defer cancel()
	defer ln.Close()

	go func() {
		<-ctx.Done()
		_ = ln.Close()
	}()

	proxyCtx, proxyCancel := context.WithCancel(ctx)
	defer proxyCancel()
	go s.serveReverse(proxyCtx, s.cfg.RemoteWebPort, streamWeb)
	go s.serveReverse(proxyCtx, s.cfg.RemoteAPIPort, streamAPI)

	for {
		conn, err := ln.Accept()
		if err != nil {
			select {
			case <-ctx.Done():
				return nil
			default:
			}
			if ne, ok := err.(net.Error); ok && ne.Temporary() {
				continue
			}
			return err
		}
		go s.handleConn(ctx, conn)
	}
}

func (s *Server) handleConn(ctx context.Context, conn net.Conn) {
	defer conn.Close()
	if err := HandshakeServer(conn, s.cfg.Token); err != nil {
		return
	}
	session, err := yamux.Server(conn, yamuxConfig())
	if err != nil {
		return
	}

	s.replaceSession(session)
	if s.cfg.OnClientConnect != nil {
		s.cfg.OnClientConnect()
	}

	child, childCancel := context.WithCancel(ctx)
	defer func() {
		childCancel()
		s.removeSession(session)
		session.Close()
		if s.cfg.OnClientLost != nil {
			s.cfg.OnClientLost()
		}
	}()

	_ = s.serveForward(child, session)
}

func (s *Server) replaceSession(session *yamux.Session) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.sessionCancel != nil {
		s.sessionCancel()
	}
	if s.session != nil {
		_ = s.session.Close()
	}
	_, cancel := context.WithCancel(context.Background())
	s.session = session
	s.sessionCancel = cancel
}

func (s *Server) removeSession(session *yamux.Session) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.session != session {
		return
	}
	if s.sessionCancel != nil {
		s.sessionCancel()
		s.sessionCancel = nil
	}
	s.session = nil
}

func (s *Server) activeSession() *yamux.Session {
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.session
}

func (s *Server) serveReverse(ctx context.Context, port int, kind byte) error {
	ln, err := net.Listen("tcp", fmt.Sprintf("127.0.0.1:%d", port))
	if err != nil {
		return fmt.Errorf("listen 127.0.0.1:%d: %w", port, err)
	}
	defer ln.Close()

	go func() {
		<-ctx.Done()
		_ = ln.Close()
	}()

	for {
		incoming, err := ln.Accept()
		if err != nil {
			select {
			case <-ctx.Done():
				return nil
			default:
			}
			return err
		}
		go func(local net.Conn) {
			defer local.Close()
			session := s.activeSession()
			if session == nil {
				return
			}
			stream, err := session.OpenStream()
			if err != nil {
				return
			}
			defer stream.Close()
			if _, err := stream.Write([]byte{kind}); err != nil {
				return
			}
			pipe(local, stream)
		}(incoming)
	}
}

func (s *Server) serveForward(ctx context.Context, session *yamux.Session) error {
	for {
		select {
		case <-ctx.Done():
			return nil
		default:
		}
		stream, err := session.AcceptStream()
		if err != nil {
			return err
		}
		go func(st net.Conn) {
			defer st.Close()
			buf := make([]byte, 1)
			if _, err := io.ReadFull(st, buf); err != nil || buf[0] != streamLiveKit {
				return
			}
			target, err := net.Dial("tcp", fmt.Sprintf("127.0.0.1:%d", s.cfg.LiveKitPort))
			if err != nil {
				return
			}
			defer target.Close()
			pipe(st, target)
		}(stream)
	}
}