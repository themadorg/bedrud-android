package remote

import (
	"bufio"
	"context"
	"encoding/base64"
	"encoding/hex"
	"fmt"
	"io"
	"net"
	"net/netip"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"sync"
	"syscall"

	"golang.zx2c4.com/wireguard/conn"
	"golang.zx2c4.com/wireguard/device"
	"golang.zx2c4.com/wireguard/tun"
	"golang.zx2c4.com/wireguard/tun/netstack"
	"bedrud/devcli/internal/logfmt"
)

const defaultWireGuardPIDFile = "~/.config/bedrud/wireguard.pid"

var (
	wgDaemonMu sync.Mutex
	wgDaemon   *embeddedWGDevice
)

type embeddedWGDevice struct {
	dev     *device.Device
	tun     tun.Device
	tnet    *netstack.Net
	stopFns []func()
	iface   string
}

// wireguardUserspaceUp runs WireGuard entirely in Go (gVisor netstack) — no TUN, no CAP_NET_ADMIN.
// TCP proxies bridge netstack tunnel IPs to local api/web and expose LiveKit API on localhost.
func wireguardUserspaceUp(cfg *Config) error {
	iface := cfg.WireGuard.Interface
	if iface == "" {
		iface = interfaceFromConfig(cfg.WireGuard.ConfigFile)
	}

	wgDaemonMu.Lock()
	defer wgDaemonMu.Unlock()

	if wgDaemon != nil && wgDaemon.iface == iface {
		logfmt.Println("wireguard", fmt.Sprintf("%s already up (netstack)", iface))
		return nil
	}
	if wgDaemon != nil {
		teardownEmbeddedWG(wgDaemon)
		wgDaemon = nil
	}

	ipc, err := wgConfigToIpcSet(cfg.WireGuard.ConfigFile)
	if err != nil {
		return err
	}

	localIP, err := netip.ParseAddr(strings.TrimSpace(cfg.WireGuard.LocalTunnelIP))
	if err != nil {
		return fmt.Errorf("wireguard.localTunnelIP: %w", err)
	}
	remoteIP, err := netip.ParseAddr(strings.TrimSpace(cfg.WireGuard.RemoteTunnelIP))
	if err != nil {
		return fmt.Errorf("wireguard.remoteTunnelIP: %w", err)
	}

	tunDev, tnet, err := netstack.CreateNetTUN([]netip.Addr{localIP}, nil, 1420)
	if err != nil {
		return fmt.Errorf("netstack: %w", err)
	}

	logger := device.NewLogger(device.LogLevelError, "wireguard: ")
	dev := device.NewDevice(tunDev, conn.NewDefaultBind(), logger)
	if err := dev.IpcSet(ipc); err != nil {
		dev.Close()
		return fmt.Errorf("wireguard config: %w", err)
	}
	if err := dev.Up(); err != nil {
		dev.Close()
		return fmt.Errorf("wireguard up: %w", err)
	}

	var stopFns []func()
	stops := func(fn func()) {
		stopFns = append(stopFns, fn)
	}

	// Server Traefik → 10.0.0.2:7070/7071 (netstack) → localhost api/web
	for _, spec := range []struct {
		name   string
		port   int
		target string
	}{
		{"web", cfg.Local.WebPort, fmt.Sprintf("127.0.0.1:%d", cfg.Local.WebPort)},
		{"api", cfg.Local.APIPort, fmt.Sprintf("127.0.0.1:%d", cfg.Local.APIPort)},
	} {
		listen := netip.AddrPortFrom(localIP, uint16(spec.port))
		stop, err := proxyNetstackToHost(tnet, listen, spec.target)
		if err != nil {
			for _, s := range stopFns {
				s()
			}
			dev.Close()
			return fmt.Errorf("proxy %s: %w", spec.name, err)
		}
		stops(stop)
		logfmt.Println("wireguard", fmt.Sprintf("proxy %s: %s → %s", spec.name, listen, spec.target))
	}

	// Local API → 127.0.0.1:17072 → 10.0.0.1:7072 (netstack) → server LiveKit
	lkPort := cfg.Tunnel.SSH.LocalLiveKitPort
	if lkPort == 0 {
		lkPort = 17072
	}
	lkListen := fmt.Sprintf("127.0.0.1:%d", lkPort)
	lkTarget := netip.AddrPortFrom(remoteIP, uint16(cfg.Traefik.LiveKitPort)).String()
	stop, err := proxyHostToNetstack(tnet, lkListen, lkTarget)
	if err != nil {
		for _, s := range stopFns {
			s()
		}
		dev.Close()
		return fmt.Errorf("proxy livekit: %w", err)
	}
	stops(stop)
	logfmt.Println("wireguard", fmt.Sprintf("proxy livekit: %s → %s", lkListen, lkTarget))

	wgDaemon = &embeddedWGDevice{
		dev:     dev,
		tun:     tunDev,
		tnet:    tnet,
		stopFns: stopFns,
		iface:   iface,
	}
	_ = writeWireGuardPID(cfg, os.Getpid())

	logfmt.Println("wireguard", fmt.Sprintf(
		"netstack up (routes %s/32 only; no kernel TUN, no CAP_NET_ADMIN)",
		cfg.WireGuard.RemoteTunnelIP,
	))
	return nil
}

func wireguardUserspaceDown(cfg *Config) (bool, error) {
	wgDaemonMu.Lock()
	defer wgDaemonMu.Unlock()

	changed := false
	if wgDaemon != nil {
		teardownEmbeddedWG(wgDaemon)
		wgDaemon = nil
		changed = true
	}
	_ = os.Remove(wireguardPIDPath(cfg))
	iface := cfg.WireGuard.Interface
	if iface == "" {
		iface = interfaceFromConfig(cfg.WireGuard.ConfigFile)
	}
	if changed {
		logfmt.Println("wireguard", iface+" down")
	}
	return changed, nil
}

func teardownEmbeddedWG(d *embeddedWGDevice) {
	if d == nil {
		return
	}
	for i := len(d.stopFns) - 1; i >= 0; i-- {
		d.stopFns[i]()
	}
	if d.dev != nil {
		d.dev.Close()
	}
	if d.tun != nil {
		_ = d.tun.Close()
	}
}

func embeddedWGUp() bool {
	wgDaemonMu.Lock()
	defer wgDaemonMu.Unlock()
	return wgDaemon != nil
}

func proxyNetstackToHost(tnet *netstack.Net, listen netip.AddrPort, target string) (func(), error) {
	ln, err := tnet.ListenTCPAddrPort(listen)
	if err != nil {
		return nil, err
	}
	ctx, cancel := context.WithCancel(context.Background())
	go func() {
		for {
			conn, err := ln.Accept()
			if err != nil {
				select {
				case <-ctx.Done():
					return
				default:
					continue
				}
			}
			go proxyPair(conn, target)
		}
	}()
	return func() {
		cancel()
		_ = ln.Close()
	}, nil
}

func proxyHostToNetstack(tnet *netstack.Net, listenAddr, targetAddr string) (func(), error) {
	ln, err := net.Listen("tcp", listenAddr)
	if err != nil {
		return nil, err
	}
	ctx, cancel := context.WithCancel(context.Background())
	go func() {
		for {
			conn, err := ln.Accept()
			if err != nil {
				select {
				case <-ctx.Done():
					return
				default:
					continue
				}
			}
			go func(hostConn net.Conn) {
				defer hostConn.Close()
				remote, err := tnet.DialContext(ctx, "tcp", targetAddr)
				if err != nil {
					return
				}
				defer remote.Close()
				pipe(hostConn, remote)
			}(conn)
		}
	}()
	return func() {
		cancel()
		_ = ln.Close()
	}, nil
}

func proxyPair(from net.Conn, target string) {
	defer from.Close()
	to, err := net.Dial("tcp", target)
	if err != nil {
		return
	}
	defer to.Close()
	pipe(from, to)
}

func pipe(a, b net.Conn) {
	done := make(chan struct{}, 2)
	go func() { _, _ = io.Copy(b, a); done <- struct{}{} }()
	go func() { _, _ = io.Copy(a, b); done <- struct{}{} }()
	<-done
}

func wgConfigToIpcSet(path string) (string, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return "", err
	}
	var priv, pub, endpoint, allowed string
	var keepalive int
	sc := bufio.NewScanner(strings.NewReader(string(data)))
	for sc.Scan() {
		line := strings.TrimSpace(sc.Text())
		if line == "" || strings.HasPrefix(line, "#") || strings.HasPrefix(line, "[") {
			continue
		}
		key, val, ok := strings.Cut(line, "=")
		if !ok {
			continue
		}
		key = strings.TrimSpace(strings.ToLower(key))
		val = strings.TrimSpace(val)
		switch key {
		case "privatekey":
			priv, err = wgKeyBase64ToHex(val)
			if err != nil {
				return "", fmt.Errorf("private key: %w", err)
			}
		case "publickey":
			pub, err = wgKeyBase64ToHex(val)
			if err != nil {
				return "", fmt.Errorf("public key: %w", err)
			}
		case "endpoint":
			endpoint = val
		case "allowedips":
			allowed = strings.Split(val, ",")[0]
			allowed = strings.TrimSpace(allowed)
		case "persistentkeepalive":
			keepalive, _ = strconv.Atoi(val)
		}
	}
	if err := sc.Err(); err != nil {
		return "", err
	}
	if priv == "" || pub == "" || endpoint == "" || allowed == "" {
		return "", fmt.Errorf("incomplete wireguard config %s", path)
	}
	endpoint, err = resolveWireGuardEndpoint(endpoint)
	if err != nil {
		return "", fmt.Errorf("wireguard endpoint: %w", err)
	}
	var b strings.Builder
	b.WriteString("private_key=")
	b.WriteString(priv)
	b.WriteByte('\n')
	b.WriteString("public_key=")
	b.WriteString(pub)
	b.WriteByte('\n')
	b.WriteString("endpoint=")
	b.WriteString(endpoint)
	b.WriteByte('\n')
	b.WriteString("allowed_ip=")
	b.WriteString(allowed)
	b.WriteByte('\n')
	if keepalive > 0 {
		b.WriteString("persistent_keepalive_interval=")
		b.WriteString(strconv.Itoa(keepalive))
		b.WriteByte('\n')
	}
	b.WriteByte('\n')
	return b.String(), nil
}

func wgKeyBase64ToHex(b64 string) (string, error) {
	raw, err := base64.StdEncoding.DecodeString(b64)
	if err != nil {
		return "", err
	}
	return hex.EncodeToString(raw), nil
}

func wireguardPIDPath(cfg *Config) string {
	if path := strings.TrimSpace(cfg.Tunnel.SSH.PIDFile); path != "" {
		return filepath.Join(filepath.Dir(expandHome(path)), "wireguard.pid")
	}
	return expandHome(defaultWireGuardPIDFile)
}

func writeWireGuardPID(cfg *Config, pid int) error {
	path := wireguardPIDPath(cfg)
	if err := os.MkdirAll(filepath.Dir(path), 0o755); err != nil {
		return err
	}
	return os.WriteFile(path, []byte(strconv.Itoa(pid)+"\n"), 0o644)
}

func readWireGuardPID(cfg *Config) (int, string, error) {
	path := wireguardPIDPath(cfg)
	data, err := os.ReadFile(path)
	if err != nil {
		return 0, path, err
	}
	pid, err := strconv.Atoi(strings.TrimSpace(string(data)))
	if err != nil {
		return 0, path, fmt.Errorf("invalid pid in %s: %w", path, err)
	}
	return pid, path, nil
}

func wireguardProcessAlive(pid int) bool {
	if pid <= 0 {
		return false
	}
	proc, err := os.FindProcess(pid)
	if err != nil {
		return false
	}
	return proc.Signal(syscall.Signal(0)) == nil
}