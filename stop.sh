#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
if docker info >/dev/null 2>&1; then
  docker compose stop mysql opencode
elif command -v pkexec >/dev/null 2>&1 && [[ -n "${DISPLAY:-}${WAYLAND_DISPLAY:-}" ]]; then
  pkexec docker compose stop mysql opencode
else
  sudo docker compose stop mysql opencode
fi
