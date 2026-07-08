#!/usr/bin/env bash
# Bedrud local dev port map (predictable 7070+ range).
# Sourced by Makefile helpers and scripts/dev-stop-all.sh.

DEV_PORT_WEB=7070        # Vite / TanStack Start (make dev-web)
DEV_PORT_API=7071        # Go API (make dev-server-hot)
DEV_PORT_LIVEKIT=7072    # LiveKit HTTP/API (make dev-livekit)
DEV_PORT_LIVEKIT_RTC=7073
DEV_PORT_DEVTOOLS=7074   # TanStack DevTools event bus
DEV_PORT_SITE=7075       # Astro site (make dev-site)
DEV_PORT_TURN_UDP=7076
DEV_PORT_TURN_TLS=7077
DEV_PORT_RTC_START=7080  # WebRTC media (UDP)
DEV_PORT_RTC_END=7099

DEV_TCP_PORTS=(
  "$DEV_PORT_WEB"
  "$DEV_PORT_API"
  "$DEV_PORT_LIVEKIT"
  "$DEV_PORT_LIVEKIT_RTC"
  "$DEV_PORT_DEVTOOLS"
  "$DEV_PORT_SITE"
  "$DEV_PORT_TURN_UDP"
  "$DEV_PORT_TURN_TLS"
)