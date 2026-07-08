package tunnel

import (
	"bufio"
	"fmt"
	"io"
	"net"
	"strings"
)

const (
	protocolVersion = "BEDRUD-DEVTUNNELv1"

	// Stream prefixes (first byte on each yamux stream).
	streamWeb     byte = 'W'
	streamAPI     byte = 'A'
	streamLiveKit byte = 'L'
)

// HandshakeClient authenticates and leaves conn ready for yamux.
func HandshakeClient(conn net.Conn, token string) error {
	if err := writeLine(conn, protocolVersion); err != nil {
		return err
	}
	if err := writeLine(conn, strings.TrimSpace(token)); err != nil {
		return err
	}
	br := bufio.NewReader(conn)
	line, err := readLineFrom(br)
	if err != nil {
		return err
	}
	if line != "OK" {
		return fmt.Errorf("handshake rejected: %s", line)
	}
	return nil
}

// HandshakeServer validates the client token.
func HandshakeServer(conn net.Conn, expectToken string) error {
	br := bufio.NewReader(conn)
	line, err := readLineFrom(br)
	if err != nil {
		return err
	}
	if line != protocolVersion {
		return fmt.Errorf("unsupported protocol: %q", line)
	}
	token, err := readLineFrom(br)
	if err != nil {
		return err
	}
	if strings.TrimSpace(token) != strings.TrimSpace(expectToken) {
		_ = writeLine(conn, "ERR unauthorized")
		return fmt.Errorf("unauthorized")
	}
	return writeLine(conn, "OK")
}

func writeLine(w io.Writer, s string) error {
	_, err := io.WriteString(w, s+"\n")
	return err
}

func readLineFrom(br *bufio.Reader) (string, error) {
	line, err := br.ReadString('\n')
	if err != nil {
		return "", err
	}
	return strings.TrimSpace(line), nil
}