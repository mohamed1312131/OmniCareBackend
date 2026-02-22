# Omnicare API (Dev Setup)

## Coworker setup (fast)

1) Get `dev.env.ps1` from the backend dev (shared privately).

2) Put it in the repo root (same folder as `run-dev.ps1`).

3) Run:

```powershell
./run-dev.ps1
```

## Important
- `dev.env.ps1` contains secrets. Do not commit it. It is ignored by git.

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

```powershell
$token = "<paste token>"
curl.exe -H "Authorization: Bearer $token" http://localhost:8080/api/account/me
```
