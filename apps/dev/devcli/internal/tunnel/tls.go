package tunnel

import (
	"crypto/ed25519"
	"crypto/rand"
	"crypto/sha256"
	"crypto/tls"
	"crypto/x509"
	"crypto/x509/pkix"
	"encoding/hex"
	"encoding/pem"
	"fmt"
	"math/big"
	"net"
	"os"
	"strings"
	"time"
)

const tlsMinVersion = tls.VersionTLS13

// GenerateServerTLS creates a self-signed TLS cert/key for the tunnel agent.
func GenerateServerTLS(hosts ...string) (certPEM, keyPEM []byte, fingerprint string, err error) {
	pub, priv, err := ed25519.GenerateKey(rand.Reader)
	if err != nil {
		return nil, nil, "", err
	}

	dnsNames, ipAddrs := parseSANHosts(hosts...)
	if len(dnsNames) == 0 && len(ipAddrs) == 0 {
		dnsNames = []string{"localhost"}
		ipAddrs = []net.IP{net.ParseIP("127.0.0.1")}
	}

	serial, err := rand.Int(rand.Reader, new(big.Int).Lsh(big.NewInt(1), 128))
	if err != nil {
		return nil, nil, "", err
	}

	cn := "bedrud-devtunnel"
	if len(dnsNames) > 0 {
		cn = dnsNames[0]
	} else if len(ipAddrs) > 0 {
		cn = ipAddrs[0].String()
	}

	now := time.Now()
	tmpl := x509.Certificate{
		SerialNumber: serial,
		Subject: pkix.Name{
			Organization: []string{"Bedrud devtunnel"},
			CommonName:   cn,
		},
		NotBefore:             now.Add(-time.Hour),
		NotAfter:              now.Add(5 * 365 * 24 * time.Hour),
		KeyUsage:              x509.KeyUsageDigitalSignature,
		ExtKeyUsage:           []x509.ExtKeyUsage{x509.ExtKeyUsageServerAuth},
		BasicConstraintsValid: true,
		DNSNames:              dnsNames,
		IPAddresses:           ipAddrs,
	}

	der, err := x509.CreateCertificate(rand.Reader, &tmpl, &tmpl, pub, priv)
	if err != nil {
		return nil, nil, "", err
	}

	certPEM = pem.EncodeToMemory(&pem.Block{Type: "CERTIFICATE", Bytes: der})
	privBytes, err := x509.MarshalPKCS8PrivateKey(priv)
	if err != nil {
		return nil, nil, "", err
	}
	keyPEM = pem.EncodeToMemory(&pem.Block{Type: "PRIVATE KEY", Bytes: privBytes})
	fingerprint, err = FingerprintCertPEM(certPEM)
	if err != nil {
		return nil, nil, "", err
	}
	return certPEM, keyPEM, fingerprint, nil
}

// FingerprintCertPEM returns the SHA-256 fingerprint of a PEM-encoded certificate.
func FingerprintCertPEM(certPEM []byte) (string, error) {
	block, _ := pem.Decode(certPEM)
	if block == nil {
		return "", fmt.Errorf("invalid certificate PEM")
	}
	sum := sha256.Sum256(block.Bytes)
	return hex.EncodeToString(sum[:]), nil
}

// FingerprintCertFile fingerprints a certificate file on disk.
func FingerprintCertFile(path string) (string, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return "", err
	}
	return FingerprintCertPEM(data)
}

func parseSANHosts(hosts ...string) (dns []string, ips []net.IP) {
	seenDNS := make(map[string]struct{})
	seenIP := make(map[string]struct{})
	for _, host := range hosts {
		host = strings.TrimSpace(host)
		if host == "" {
			continue
		}
		if ip := net.ParseIP(host); ip != nil {
			key := ip.String()
			if _, ok := seenIP[key]; ok {
				continue
			}
			seenIP[key] = struct{}{}
			ips = append(ips, ip)
			continue
		}
		if _, ok := seenDNS[host]; ok {
			continue
		}
		seenDNS[host] = struct{}{}
		dns = append(dns, host)
	}
	return dns, ips
}

func normalizeFingerprint(fp string) string {
	return strings.ToLower(strings.TrimPrefix(strings.TrimSpace(fp), "sha256:"))
}

// ServerTLSConfig loads TLS settings for the tunnel agent.
func ServerTLSConfig(certFile, keyFile string) (*tls.Config, error) {
	cert, err := tls.LoadX509KeyPair(certFile, keyFile)
	if err != nil {
		return nil, err
	}
	return &tls.Config{
		Certificates: []tls.Certificate{cert},
		MinVersion:   tlsMinVersion,
	}, nil
}

// ClientTLSConfig pins the server certificate fingerprint (SHA-256 of DER).
func ClientTLSConfig(fingerprint, serverName string) (*tls.Config, error) {
	fp := normalizeFingerprint(fingerprint)
	if fp == "" {
		return nil, fmt.Errorf("empty TLS fingerprint")
	}
	want, err := hex.DecodeString(fp)
	if err != nil {
		return nil, fmt.Errorf("invalid TLS fingerprint: %w", err)
	}
	if serverName == "" {
		serverName = "bedrud-devtunnel"
	}
	return &tls.Config{
		MinVersion:         tlsMinVersion,
		ServerName:         serverName,
		InsecureSkipVerify: true, //nolint:gosec // pinned below
		VerifyPeerCertificate: func(rawCerts [][]byte, _ [][]*x509.Certificate) error {
			if len(rawCerts) == 0 {
				return fmt.Errorf("no server certificate")
			}
			sum := sha256.Sum256(rawCerts[0])
			if !equalBytes(sum[:], want) {
				return fmt.Errorf("certificate fingerprint mismatch")
			}
			return nil
		},
	}, nil
}

func equalBytes(a, b []byte) bool {
	if len(a) != len(b) {
		return false
	}
	var v byte
	for i := range a {
		v |= a[i] ^ b[i]
	}
	return v == 0
}

// ListenTLS listens for TLS connections on addr.
func ListenTLS(addr, certFile, keyFile string) (net.Listener, error) {
	cfg, err := ServerTLSConfig(certFile, keyFile)
	if err != nil {
		return nil, err
	}
	return tls.Listen("tcp", addr, cfg)
}

// DialTLS connects to a TLS tunnel agent with fingerprint pinning.
func DialTLS(addr, fingerprint, serverName string) (net.Conn, error) {
	cfg, err := ClientTLSConfig(fingerprint, serverName)
	if err != nil {
		return nil, err
	}
	return tls.Dial("tcp", addr, cfg)
}