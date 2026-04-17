$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $repoRoot

function Test-CommandExists {
    param([string]$Name)
    return $null -ne (Get-Command $Name -ErrorAction SilentlyContinue)
}

function Import-DotEnv {
    param([string]$Path)
    if (-not (Test-Path $Path)) {
        return
    }

    Get-Content $Path | ForEach-Object {
        if ([string]::IsNullOrWhiteSpace($_) -or $_.TrimStart().StartsWith("#")) {
            return
        }

        $parts = $_ -split "=", 2
        if ($parts.Count -eq 2 -and [string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($parts[0]))) {
            [Environment]::SetEnvironmentVariable($parts[0], $parts[1])
            Set-Item -Path "env:$($parts[0])" -Value $parts[1]
        }
    }
}

function Wait-ForDatabase {
    param([int]$Attempts = 30)
    for ($attempt = 1; $attempt -le $Attempts; $attempt++) {
        & docker compose exec -T db pg_isready -U postgres -d research_assistant *> $null
        if ($LASTEXITCODE -eq 0) {
            return
        }
        Start-Sleep -Seconds 2
    }
    throw "PostgreSQL did not become ready in time."
}

if (-not (Test-CommandExists "docker")) {
    throw "Docker is not installed or not on PATH. Install Docker Desktop first."
}

Import-DotEnv (Join-Path $repoRoot ".env")

if ([string]::IsNullOrWhiteSpace($env:AI_DASHSCOPE_API_KEY)) {
    throw "AI_DASHSCOPE_API_KEY is missing. Set it in your environment or in .env."
}

if (-not (Test-CommandExists "mvn")) {
    $fallbackMaven = "D:\apache-maven-3.9.11\bin\mvn.cmd"
    if (Test-Path $fallbackMaven) {
        $env:PATH = "D:\apache-maven-3.9.11\bin;$env:PATH"
    } else {
        throw "Maven was not found on PATH and fallback Maven was not found at D:\apache-maven-3.9.11\bin\mvn.cmd."
    }
}

$mvnCommandInfo = Get-Command "mvn.cmd" -ErrorAction SilentlyContinue
if (-not $mvnCommandInfo) {
    $mvnCommandInfo = Get-Command "mvn" -ErrorAction SilentlyContinue
}
if (-not $mvnCommandInfo) {
    throw "Maven command resolution failed even though Maven exists on PATH."
}
$mvnCommand = $mvnCommandInfo.Source

$mavenRepo = Join-Path $repoRoot ".m2\repository"
New-Item -ItemType Directory -Force -Path $mavenRepo | Out-Null

Write-Host "Starting pgvector database..."
docker compose up -d db

Write-Host "Waiting for database health..."
Wait-ForDatabase

Write-Host "Starting Spring Boot application..."
& $mvnCommand "-Dmaven.repo.local=$mavenRepo" "spring-boot:run"
