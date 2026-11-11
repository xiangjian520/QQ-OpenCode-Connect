#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if docker info >/dev/null 2>&1; then
  docker compose exec opencode opencode auth login
  docker compose restart opencode
elif command -v pkexec >/dev/null 2>&1 && [[ -n "${DISPLAY:-}${WAYLAND_DISPLAY:-}" ]]; then
  pkexec docker compose exec opencode opencode auth login
  pkexec docker compose restart opencode
else
  sudo docker compose exec opencode opencode auth login
  sudo docker compose restart opencode
fi
