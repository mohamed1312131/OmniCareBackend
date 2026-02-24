# Omnicare API (Dev Setup)

## Setup (fast)

1) Get `dev.env.ps1` from the backend dev (shared privately).

2) Put it in the repo root (same folder as `run-dev.ps1`).

3) Run:

```powershell
.\run-dev.ps1
```

## Important
- `dev.env.ps1` contains secrets. Do not commit it. It is ignored by git.
- Email verification links use `VERIFY_BASE_URL`. In production set it to your deployed backend URL (e.g. `https://api.yoursite.com`).

## Test login

1) Open the dev UI:
- `http://localhost:8080/dev/index.html`

2) Click **Login with Google**.

3) After login, you will land on:
- `http://localhost:8080/auth/dev/callback?token=...`

4) Click **Open Dev UI with this token**.

5) In the dev UI you should see:
- `/api/account/me` returns your email + name and `hasPassword: false`
- JWT payload contains `roles: ["PATIENT"]` and `registrationStatus: "PENDING_PASSWORD"`

6) Set an initial password in the dev UI (calls `POST /api/auth/set-initial-password`).

7) Re-login to refresh the JWT claims.

## Notes
- OTP is not implemented yet, so after setting a password your DB status becomes `PENDING_OTP` and you still won’t be `ACTIVE`.
- Most `/api/**` endpoints return `403` until the user becomes `ACTIVE`.

## (Optional) Backend-only test

### Email + password (no Google)

```powershell
$registerBody = @{ email = "test2@example.com"; name = "Test Two"; password = "password" } | ConvertTo-Json
try {
  Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/auth/register" -ContentType "application/json" -Body $registerBody | Out-Null
} catch {
}

# Check Mailtrap inbox and open the verification link (GET /api/auth/verify-email?token=...)

$loginBody = @{ email = "test2@example.com"; password = "password" } | ConvertTo-Json
$login = Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/auth/login" -ContentType "application/json" -Body $loginBody
$token = $login.token

if (-not $token) { throw "No token returned" }

$patchBody = @{ name = "Test Two Renamed" } | ConvertTo-Json
Invoke-RestMethod -Method Patch -Uri "http://localhost:8080/api/users/me" -ContentType "application/json" -Headers @{ Authorization = "Bearer $token" } -Body $patchBody
```

```powershell
$token = "<paste token>"
curl.exe -H "Authorization: Bearer $token" http://localhost:8080/api/account/me
```
