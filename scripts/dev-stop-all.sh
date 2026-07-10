#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEVCLI="${ROOT}/apps/dev/devcli/bin/devcli"

if [ -x "$DEVCLI" ]; then
  exec "$DEVCLI" stop
fi

echo "❌ devcli not built — run: make build-devcli" >&2
exit 1