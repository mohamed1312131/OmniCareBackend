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

1) Open:
- `http://localhost:8080/oauth2/authorization/google`

2) After login, you will land on:
- `http://localhost:8080/auth/dev/callback?token=...`

3) Use the token to call APIs:

```powershell
$token = "<paste token>"
curl.exe -H "Authorization: Bearer $token" http://localhost:8080/api/account/me
```
