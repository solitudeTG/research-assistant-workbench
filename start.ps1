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

function Wait-ForAppHealth {
    param([int]$Attempts = 30)
    for ($attempt = 1; $attempt -le $Attempts; $attempt++) {
        try {
            $response = Invoke-RestMethod http://localhost:8080/actuator/health -TimeoutSec 5
            if ($response.status -eq "UP") {
                return
            }
        } catch {
        }
        Start-Sleep -Seconds 2
    }
    Write-Host "Application did not become healthy in time. Recent app logs:"
    docker compose logs --tail 100 app
    throw "Research Assistant application did not become healthy in time."
}

if (-not (Test-CommandExists "docker")) {
    throw "Docker is not installed or not on PATH. Install Docker Desktop first."
}

Import-DotEnv (Join-Path $repoRoot ".env")

if ([string]::IsNullOrWhiteSpace($env:AI_API_KEY)) {
    throw "AI_API_KEY is missing. Set it in your environment or in .env."
}

Write-Host "Building and starting research assistant containers..."
docker compose up --build -d

Write-Host "Waiting for database health..."
Wait-ForDatabase

Write-Host "Waiting for application health..."
Wait-ForAppHealth

Write-Host "Research Assistant is ready."
Write-Host "UI: http://localhost:8080"
Write-Host "Logs: docker compose logs -f app"
