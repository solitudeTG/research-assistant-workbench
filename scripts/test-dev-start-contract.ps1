$ErrorActionPreference = "Stop"

$scriptsRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent $scriptsRoot

$startScript = Get-Content -Raw -Path (Join-Path $repoRoot "start.ps1")
$dockerfile = Get-Content -Raw -Path (Join-Path $repoRoot "Dockerfile")
$rebuildScriptPath = Join-Path $scriptsRoot "rebuild-dev.ps1"

if ($startScript -match "--build") {
    throw "start.ps1 must not force Docker image rebuilds. Use scripts/rebuild-dev.ps1 for rebuilds."
}

if (-not (Test-Path $rebuildScriptPath)) {
    throw "scripts/rebuild-dev.ps1 must exist for explicit rebuilds."
}

$rebuildScript = Get-Content -Raw -Path $rebuildScriptPath
if ($rebuildScript -notmatch "--build") {
    throw "scripts/rebuild-dev.ps1 must explicitly run docker compose with --build."
}

if ($dockerfile -notmatch "--mount=type=cache,target=/root/.m2") {
    throw "Dockerfile must use a BuildKit Maven cache mount for rebuilds."
}

Write-Host "Dev start contract: ok"
