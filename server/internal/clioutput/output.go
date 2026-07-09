package clioutput

import (
	"encoding/json"
	"fmt"
	"io"
	"os"
)

// Result is the standard JSON envelope for CLI commands invoked with --json.
type Result struct {
	OK      bool   `json:"ok"`
	Message string `json:"message,omitempty"`
	Data    any    `json:"data,omitempty"`
}

var (
	jsonMode bool
	stdout   io.Writer = os.Stdout
	stderr   io.Writer = os.Stderr
)

// SetJSON enables or disables machine-readable JSON output.
func SetJSON(v bool) {
	jsonMode = v
}

// JSON reports whether JSON output mode is active.
func JSON() bool {
	return jsonMode
}

// SetWriters overrides stdout/stderr (for tests).
func SetWriters(out, err io.Writer) {
	stdout = out
	stderr = err
}

// ResetWriters restores stdout/stderr to os defaults.
func ResetWriters() {
	stdout = os.Stdout
	stderr = os.Stderr
}

// Success writes a successful Result when JSON mode is on; otherwise prints message.
func Success(message string, data any) error {
	if jsonMode {
		return writeJSON(stdout, Result{OK: true, Message: message, Data: data})
	}
	if message != "" {
		fmt.Fprintln(stdout, message)
	}
	return nil
}

// Printf writes formatted text only when not in JSON mode.
func Printf(format string, args ...any) {
	if jsonMode {
		return
	}
	fmt.Fprintf(stdout, format, args...)
}

// Println writes a line only when not in JSON mode.
func Println(args ...any) {
	if jsonMode {
		return
	}
	fmt.Fprintln(stdout, args...)
}

// Emit writes raw JSON (any value) to stdout when JSON mode is on.
func Emit(v any) error {
	if !jsonMode {
		return fmt.Errorf("clioutput.Emit called without JSON mode")
	}
	return writeJSON(stdout, v)
}

// EmitResult writes a Result envelope to stdout.
func EmitResult(r Result) error {
	return writeJSON(stdout, r)
}

// EmitError writes a failed Result to stderr and is used by the root executor.
func EmitError(err error) {
	_ = writeJSON(stderr, Result{OK: false, Message: err.Error()})
}

func writeJSON(w io.Writer, v any) error {
	out, err := json.MarshalIndent(v, "", "  ")
	if err != nil {
		return err
	}
	_, err = fmt.Fprintln(w, string(out))
	return err
}
