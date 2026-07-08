package status

import (
	"fmt"
	"io"
	"net"
	"net/http"
	"strings"
	"time"

	"bedrud/devcli/internal/ports"
)

// Result is one local service probe outcome.
type Result struct {
	Name   string
	OK     bool
	Detail string
	Hint   string
}

// Report aggregates local probe results.
type Report struct {
	Results []Result
}

func (r Report) AllOK() bool {
	for _, res := range r.Results {
		if !res.OK {
			return false
		}
	}
	return true
}

// CheckLocal probes livekit, api, and web on the local dev ports.
func CheckLocal() Report {
	var report Report
	report.Results = append(report.Results,
		probeLocalHTTP("web", ports.Web, "http://127.0.0.1:%d/", "200", "devcli run --yes"),
		probeLocalHTTP("api", ports.API, "http://127.0.0.1:%d/api/health", "200", "devcli run --yes"),
		probeLocalHTTP("livekit", ports.LiveKit, "http://127.0.0.1:%d/", "OK", "devcli run --yes (local mode includes livekit)"),
	)
	return report
}

// CheckLocalRemote probes only local api and web (LiveKit runs on the remote server).
func CheckLocalRemote(webPort, apiPort int) Report {
	var report Report
	report.Results = append(report.Results,
		probeLocalHTTP("web", webPort, "http://127.0.0.1:%d/", "200", "devcli remote run --yes"),
		probeLocalHTTP("api", apiPort, "http://127.0.0.1:%d/api/health", "200", "devcli remote run --yes"),
	)
	return report
}

func probeLocalHTTP(name string, port int, urlFmt, wantBody, hint string) Result {
	if !tcpReady(port) {
		return Result{
			Name: name, OK: false,
			Detail: fmt.Sprintf("127.0.0.1:%d not listening", port),
			Hint:   hint,
		}
	}
	url := fmt.Sprintf(urlFmt, port)
	client := &http.Client{Timeout: 2 * time.Second}
	resp, err := client.Get(url)
	if err != nil {
		return Result{Name: name, OK: false, Detail: err.Error(), Hint: hint}
	}
	defer resp.Body.Close()
	body, _ := io.ReadAll(io.LimitReader(resp.Body, 64))
	bodyStr := strings.TrimSpace(string(body))
	ok := false
	if wantBody == "200" {
		ok = resp.StatusCode == 200
	} else {
		ok = resp.StatusCode == 200 && bodyStr == wantBody
	}
	detail := fmt.Sprintf(":%d HTTP %d", port, resp.StatusCode)
	if bodyStr != "" && wantBody != "200" {
		detail += " " + bodyStr
	}
	return Result{Name: name, OK: ok, Detail: detail, Hint: hint}
}

func tcpReady(port int) bool {
	conn, err := net.DialTimeout("tcp", fmt.Sprintf("127.0.0.1:%d", port), 250*time.Millisecond)
	if err != nil {
		return false
	}
	_ = conn.Close()
	return true
}

// PrintLocalRemote renders local api/web probe results for dev-remote mode.
func PrintLocalRemote(report Report) bool {
	fmt.Println("Local dev-remote backends (api + web; LiveKit on server)")
	fmt.Println()
	for _, res := range report.Results {
		mark := "ok"
		if !res.OK {
			mark = "x"
		}
		fmt.Printf("  %-10s %s  %s\n", res.Name, mark, res.Detail)
		if !res.OK && res.Hint != "" {
			fmt.Printf("  %-10s     → %s\n", "", res.Hint)
		}
	}
	fmt.Println()
	if report.AllOK() {
		fmt.Println("All checks passed.")
		return true
	}
	fmt.Println("Some services are down.")
	return false
}

// PrintLocal renders local probe results.
func PrintLocal(report Report) bool {
	fmt.Println("Local dev stack status")
	fmt.Println()
	for _, res := range report.Results {
		mark := "ok"
		if !res.OK {
			mark = "x"
		}
		fmt.Printf("  %-10s %s  %s\n", res.Name, mark, res.Detail)
		if !res.OK && res.Hint != "" {
			fmt.Printf("  %-10s     → %s\n", "", res.Hint)
		}
	}
	fmt.Println()
	if report.AllOK() {
		fmt.Println("All checks passed.")
		return true
	}
	fmt.Println("Some services are down.")
	return false
}