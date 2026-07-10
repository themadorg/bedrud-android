package root

import (
	"fmt"
	"os"
	"path/filepath"
)

// Find walks up from start (or cwd) until it finds the Bedrud repo root.
func Find(start string) (string, error) {
	if start == "" {
		var err error
		start, err = os.Getwd()
		if err != nil {
			return "", err
		}
	}

	dir, err := filepath.Abs(start)
	if err != nil {
		return "", err
	}

	for {
		if isRepoRoot(dir) {
			return dir, nil
		}
		parent := filepath.Dir(dir)
		if parent == dir {
			return "", fmt.Errorf("bedrud repo root not found (started at %s)", start)
		}
		dir = parent
	}
}

func isRepoRoot(dir string) bool {
	for _, name := range []string{"Makefile", "server", "apps/web"} {
		if _, err := os.Stat(filepath.Join(dir, name)); err != nil {
			return false
		}
	}
	return true
}