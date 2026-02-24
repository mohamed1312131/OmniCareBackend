$ErrorActionPreference = "Stop"

$envFile = Join-Path $PSScriptRoot "dev.env.ps1"

if (-not (Test-Path $envFile)) {
  Write-Host "Missing dev.env.ps1" -ForegroundColor Yellow
  Write-Host "Copy dev.env.ps1.example to dev.env.ps1 and fill in values." -ForegroundColor Yellow
  exit 1
}

. $envFile

if ($env:GOOGLE_CLIENT_ID) { $env:GOOGLE_CLIENT_ID = $env:GOOGLE_CLIENT_ID.Trim() }
if ($env:GOOGLE_CLIENT_SECRET) { $env:GOOGLE_CLIENT_SECRET = $env:GOOGLE_CLIENT_SECRET.Trim() }
if ($env:JWT_SECRET) { $env:JWT_SECRET = $env:JWT_SECRET.Trim() }

$listener = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
if ($listener) {
  Write-Host "Port 8080 is already in use (PID $($listener.OwningProcess)). Stop the existing process or change server.port." -ForegroundColor Yellow
  exit 1
}

docker compose up -d

mvn spring-boot:run
