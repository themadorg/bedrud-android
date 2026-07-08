package main

import (
	"context"
	"flag"
	"fmt"
	"os"
	"time"

	"bedrud/devcli/internal/logfmt"
	"bedrud/devcli/internal/ports"
	"bedrud/devcli/internal/remote"
	"bedrud/devcli/internal/root"
	"bedrud/devcli/internal/runner"
)

func cmdRemote(args []string) int {
	if len(args) == 0 {
		args = []string{"status"}
	}
	switch args[0] {
	case "status":
		return cmdRemoteStatus(args[1:])
	case "ssh":
		return cmdRemoteSSH(args[1:])
	case "tunnel":
		return cmdRemoteTunnel(args[1:])
	case "wg":
		return cmdRemoteWG(args[1:])
	case "traefik":
		return cmdRemoteTraefik(args[1:])
	case "livekit":
		return cmdRemoteLiveKit(args[1:])
	case "run":
		return cmdRemoteRun(args[1:])
	case "tunnel-client":
		return cmdRemoteTunnelClient(args[1:])
	case "provision":
		return cmdRemoteProvision(args[1:])
	case "help", "-h", "--help":
		printRemoteUsage()
		return 0
	default:
		fmt.Fprintf(os.Stderr, "unknown remote subcommand %q\n\n", args[0])
		printRemoteUsage()
		return 2
	}
}

func loadRemoteConfig(repoFlag string) (*remote.Config, string, error) {
	repo := repoFlag
	if repo == "" {
		var err error
		repo, err = root.Find("")
		if err != nil {
			return nil, "", err
		}
	}
	cfg, err := remote.Load(repo)
	if err != nil {
		return nil, repo, err
	}
	return cfg, repo, nil
}

func cmdRemoteStatus(args []string) int {
	fs := flag.NewFlagSet("remote status", flag.ExitOnError)
	repo := fs.String("repo", "", "bedrud repo root")
	_ = fs.Parse(args)

	cfg, _, err := loadRemoteConfig(*repo)
	if err != nil {
		fmt.Fprintf(os.Stderr, "devcli: %v\n", err)
		return 1
	}
	ok, err := remote.PrintStatus(cfg)
	if err != nil {
		fmt.Fprintf(os.Stderr, "devcli: %v\n", err)
		return 1
	}
	if !ok {
		return 1
	}
	return 0
}

func cmdRemoteSSH(args []string) int {
	fs := flag.NewFlagSet("remote ssh", flag.ExitOnError)
	repo := fs.String("repo", "", "bedrud repo root")
	_ = fs.Parse(args)

	cfg, _, err := loadRemoteConfig(*repo)
	if err != nil {
		fmt.Fprintf(os.Stderr, "devcli: %v\n", err)
		return 1
	}
	if err := remote.SSH(cfg, fs.Args()...); err != nil {
		return 1
	}
	return 0
}

func cmdRemoteTunnelClient(args []string) int {
	fs := flag.NewFlagSet("remote tunnel-client", flag.ExitOnError)
	repo := fs.String("repo", "", "bedrud repo root")
	_ = fs.Parse(args)

	cfg, _, err := loadRemoteConfig(*repo)
	if err != nil {
		fmt.Fprintf(os.Stderr, "devcli: %v\n", err)
		return 1
	}
	if err := remote.RunDevTunnelClient(cfg); err != nil {
		fmt.Fprintf(os.Stderr, "devcli: %v\n", err)
		return 1
	}
	return 0
}

func cmdRemoteTunnel(args []string) int {
	if len(args) == 0 {
		fmt.Fprintln(os.Stderr, "usage: devcli remote tunnel <up|down|status|deploy>")
		return 2
	}
	fs := flag.NewFlagSet("remote tunnel", flag.ExitOnError)
	repo := fs.String("repo", "", "bedrud repo root")
	_ = fs.Parse(args[1:])

	cfg, _, err := loadRemoteConfig(*repo)
	if err != nil {
		fmt.Fprintf(os.Stderr, "devcli: %v\n", err)
		return 1
	}

	switch args[0] {
	case "up":
		if cfg.UsesDevTunnel() {
			if err := remote.DevTunnelEnsureAgent(cfg); err != nil {
				fmt.Fprintf(os.Stderr, "devcli: %v\n", err)
				return 1
			}
		}
		if err := remote.TunnelDetachedUp(cfg); err != nil {
			fmt.Fprintf(os.Stderr, "devcli: %v\n", err)
			return 1
		}
	case "deploy":
		if err := remote.DevTunnelDeploy(cfg); err != nil {
			fmt.Fprintf(os.Stderr, "devcli: %v\n", err)
			return 1
		}
	case "down":
		if _, err := remote.TunnelDown(cfg); err != nil {
			fmt.Fprintf(os.Stderr, "devcli: %v\n", err)
			return 1
		}
	case "status":
		up, detail, err := remote.TunnelStatus(cfg)
		if err != nil {
			fmt.Fprintf(os.Stderr, "devcli: %v\n", err)
			return 1
		}
		if up {
			fmt.Printf("%s: up (%s)\n", cfg.TunnelMode(), detail)
		} else {
			fmt.Printf("%s: down\n", cfg.TunnelMode())
		}
	default:
		fmt.Fprintf(os.Stderr, "unknown tunnel action %q (use up, down, status, deploy)\n", args[0])
		return 2
	}
	return 0
}

func cmdRemoteWG(args []string) int {
	if len(args) == 0 {
		fmt.Fprintln(os.Stderr, "usage: devcli remote wg <up|down|status|sync>")
		return 2
	}
	fs := flag.NewFlagSet("remote wg", flag.ExitOnError)
	repo := fs.String("repo", "", "bedrud repo root")
	_ = fs.Parse(args[1:])

	cfg, _, err := loadRemoteConfig(*repo)
	if err != nil {
		fmt.Fprintf(os.Stderr, "devcli: %v\n", err)
		return 1
	}
	if cfg.UsesSSHTunnel() {
		fmt.Fprintf(os.Stderr, "devcli: tunnel.mode is ssh — use: devcli remote tunnel %s\n", args[0])
		return 2
	}

	switch args[0] {
	case "sync":
		if err := remote.WireGuardEnsureClientConfig(cfg); err != nil {
			fmt.Fprintf(os.Stderr, "devcli: %v\n", err)
			return 1
		}
		fmt.Println("WireGuard client config ready:", cfg.WireGuard.ConfigFile)
	case "up":
		if err := remote.WireGuardEnsureClientConfig(cfg); err != nil {
			fmt.Fprintf(os.Stderr, "devcli: %v\n", err)
			return 1
		}
		if err := remote.WireGuardUp(cfg); err != nil {
			fmt.Fprintf(os.Stderr, "devcli: %v\n", err)
			return 1
		}
	case "down":
		if _, err := remote.WireGuardDown(cfg); err != nil {
			fmt.Fprintf(os.Stderr, "devcli: %v\n", err)
			return 1
		}
	case "status":
		st, err := remote.WireGuardStatus(cfg)
		if err != nil {
			fmt.Fprintf(os.Stderr, "devcli: %v\n", err)
			return 1
		}
		if st.Up {
			fmt.Printf("%s: up (local %s)\n", st.Interface, cfg.WireGuard.LocalTunnelIP)
		} else {
			fmt.Printf("%s: down\n", st.Interface)
		}
	default:
		fmt.Fprintf(os.Stderr, "unknown wg action %q (use up, down, status, sync)\n", args[0])
		return 2
	}
	return 0
}

func cmdRemoteTraefik(args []string) int {
	if len(args) == 0 {
		fmt.Fprintln(os.Stderr, "usage: devcli remote traefik <sync|status|show>")
		return 2
	}
	fs := flag.NewFlagSet("remote traefik", flag.ExitOnError)
	repo := fs.String("repo", "", "bedrud repo root")
	_ = fs.Parse(args[1:])

	cfg, _, err := loadRemoteConfig(*repo)
	if err != nil {
		fmt.Fprintf(os.Stderr, "devcli: %v\n", err)
		return 1
	}

	switch args[0] {
	case "sync":
		if err := remote.TraefikSync(cfg); err != nil {
			fmt.Fprintf(os.Stderr, "devcli: %v\n", err)
			return 1
		}
	case "status":
		if err := remote.TraefikStatus(cfg); err != nil {
			fmt.Fprintf(os.Stderr, "devcli: %v\n", err)
			return 1
		}
	case "show":
		remote.TraefikShow(cfg)
	default:
		fmt.Fprintf(os.Stderr, "unknown traefik action %q (use sync, status, show)\n", args[0])
		return 2
	}
	return 0
}

func cmdRemoteLiveKit(args []string) int {
	if len(args) == 0 {
		fmt.Fprintln(os.Stderr, "usage: devcli remote livekit <sync|status>")
		return 2
	}
	switch args[0] {
	case "sync":
		fs := flag.NewFlagSet("remote livekit sync", flag.ExitOnError)
		repo := fs.String("repo", "", "bedrud repo root")
		_ = fs.Parse(args[1:])

		cfg, _, err := loadRemoteConfig(*repo)
		if err != nil {
			fmt.Fprintf(os.Stderr, "devcli: %v\n", err)
			return 1
		}
		if err := remote.LiveKitSync(cfg); err != nil {
			fmt.Fprintf(os.Stderr, "devcli: %v\n", err)
			return 1
		}
		return 0
	case "status":
		fs := flag.NewFlagSet("remote livekit status", flag.ExitOnError)
		repo := fs.String("repo", "", "bedrud repo root")
		_ = fs.Parse(args[1:])

		cfg, _, err := loadRemoteConfig(*repo)
		if err != nil {
			fmt.Fprintf(os.Stderr, "devcli: %v\n", err)
			return 1
		}
		ok, err := remote.LiveKitStatus(cfg)
		if err != nil {
			fmt.Fprintf(os.Stderr, "devcli: %v\n", err)
			return 1
		}
		if !ok {
			return 1
		}
		return 0
	default:
		fmt.Fprintf(os.Stderr, "unknown livekit action %q (use sync, status)\n", args[0])
		return 2
	}
}

func cmdRemoteProvision(args []string) int {
	fs := flag.NewFlagSet("remote provision", flag.ExitOnError)
	repo := fs.String("repo", "", "bedrud repo root")
	force := fs.Bool("force", false, "reinstall binaries and regenerate WireGuard keys")
	skipLocalWG := fs.Bool("skip-local-wg", false, "do not write local WireGuard client config")
	_ = fs.Parse(args)

	cfg, _, err := loadRemoteConfig(*repo)
	if err != nil {
		fmt.Fprintf(os.Stderr, "devcli: %v\n", err)
		return 1
	}
	if err := remote.Provision(cfg, remote.ProvisionOptions{Force: *force, SkipLocalWG: *skipLocalWG || cfg.UsesSSHTunnel()}); err != nil {
		fmt.Fprintf(os.Stderr, "devcli: %v\n", err)
		return 1
	}
	return 0
}

func cmdRemoteRun(args []string) int {
	fs := flag.NewFlagSet("remote run", flag.ExitOnError)
	timestamps := fs.Bool("timestamps", false, "prefix log lines with time")
	autoStop := fs.Bool("yes", false, "stop processes on dev ports without prompting")
	repo := fs.String("repo", "", "bedrud repo root")
	skipTunnel := fs.Bool("no-tunnel", false, "skip tunnel up")
	skipWG := fs.Bool("no-wg", false, "alias for --no-tunnel")
	skipTraefik := fs.Bool("no-traefik", false, "skip traefik sync")
	skipLiveKit := fs.Bool("no-livekit", false, "skip remote livekit config sync")
	_ = fs.Parse(args)

	cfg, repoRoot, err := loadRemoteConfig(*repo)
	if err != nil {
		fmt.Fprintf(os.Stderr, "devcli: %v\n", err)
		return 1
	}

	if err := ports.PrepareRemoteDev(repoRoot, *autoStop); err != nil {
		fmt.Fprintf(os.Stderr, "devcli: %v\n", err)
		return 1
	}

	if !*skipLiveKit {
		logfmt.Println("devcli", "deploying LiveKit to server (local runs api + web only)")
		if err := remote.LiveKitSync(cfg); err != nil {
			fmt.Fprintf(os.Stderr, "devcli: livekit: %v\n", err)
			return 1
		}
		if err := remote.RequireRemoteLiveKit(cfg); err != nil {
			fmt.Fprintf(os.Stderr, "devcli: %v\n", err)
			return 1
		}
	}

	if !*skipTunnel && !*skipWG {
		switch {
		case cfg.UsesSSHTunnel():
			logfmt.Println("devcli", "starting livekit SSH tunnel (before api)")
			if err := remote.TunnelEnsureLiveKit(cfg); err != nil {
				fmt.Fprintf(os.Stderr, "devcli: livekit tunnel: %v\n", err)
				return 1
			}
		case cfg.UsesDevTunnel():
			logfmt.Println("devcli", "starting devtunnel (before api)")
			if err := remote.DevTunnelEnsureAgent(cfg); err != nil {
				fmt.Fprintf(os.Stderr, "devcli: devtunnel: %v\n", err)
				return 1
			}
			if err := remote.TunnelUp(cfg); err != nil {
				fmt.Fprintf(os.Stderr, "devcli: devtunnel: %v\n", err)
				return 1
			}
			if err := remote.WaitDevTunnelReady(cfg, 30*time.Second); err != nil {
				fmt.Fprintf(os.Stderr, "devcli: devtunnel: %v\n", err)
				return 1
			}
		case cfg.UsesWireGuard():
			logfmt.Println("devcli", "starting userspace WireGuard tunnel (before api)")
			if err := remote.WireGuardEnsureClientConfig(cfg); err != nil {
				fmt.Fprintf(os.Stderr, "devcli: wireguard: %v\n", err)
				return 1
			}
			if err := remote.TunnelUp(cfg); err != nil {
				fmt.Fprintf(os.Stderr, "devcli: wireguard: %v\n", err)
				return 1
			}
			if err := remote.WaitWireGuardReady(cfg, 30*time.Second); err != nil {
				fmt.Fprintf(os.Stderr, "devcli: wireguard: %v\n", err)
				return 1
			}
		}
	}

	r, err := runner.New(runner.Options{
		RepoRoot:   repoRoot,
		Timestamps: *timestamps,
		AutoStop:   *autoStop,
		Remote:     cfg,
	})
	if err != nil {
		fmt.Fprintf(os.Stderr, "devcli: %v\n", err)
		return 1
	}

	ctx := context.Background()
	if err := r.RunWithHook(ctx, func(hookCtx context.Context) error {
		if err := remote.WaitLocalBackends(hookCtx, cfg, 90*time.Second); err != nil {
			return fmt.Errorf("local backends: %w", err)
		}
		if !*skipTunnel && !*skipWG {
			if cfg.UsesSSHTunnel() {
				logfmt.Println("devcli", "starting backend SSH tunnel (local backends are up)")
				if err := remote.TunnelEnsureBackends(cfg); err != nil {
					return fmt.Errorf("tunnel: %w", err)
				}
			}
			if err := remote.WaitRemoteBackends(hookCtx, cfg, 30*time.Second); err != nil {
				return fmt.Errorf("remote backends: %w", err)
			}
		}
		if !*skipTraefik {
			if err := remote.TraefikSync(cfg); err != nil {
				return fmt.Errorf("traefik: %w", err)
			}
		}
		if err := remote.VerifyDevRemoteReady(cfg, remote.ReadyOptions{
			RequireTunnel:  !*skipTunnel && !*skipWG,
			RequireTraefik: !*skipTraefik,
		}); err != nil {
			return err
		}
		logfmt.Println("devcli", fmt.Sprintf("ready → %s (server LiveKit + local api/web verified)", cfg.URLs.PublicBase))
		return nil
	}); err != nil {
		fmt.Fprintf(os.Stderr, "devcli: %v\n", err)
		return 1
	}
	return 0
}

func printRemoteUsage() {
	fmt.Println(`Remote debug — LiveKit on server, api/web local over tunnel + Traefik

Usage:
  devcli remote <command>

Commands:
  provision [--force]            Bootstrap fresh Debian 13 (Traefik + LiveKit; WG optional)
  status                         Health-check all services (SSH, tunnel, LiveKit, api/web, Traefik, public)
  ssh [--] [command...]          SSH to debug server (no args = interactive)
  tunnel up|down|status|deploy   Manage devtunnel / ssh / wireguard per remote-debug.yaml
  wg up|down|status|sync         WireGuard netstack (legacy)
  traefik sync|status|show       Sync/show Traefik dynamic routes on server
  livekit sync|status            Push or check LiveKit on server (service, HTTP, TURN/TLS, firewall)
  run [--yes] [--no-tunnel] [--no-traefik] [--no-livekit]
                                 local api/web, then tunnel + traefik/livekit sync

Tunnel modes (server/remote-debug.yaml):
  devtunnel   Outbound TCP mux to devcli agent on server (recommended)
  ssh         Rootless SSH -R/-L port forwards (legacy)
  wireguard   Go netstack WireGuard (legacy)

Config:
  server/remote-debug.yaml  (copy from server/remote-debug.yaml.example)
  server/.env               (SSH — copy from server/.env.example)

Examples:
  devcli remote provision
  devcli remote tunnel up          # ssh mode — no root
  devcli remote traefik sync
  devcli remote run --yes
  devcli remote status`)
}