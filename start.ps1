$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $repoRoot

. (Join-Path $repoRoot "scripts\dev-compose.ps1")

if (-not (Test-CommandExists "docker")) {
    throw "Docker is not installed or not on PATH. Install Docker Desktop first."
}

Import-DotEnv (Join-Path $repoRoot ".env")

if ([string]::IsNullOrWhiteSpace($env:AI_API_KEY)) {
    throw "AI_API_KEY is missing. Set it in your environment or in .env."
}

$appHostPort = Resolve-AppHostPort

Write-Host "Starting research assistant containers..."
docker compose up -d
if ($LASTEXITCODE -ne 0) {
    throw "Docker Compose startup failed during container startup. If the app image is stale or missing, run .\scripts\rebuild-dev.cmd."
}

Write-Host "Waiting for database health..."
Wait-ForDatabase

Write-Host "Waiting for application health..."
$appHostPort = Get-AppPublishedHostPort -FallbackPort $appHostPort
Wait-ForAppHealth -Port $appHostPort

Write-Host "Research Assistant is ready."
Write-Host "UI: http://localhost:$appHostPort"
Write-Host "Logs: docker compose logs -f app"
