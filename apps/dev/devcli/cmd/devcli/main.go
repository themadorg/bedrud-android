package main

import (
	"context"
	"flag"
	"fmt"
	"os"
	"strings"
	"time"

	"bedrud/devcli/internal/ports"
	"bedrud/devcli/internal/remote"
	"bedrud/devcli/internal/root"
	"bedrud/devcli/internal/runner"
	"bedrud/devcli/internal/status"
)

func main() {
	os.Exit(run(os.Args[1:]))
}

func run(args []string) int {
	if len(args) == 0 {
		args = []string{"run"}
	}

	switch args[0] {
	case "run":
		return cmdRun(args[1:])
	case "status":
		return cmdStatus(args[1:])
	case "stop":
		return cmdStop()
	case "remote":
		return cmdRemote(args[1:])
	case "tunnel-server":
		return cmdTunnelServer(args[1:])
	case "help", "-h", "--help":
		printUsage()
		return 0
	default:
		fmt.Fprintf(os.Stderr, "unknown command %q\n\n", args[0])
		printUsage()
		return 2
	}
}

func cmdRun(args []string) int {
	fs := flag.NewFlagSet("run", flag.ExitOnError)
	timestamps := fs.Bool("timestamps", false, "prefix log lines with time (docker compose --timestamps)")
	autoStop := fs.Bool("yes", false, "stop processes on dev ports without prompting")
	repo := fs.String("repo", "", "bedrud repo root (auto-detected)")
	_ = fs.Parse(args)

	r, err := runner.New(runner.Options{
		RepoRoot:   *repo,
		Timestamps: *timestamps,
		AutoStop:   *autoStop,
	})
	if err != nil {
		fmt.Fprintf(os.Stderr, "devcli: %v\n", err)
		return 1
	}

	if err := r.Run(context.Background()); err != nil {
		fmt.Fprintf(os.Stderr, "devcli: %v\n", err)
		return 1
	}
	return 0
}

func cmdStatus(args []string) int {
	fs := flag.NewFlagSet("status", flag.ExitOnError)
	remoteMode := fs.Bool("remote", false, "check local api/web only (LiveKit on server)")
	repo := fs.String("repo", "", "bedrud repo root (for --remote)")
	_ = fs.Parse(args)

	if *remoteMode {
		cfg, _, err := loadRemoteConfig(*repo)
		if err != nil {
			fmt.Fprintf(os.Stderr, "devcli: %v\n", err)
			return 1
		}
		report := status.CheckLocalRemote(cfg.Local.WebPort, cfg.Local.APIPort)
		if !status.PrintLocalRemote(report) {
			return 1
		}
		return 0
	}

	report := status.CheckLocal()
	if !status.PrintLocal(report) {
		return 1
	}
	return 0
}

func cmdStop() int {
	repo, err := root.Find("")
	if err != nil {
		fmt.Fprintf(os.Stderr, "devcli: %v\n", err)
		return 1
	}

	var stopped []string

	if ports.StopOrchestrator(repo) {
		stopped = append(stopped, "devcli")
		time.Sleep(200 * time.Millisecond)
	}

	if devStopped, err := ports.StopDev(repo); err != nil {
		fmt.Fprintf(os.Stderr, "devcli: stop failed: %v\n", err)
		return 1
	} else if devStopped {
		stopped = append(stopped, "api/web/livekit")
	}

	if infraStopped, label, err := remote.StopInfra(repo); err != nil {
		fmt.Fprintf(os.Stderr, "devcli: stop tunnel failed: %v\n", err)
		return 1
	} else if infraStopped {
		stopped = append(stopped, "tunnel ("+label+")")
	}

	if len(stopped) > 0 {
		fmt.Printf("✅ Stopped: %s\n", strings.Join(stopped, ", "))
		return 0
	}
	fmt.Println("ℹ️  No Bedrud dev processes found")
	return 0
}

func printUsage() {
	fmt.Println(`bedrud devcli — run the local dev stack with multiplexed logs

Usage:
  devcli [command]

Commands:
  run [--timestamps] [--yes] [--repo PATH]   Start livekit + api (air) + web
  status [--remote]                            Check local health (api/web only with --remote)
  remote <command>                           Remote debug server (SSH, WG, Traefik)
  stop                                         Stop all dev processes
  help                                         Show this help

Examples:
  devcli
  devcli run --timestamps
  devcli run --yes
  devcli status
  devcli remote run --yes
  devcli remote status
  devcli remote livekit status
  devcli stop`)
}