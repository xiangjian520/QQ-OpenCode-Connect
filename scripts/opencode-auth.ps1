$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")
docker compose exec opencode opencode auth login
docker compose restart opencode
