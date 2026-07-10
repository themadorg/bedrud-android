package remote

import (
	"fmt"
	"net"
	"strings"
)

// resolveWireGuardEndpoint resolves host:port to IP:port for WireGuard IPC (hostnames are not accepted).
func resolveWireGuardEndpoint(endpoint string) (string, error) {
	endpoint = strings.TrimSpace(endpoint)
	if endpoint == "" {
		return "", fmt.Errorf("empty wireguard endpoint")
	}
	host, port, err := net.SplitHostPort(endpoint)
	if err != nil {
		return "", fmt.Errorf("parse endpoint %q: %w", endpoint, err)
	}
	if ip := net.ParseIP(host); ip != nil {
		return net.JoinHostPort(ip.String(), port), nil
	}
	ips, err := net.LookupIP(host)
	if err != nil {
		return "", fmt.Errorf("resolve %q: %w", host, err)
	}
	for _, ip := range ips {
		if v4 := ip.To4(); v4 != nil {
			return net.JoinHostPort(v4.String(), port), nil
		}
	}
	if len(ips) > 0 {
		return net.JoinHostPort(ips[0].String(), port), nil
	}
	return "", fmt.Errorf("no addresses for %q", host)
}

func (c *Config) wireGuardEndpointResolved() (string, error) {
	return resolveWireGuardEndpoint(c.WireGuardEndpoint())
}