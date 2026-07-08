package logfmt

import (
	"io"
	"os"
	"sync"
)

var defaultMu sync.Mutex

// Println writes one docker-compose-style log line to stdout.
func Println(service, line string) {
	Line(os.Stdout, &defaultMu, UseColor(), false, service, line)
}

// Line writes a prefixed log line (used by Writer and remote sync helpers).
func Line(dest io.Writer, mu *sync.Mutex, color, timestamps bool, service, line string) {
	if mu == nil {
		mu = &defaultMu
	}
	w := NewWriter(service, dest, mu, color, timestamps)
	w.writeLine(line)
}