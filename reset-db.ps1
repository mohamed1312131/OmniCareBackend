param(
  [switch]$Force,
  [string]$ProjectName = "omnicare"
)

$ErrorActionPreference = 'Stop'

if (-not $Force) {
  Write-Host "This will DELETE your docker volumes for the dev database (destructive)." -ForegroundColor Yellow
  Write-Host "Re-run with -Force to proceed." -ForegroundColor Yellow
  exit 1
}

if (-not $env:COMPOSE_PROJECT_NAME) {
  $env:COMPOSE_PROJECT_NAME = $ProjectName
}

Write-Host "Using COMPOSE_PROJECT_NAME=$($env:COMPOSE_PROJECT_NAME)" -ForegroundColor Cyan

Write-Host "Stopping containers + deleting volumes..." -ForegroundColor Yellow
& docker compose down -v

Write-Host "Starting containers..." -ForegroundColor Yellow
& docker compose up -d

Write-Host "Done. Database volume reset completed." -ForegroundColor Green
