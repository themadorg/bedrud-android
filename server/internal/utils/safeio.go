package utils

import (
	"fmt"
	"os"
	"path/filepath"
)

func checkParentChain(path string) error {
	dir := filepath.Clean(filepath.Dir(path))
	resolved, err := filepath.EvalSymlinks(dir)
	if err != nil {
		return fmt.Errorf("cannot resolve parent path %s: %w", dir, err)
	}
	if resolved != dir {
		return fmt.Errorf("parent path resolves to different path via symlinks: %s → %s", dir, resolved)
	}
	return nil
}

func SafeCreate(path string, perm os.FileMode) (*os.File, error) {
	path = filepath.Clean(path)
	if _, err := os.Lstat(path); err == nil {
		return nil, fmt.Errorf("path already exists: %s", path)
	}
	if err := checkParentChain(path); err != nil {
		return nil, err
	}
	f, err := os.OpenFile(path, os.O_WRONLY|os.O_CREATE|os.O_EXCL, perm)
	if err != nil {
		return nil, fmt.Errorf("failed to create %s: %w", path, err)
	}
	return f, nil
}

func SafeOpenAppend(path string, perm os.FileMode) (*os.File, error) {
	path = filepath.Clean(path)
	if fi, err := os.Lstat(path); err == nil {
		if fi.Mode()&os.ModeSymlink != 0 {
			return nil, fmt.Errorf("path is a symlink: %s", path)
		}
		if !fi.Mode().IsRegular() {
			return nil, fmt.Errorf("path exists but is not a regular file: %s", path)
		}
	} else if !os.IsNotExist(err) {
		return nil, fmt.Errorf("cannot stat path %s: %w", path, err)
	}
	if err := checkParentChain(path); err != nil {
		return nil, err
	}
	f, err := os.OpenFile(path, os.O_WRONLY|os.O_CREATE|os.O_APPEND, perm)
	if err != nil {
		return nil, fmt.Errorf("failed to open %s: %w", path, err)
	}
	return f, nil
}
