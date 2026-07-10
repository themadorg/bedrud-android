package main

import (
	"context"
	"flag"
	"fmt"
	"os"
	"os/signal"
	"strconv"
	"strings"
	"syscall"

	"bedrud/devcli/internal/logfmt"
	"bedrud/devcli/internal/tunnel"
)

func cmdTunnel(args []string) int {
	if len(args) == 0 {
		fmt.Fprintln(os.Stderr, "usage: devcli tunnel-server --listen :7079 --token-file /path")
		return 2
	}
	switch args[0] {
	case "tunnel-server":
		return cmdTunnelServer(args[1:])
	default:
		fmt.Fprintf(os.Stderr, "unknown tunnel command %q\n", args[0])
		return 2
	}
}

func cmdTunnelServer(args []string) int {
	fs := flag.NewFlagSet("tunnel-server", flag.ExitOnError)
	listen := fs.String("listen", ":7079", "listen address")
	token := fs.String("token", "", "shared auth token")
	tokenFile := fs.String("token-file", "", "read token from file")
	tlsCert := fs.String("tls-cert", "", "TLS certificate file (required)")
	tlsKey := fs.String("tls-key", "", "TLS private key file (required)")
	webPort := fs.Int("web-port", 7070, "remote listen port for web reverse proxy")
	apiPort := fs.Int("api-port", 7071, "remote listen port for api reverse proxy")
	livekitPort := fs.Int("livekit-port", 7072, "local LiveKit port to forward to")
	_ = fs.Parse(args)

	authToken := strings.TrimSpace(*token)
	if authToken == "" && *tokenFile != "" {
		data, err := os.ReadFile(*tokenFile)
		if err != nil {
			fmt.Fprintf(os.Stderr, "devcli: read token file: %v\n", err)
			return 1
		}
		authToken = strings.TrimSpace(string(data))
	}
	if authToken == "" {
		fmt.Fprintln(os.Stderr, "devcli: --token or --token-file is required")
		return 2
	}
	if strings.TrimSpace(*tlsCert) == "" || strings.TrimSpace(*tlsKey) == "" {
		fmt.Fprintln(os.Stderr, "devcli: --tls-cert and --tls-key are required")
		return 2
	}

	ctx, cancel := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer cancel()

	srv := &tunnel.Server{}
	srv.SetConfig(tunnel.ServerConfig{
		ListenAddr:    normalizeListen(*listen),
		Token:         authToken,
		TLSCertFile:   strings.TrimSpace(*tlsCert),
		TLSKeyFile:    strings.TrimSpace(*tlsKey),
		RemoteWebPort: *webPort,
		RemoteAPIPort: *apiPort,
		LiveKitPort:   *livekitPort,
		OnClientConnect: func() {
			logfmt.Println("devtunnel", "client connected")
		},
		OnClientLost: func() {
			logfmt.Println("devtunnel", "client disconnected")
		},
	})
	logfmt.Println("devtunnel", fmt.Sprintf("agent listening on %s", normalizeListen(*listen)))
	if err := srv.ListenAndServe(ctx); err != nil && ctx.Err() == nil {
		fmt.Fprintf(os.Stderr, "devcli: %v\n", err)
		return 1
	}
	return 0
}

func normalizeListen(addr string) string {
	addr = strings.TrimSpace(addr)
	if strings.HasPrefix(addr, ":") {
		port, err := strconv.Atoi(strings.TrimPrefix(addr, ":"))
		if err == nil {
			return fmt.Sprintf("0.0.0.0:%d", port)
		}
	}
	if !strings.Contains(addr, ":") {
		return "0.0.0.0:" + addr
	}
	return addr
}