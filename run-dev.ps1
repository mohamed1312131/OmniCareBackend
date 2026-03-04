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

docker compose up -d

mvn spring-boot:run
