package remote

import (
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"strings"
)

func contentSHA256(content string) string {
	sum := sha256.Sum256([]byte(content))
	return hex.EncodeToString(sum[:])
}

func remoteFileSHA256(cfg *Config, path string) (string, error) {
	out, err := SSHOutput(cfg, fmt.Sprintf("sha256sum %s 2>/dev/null | awk '{print $1}' || true", shellQuote(path)))
	if err != nil {
		return "", err
	}
	return strings.TrimSpace(out), nil
}

func remoteStoredHash(cfg *Config, sidecarPath string) string {
	out, _ := SSHOutput(cfg, fmt.Sprintf("cat %s 2>/dev/null || true", shellQuote(sidecarPath)))
	return strings.TrimSpace(out)
}

func storeRemoteHash(cfg *Config, hash, sidecarPath string) error {
	return UploadContent(cfg, hash+"\n", sidecarPath, "644")
}