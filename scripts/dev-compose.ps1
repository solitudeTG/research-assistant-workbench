$ErrorActionPreference = "Stop"

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

function Test-TcpPortAvailable {
    param([int]$Port)

    $listener = $null
    try {
        $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Any, $Port)
        $listener.Start()
        return $true
    } catch {
        return $false
    } finally {
        if ($null -ne $listener) {
            $listener.Stop()
        }
    }
}

function Resolve-AppHostPort {
    param(
        [int]$DefaultPort = 8080,
        [int[]]$FallbackPorts = @(18080, 18081, 18082, 28080)
    )

    if (-not [string]::IsNullOrWhiteSpace($env:APP_HOST_PORT)) {
        $configuredPort = 0
        if (-not [int]::TryParse($env:APP_HOST_PORT, [ref]$configuredPort) -or $configuredPort -lt 1 -or $configuredPort -gt 65535) {
            throw "APP_HOST_PORT must be a TCP port between 1 and 65535."
        }
        return $configuredPort
    }

    $publishedPort = Get-AppPublishedHostPort -FallbackPort 0
    if ($publishedPort -gt 0) {
        $env:APP_HOST_PORT = "$publishedPort"
        return $publishedPort
    }

    if (Test-TcpPortAvailable -Port $DefaultPort) {
        $env:APP_HOST_PORT = "$DefaultPort"
        return $DefaultPort
    }

    foreach ($candidate in $FallbackPorts) {
        if (Test-TcpPortAvailable -Port $candidate) {
            $env:APP_HOST_PORT = "$candidate"
            Write-Host "Host port $DefaultPort is not available; using APP_HOST_PORT=$candidate."
            return $candidate
        }
    }

    throw "No available app host port found. Set APP_HOST_PORT in .env, for example APP_HOST_PORT=18080."
}

function Get-AppPublishedHostPort {
    param([int]$FallbackPort)

    try {
        $published = & docker compose port app 8080 2>$null
    } catch {
        return $FallbackPort
    }

    if ($LASTEXITCODE -eq 0 -and -not [string]::IsNullOrWhiteSpace($published)) {
        $lastLine = @($published)[-1]
        if ($lastLine -match ":(\d+)$") {
            return [int]$Matches[1]
        }
    }

    return $FallbackPort
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
    param(
        [int]$Port,
        [int]$Attempts = 30
    )
    for ($attempt = 1; $attempt -le $Attempts; $attempt++) {
        try {
            $response = Invoke-RestMethod "http://localhost:$Port/actuator/health" -TimeoutSec 5
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
