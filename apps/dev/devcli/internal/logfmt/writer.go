package logfmt

import (
	"bytes"
	"fmt"
	"io"
	"os"
	"sync"
	"time"
)

const prefixWidth = 10

var serviceColors = map[string]string{
	"api":        "\033[32m", // green
	"web":        "\033[35m", // magenta
	"livekit":    "\033[36m", // cyan
	"traefik":    "\033[34m", // blue
	"ssh-tunnel": "\033[33m", // yellow
	"wireguard":  "\033[90m", // dim
	"devcli":     "\033[33m", // yellow
}

// Writer prefixes each line with a docker-compose-style service tag.
type Writer struct {
	service  string
	dest     io.Writer
	mu       *sync.Mutex
	color    bool
	time     bool
	leftover []byte
}

func NewWriter(service string, dest io.Writer, mu *sync.Mutex, color, timestamps bool) *Writer {
	return &Writer{
		service: service,
		dest:    dest,
		mu:      mu,
		color:   color,
		time:    timestamps,
	}
}

func (w *Writer) Write(p []byte) (int, error) {
	data := append(w.leftover, p...)
	for {
		idx := bytes.IndexByte(data, '\n')
		if idx < 0 {
			w.leftover = data
			return len(p), nil
		}
		line := string(data[:idx])
		if len(line) > 0 && line[len(line)-1] == '\r' {
			line = line[:len(line)-1]
		}
		w.writeLine(line)
		data = data[idx+1:]
	}
}

func (w *Writer) writeLine(line string) {
	w.mu.Lock()
	defer w.mu.Unlock()

	tag := fmt.Sprintf("%-*s", prefixWidth, w.service)
	prefix := tag + " | "
	if w.color {
		if c, ok := serviceColors[w.service]; ok {
			prefix = c + tag + "\033[0m | "
		}
	}
	if w.time {
		ts := time.Now().Format("15:04:05")
		prefix = "\033[90m" + ts + "\033[0m " + prefix
	}
	_, _ = fmt.Fprintf(w.dest, "%s%s\n", prefix, line)
}

func UseColor() bool {
	if os.Getenv("NO_COLOR") != "" {
		return false
	}
	return isTerminal(os.Stdout)
}

func isTerminal(f *os.File) bool {
	info, err := f.Stat()
	if err != nil {
		return false
	}
	return (info.Mode() & os.ModeCharDevice) != 0
}