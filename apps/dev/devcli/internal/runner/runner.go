package runner

import (
	"context"
	"errors"
	"fmt"
	"io"
	"os"
	"os/exec"
	"os/signal"
	"path/filepath"
	"sync"
	"syscall"
	"time"

	"bedrud/devcli/internal/logfmt"
	"bedrud/devcli/internal/ports"
	"bedrud/devcli/internal/remote"
	"bedrud/devcli/internal/root"
)

type Service struct {
	Name    string
	Dir     string
	Command string
	Args    []string
	Env     []string
	Delay   time.Duration
}

type Options struct {
	RepoRoot   string
	Timestamps bool
	AutoStop   bool // stop conflicting processes without prompting
	Remote     *remote.Config
}

type Runner struct {
	repo string
	opts Options
}

func New(opts Options) (*Runner, error) {
	repo := opts.RepoRoot
	if repo == "" {
		var err error
		repo, err = root.Find("")
		if err != nil {
			return nil, err
		}
	}
	return &Runner{repo: repo, opts: opts}, nil
}

func (r *Runner) services() []Service {
	apiEnv := []string(nil)
	webEnv := []string(nil)
	if r.opts.Remote != nil {
		apiEnv = r.opts.Remote.APIEnv()
		webEnv = r.opts.Remote.WebEnv()
	}

	svcs := []Service{
		{
			Name:    "api",
			Dir:     filepath.Join(r.repo, "server"),
			Command: "air",
			Args:    nil,
			Env:     apiEnv,
			Delay:   time.Second,
		},
		{
			Name:    "web",
			Dir:     filepath.Join(r.repo, "apps", "web"),
			Command: "bun",
			Args:    []string{"run", "dev"},
			Env:     webEnv,
			Delay:   0,
		},
	}

	if r.opts.Remote == nil {
		lk := Service{
			Name:    "livekit",
			Dir:     r.repo,
			Command: "livekit-server",
			Args: []string{
				"--config", filepath.Join(r.repo, "server", "livekit.yaml"),
				"--dev",
			},
			Env:   []string{"LIVEKIT_BIND_IP=0.0.0.0"},
			Delay: 0,
		}
		svcs = append([]Service{lk}, svcs...)
	}
	return svcs
}

func (r *Runner) Preflight() error {
	checks := []struct {
		path string
		hint string
	}{
		{filepath.Join(r.repo, "server", "config.yaml"), "run make init"},
	}
	if r.opts.Remote == nil {
		checks = append(checks, struct {
			path string
			hint string
		}{filepath.Join(r.repo, "server", "livekit.yaml"), "run make init"})
	}
	for _, c := range checks {
		if _, err := os.Stat(c.path); err != nil {
			return fmt.Errorf("missing %s (%s)", c.path, c.hint)
		}
	}
	bins := []string{"air", "bun"}
	if r.opts.Remote == nil {
		bins = append(bins, "livekit-server")
	}
	for _, bin := range bins {
		if _, err := exec.LookPath(bin); err != nil {
			return fmt.Errorf("%s not found in PATH (run make init)", bin)
		}
	}
	return nil
}

// AfterStartHook runs after all services have been launched but before blocking on them.
type AfterStartHook func(context.Context) error

func (r *Runner) Run(ctx context.Context) error {
	return r.RunWithHook(ctx, nil)
}

func (r *Runner) RunWithHook(ctx context.Context, afterStart AfterStartHook) error {
	if err := r.Preflight(); err != nil {
		return err
	}
	if r.opts.Remote == nil {
		if err := ports.ResolveConflicts(r.repo, r.opts.AutoStop); err != nil {
			return err
		}
	}

	r.printBanner()

	ctx, cancel := context.WithCancel(ctx)
	defer cancel()

	sigCh := make(chan os.Signal, 1)
	signal.Notify(sigCh, syscall.SIGINT, syscall.SIGTERM)
	defer signal.Stop(sigCh)

	var mu sync.Mutex
	color := logfmt.UseColor()
	services := r.services()
	procs := make([]*exec.Cmd, 0, len(services))
	errCh := make(chan error, len(services))

	for _, svc := range services {
		if svc.Delay > 0 {
			select {
			case <-ctx.Done():
				return ctx.Err()
			case <-time.After(svc.Delay):
			}
		}

		cmd, err := r.startService(ctx, svc, &mu, color)
		if err != nil {
			r.stopAll(procs)
			return err
		}
		procs = append(procs, cmd)

		go func(name string, c *exec.Cmd) {
			err := c.Wait()
			if err != nil && ctx.Err() == nil {
				var exitErr *exec.ExitError
				if errors.As(err, &exitErr) {
					errCh <- fmt.Errorf("%s exited (code %d)", name, exitErr.ExitCode())
					return
				}
				errCh <- fmt.Errorf("%s: %w", name, err)
			}
		}(svc.Name, cmd)
	}

	if afterStart != nil {
		if err := afterStart(ctx); err != nil {
			cancel()
			r.stopAll(procs)
			return err
		}
	}

	go func() {
		select {
		case <-sigCh:
			fmt.Fprintln(os.Stdout)
			logfmt.Line(os.Stdout, &mu, color, r.opts.Timestamps, "devcli", "stopping services...")
			cancel()
			r.stopAll(procs)
		case <-ctx.Done():
		}
	}()

	select {
	case err := <-errCh:
		cancel()
		r.stopAll(procs)
		return err
	case <-ctx.Done():
		r.stopAll(procs)
		return nil
	}
}

func (r *Runner) startService(ctx context.Context, svc Service, mu *sync.Mutex, color bool) (*exec.Cmd, error) {
	cmd := exec.CommandContext(ctx, svc.Command, svc.Args...)
	cmd.Dir = svc.Dir
	cmd.Env = append(os.Environ(), svc.Env...)
	cmd.SysProcAttr = &syscall.SysProcAttr{Setpgid: true}

	stdout, err := cmd.StdoutPipe()
	if err != nil {
		return nil, err
	}
	stderr, err := cmd.StderrPipe()
	if err != nil {
		return nil, err
	}

	if err := cmd.Start(); err != nil {
		return nil, fmt.Errorf("start %s: %w", svc.Name, err)
	}

	outW := logfmt.NewWriter(svc.Name, os.Stdout, mu, color, r.opts.Timestamps)
	errW := logfmt.NewWriter(svc.Name, os.Stderr, mu, color, r.opts.Timestamps)
	go pipe(stdout, outW)
	go pipe(stderr, errW)

	logfmt.Line(os.Stdout, mu, color, r.opts.Timestamps, "devcli", fmt.Sprintf("started %s (pid %d)", svc.Name, cmd.Process.Pid))

	return cmd, nil
}

func pipe(r io.Reader, w io.Writer) {
	_, _ = io.Copy(w, r)
}

func (r *Runner) stopAll(procs []*exec.Cmd) {
	// Kill tracked parents (livekit, air, bun) by process group.
	for _, cmd := range procs {
		if cmd.Process == nil {
			continue
		}
		if pgid, err := syscall.Getpgid(cmd.Process.Pid); err == nil {
			_ = syscall.Kill(-pgid, syscall.SIGTERM)
		} else {
			_ = cmd.Process.Signal(syscall.SIGTERM)
		}
	}
	time.Sleep(300 * time.Millisecond)
	for _, cmd := range procs {
		if cmd.Process == nil {
			continue
		}
		if pgid, err := syscall.Getpgid(cmd.Process.Pid); err == nil {
			_ = syscall.Kill(-pgid, syscall.SIGKILL)
		} else {
			_ = cmd.Process.Kill()
		}
	}
	// Air leaves ./tmp/server listening on :7071 — sweep all dev ports/orphans.
	_, _ = ports.StopDev(r.repo)
	if r.opts.Remote != nil {
		_, _ = remote.TunnelDown(r.opts.Remote)
	}
}

func (r *Runner) printBanner() {
	color := logfmt.UseColor()
	head := "Bedrud dev stack"
	if r.opts.Remote != nil {
		head = fmt.Sprintf("Bedrud dev-remote — LiveKit on server, api + web local (%s tunnel)", r.opts.Remote.TunnelMode())
	}
	if color {
		head = "\033[1m\033[36m" + head + "\033[0m"
	}
	fmt.Println(head)
	if r.opts.Remote != nil {
		cfg := r.opts.Remote
		fmt.Println("  SERVER (remote):")
		fmt.Printf("    livekit  → %s\n", cfg.URLs.LiveKitHost)
		fmt.Printf("    public   → %s\n", cfg.URLs.PublicBase)
		fmt.Println("  LOCAL:")
		fmt.Printf("    web      → http://localhost:%d\n", cfg.Local.WebPort)
		fmt.Printf("    api      → http://localhost:%d  (swagger /api/swagger)\n", cfg.Local.APIPort)
		switch {
		case cfg.UsesDevTunnel():
			fmt.Printf("  tunnel   → devtunnel %s (reverse web/api, LiveKit %s)\n",
				cfg.DevTunnelServerAddr(), cfg.URLs.LiveKitInternal)
		case cfg.UsesSSHTunnel():
			fmt.Printf("  tunnel   → ssh -L %d (livekit), -R %d/-R %d (web/api)\n",
				cfg.Tunnel.SSH.LocalLiveKitPort, cfg.Tunnel.SSH.RemoteWebPort, cfg.Tunnel.SSH.RemoteAPIPort)
		default:
			fmt.Printf("  tunnel   → netstack WG %s (%s ↔ %s, LiveKit %s)\n",
				cfg.WireGuard.Interface, cfg.WireGuard.LocalTunnelIP, cfg.WireGuard.RemoteTunnelIP, cfg.URLs.LiveKitInternal)
		}
	} else {
		fmt.Printf("  web      → http://localhost:%d\n", ports.Web)
		fmt.Printf("  api      → http://localhost:%d  (swagger /api/swagger)\n", ports.API)
		fmt.Printf("  livekit  → localhost:%d\n", ports.LiveKit)
	}
	fmt.Println("  logs     → docker-compose style (service | message)")
	fmt.Println("  stop     → Ctrl+C or make dev-stop-all")
	if r.opts.Remote != nil {
		fmt.Println("  note     → Vite HMR off (reload meeting tabs after frontend edits)")
	}
	fmt.Println()
}