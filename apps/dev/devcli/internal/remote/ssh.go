package remote

import (
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
)

// SSH runs a command on the remote debug server. With no command, opens an interactive shell.
func SSH(cfg *Config, command ...string) error {
	if len(command) == 0 {
		args := cfg.sshBaseArgs(false)
		args = append(args, "-t", cfg.SSHTarget())
		return runTTY("ssh", args...)
	}
	return run("ssh", cfg.sshArgs(command...)...)
}

// SSHOutput runs a remote command and returns combined stdout/stderr.
func SSHOutput(cfg *Config, command string) (string, error) {
	args := cfg.sshArgs(command)
	cmd := exec.Command("ssh", args...)
	out, err := cmd.CombinedOutput()
	return strings.TrimSpace(string(out)), err
}

// SCP uploads local files to the remote server.
func SCP(cfg *Config, localPaths []string, remoteDir string) error {
	args := cfg.scpArgs()
	remote := fmt.Sprintf("%s:%s/", cfg.SSHTarget(), strings.TrimSuffix(remoteDir, "/"))
	args = append(args, append(localPaths, remote)...)
	return run("scp", args...)
}

func (c *Config) sshArgs(command ...string) []string {
	args := c.sshBaseArgs(true)
	args = append(args, c.SSHTarget())
	if len(command) > 0 {
		// OpenSSH joins multiple argv words with spaces on the remote shell; quote so
		// metacharacters in bash -c scripts (&&, $(...), etc.) are not split early.
		args = append(args, buildRemoteCommand(command...))
	}
	return args
}

func buildRemoteCommand(parts ...string) string {
	if len(parts) == 1 {
		return parts[0]
	}
	for i, part := range parts {
		if part == "-c" && i+1 < len(parts) {
			head := strings.Join(parts[:i+1], " ")
			script := strings.Join(parts[i+1:], " ")
			return head + " " + shellQuote(script)
		}
	}
	var b strings.Builder
	for i, part := range parts {
		if i > 0 {
			b.WriteByte(' ')
		}
		b.WriteString(shellQuote(part))
	}
	return b.String()
}

func (c *Config) scpArgs() []string {
	args := []string{
		"-q",
		"-P", fmt.Sprint(c.SSH.Port),
		"-o", "StrictHostKeyChecking=accept-new",
		"-o", "BatchMode=yes",
	}
	if c.SSH.IdentityFile != "" {
		args = append(args, "-i", c.SSH.IdentityFile)
	}
	return args
}

func (c *Config) sshBaseArgs(batch bool) []string {
	args := []string{
		"-p", fmt.Sprint(c.SSH.Port),
		"-o", "StrictHostKeyChecking=accept-new",
	}
	if batch {
		args = append(args, "-o", "BatchMode=yes")
	}
	if c.SSH.IdentityFile != "" {
		args = append(args, "-i", c.SSH.IdentityFile)
	}
	return args
}

func shellQuote(s string) string {
	if s == "" {
		return "''"
	}
	if strings.ContainsAny(s, " \t\n'\"\\$`") {
		return "'" + strings.ReplaceAll(s, "'", "'\\''") + "'"
	}
	return s
}

func run(name string, args ...string) error {
	cmd := exec.Command(name, args...)
	cmd.Stdout = os.Stdout
	cmd.Stderr = os.Stderr
	cmd.Stdin = os.Stdin
	return cmd.Run()
}

func runTTY(name string, args ...string) error {
	cmd := exec.Command(name, args...)
	cmd.Stdout = os.Stdout
	cmd.Stderr = os.Stderr
	cmd.Stdin = os.Stdin
	return cmd.Run()
}

func copyOutput(cmd *exec.Cmd) (string, error) {
	out, err := cmd.Output()
	if err != nil {
		if ee, ok := err.(*exec.ExitError); ok {
			return strings.TrimSpace(string(ee.Stderr)), err
		}
		return "", err
	}
	return strings.TrimSpace(string(out)), nil
}

// pingSSH returns nil when the remote host is reachable via SSH.
func pingSSH(cfg *Config) error {
	cmd := exec.Command("ssh", cfg.sshArgs("echo ok")...)
	out, err := copyOutput(cmd)
	if err != nil {
		return fmt.Errorf("ssh %s: %w", cfg.SSHTarget(), err)
	}
	if out != "ok" {
		return fmt.Errorf("ssh %s: unexpected response %q", cfg.SSHTarget(), out)
	}
	return nil
}

// MkdirRemote creates a directory on the remote server.
func MkdirRemote(cfg *Config, dir string) error {
	return SSH(cfg, "mkdir", "-p", dir)
}

// SSHSudo runs a command with sudo on the remote server (directly as root when SSH user is root).
func SSHSudo(cfg *Config, script string) error {
	script = "set -euo pipefail\n" + script
	if cfg.SSH.User == "root" {
		return SSH(cfg, "bash", "-c", script)
	}
	return SSH(cfg, "sudo", "bash", "-c", script)
}

// UploadContent writes a file on the remote server (via /tmp + sudo install).
func UploadContent(cfg *Config, content, remotePath, mode string) error {
	tmp, err := os.CreateTemp("", "bedrud-provision-*")
	if err != nil {
		return err
	}
	tmpPath := tmp.Name()
	defer os.Remove(tmpPath)

	if _, err := tmp.WriteString(content); err != nil {
		_ = tmp.Close()
		return err
	}
	if err := tmp.Close(); err != nil {
		return err
	}

	remoteTmp := "/tmp/bedrud-upload-" + filepath.Base(remotePath)
	scpArgs := cfg.scpArgs()
	scpArgs = append(scpArgs, tmpPath, cfg.SSHTarget()+":"+remoteTmp)
	if err := run("scp", scpArgs...); err != nil {
		return err
	}

	script := fmt.Sprintf(
		"sudo install -d -m 755 $(dirname %s) && sudo install -m %s %s %s",
		shellQuote(remotePath),
		mode,
		shellQuote(remoteTmp),
		shellQuote(remotePath),
	)
	return SSH(cfg, "bash", "-c", script)
}

