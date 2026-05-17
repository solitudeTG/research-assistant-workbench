$ErrorActionPreference = "Stop"

$scriptsRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent $scriptsRoot

Set-Location $repoRoot

Write-Host "Rebuilding and starting research assistant containers..."
docker compose up --build -d
if ($LASTEXITCODE -ne 0) {
    throw "Docker Compose rebuild failed. Check Docker network access, image pull logs, and compose output above."
}

Write-Host "Rebuild requested. Run .\scripts\start-dev.cmd for later starts without rebuilding."
