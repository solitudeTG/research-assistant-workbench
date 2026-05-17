$ErrorActionPreference = "Stop"

$scriptsRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent $scriptsRoot

$startScript = Get-Content -Raw -Path (Join-Path $repoRoot "start.ps1")
$dockerfile = Get-Content -Raw -Path (Join-Path $repoRoot "Dockerfile")
$composeFile = Get-Content -Raw -Path (Join-Path $repoRoot "compose.yaml")
$rebuildScriptPath = Join-Path $scriptsRoot "rebuild-dev.ps1"
$devComposeScriptPath = Join-Path $scriptsRoot "dev-compose.ps1"

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

if (-not (Test-Path $devComposeScriptPath)) {
    throw "scripts/dev-compose.ps1 must centralize dev compose environment and port selection."
}

$devComposeScript = Get-Content -Raw -Path $devComposeScriptPath
if ($devComposeScript -notmatch "Resolve-AppHostPort") {
    throw "scripts/dev-compose.ps1 must expose Resolve-AppHostPort for host port fallback."
}

if ($devComposeScript -notmatch "Get-AppPublishedHostPort") {
    throw "scripts/dev-compose.ps1 must reuse an existing published app port before probing fallbacks."
}

if ($composeFile -notmatch '\$\{APP_HOST_PORT:-8080\}:8080') {
    throw "compose.yaml must map the app port through APP_HOST_PORT with a default of 8080."
}

if ($startScript -notmatch "Resolve-AppHostPort" -or $rebuildScript -notmatch "Resolve-AppHostPort") {
    throw "start and rebuild scripts must resolve APP_HOST_PORT before running docker compose."
}

if ($startScript -match "localhost:8080") {
    throw "start.ps1 must not hard-code localhost:8080; use the resolved host port."
}

if ($dockerfile -notmatch "--mount=type=cache,target=/root/.m2") {
    throw "Dockerfile must use a BuildKit Maven cache mount for rebuilds."
}

Write-Host "Dev start contract: ok"
