$ErrorActionPreference = "Stop"

$scriptsRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent $scriptsRoot

Set-Location $repoRoot

. (Join-Path $scriptsRoot "dev-compose.ps1")
Import-DotEnv (Join-Path $repoRoot ".env")

if (-not (Test-CommandExists "docker")) {
    throw "Docker is not installed or not on PATH. Install Docker Desktop first."
}

$appHostPort = Resolve-AppHostPort

Write-Host "Rebuilding and starting research assistant containers..."
docker compose up --build -d
if ($LASTEXITCODE -ne 0) {
    throw "Docker Compose rebuild failed. Check Docker network access, image pull logs, and compose output above."
}

$appHostPort = Get-AppPublishedHostPort -FallbackPort $appHostPort
Write-Host "Rebuild requested. UI: http://localhost:$appHostPort"
Write-Host "Run .\scripts\start-dev.cmd for later starts without rebuilding."
