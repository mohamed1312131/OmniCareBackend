# OmniCare Backend (Spring Boot)

## Quick start (Windows)

1) Get `dev.env.ps1` (shared privately).

2) Put it in the repo root (same folder as `run-dev.ps1`).

3) Run:

```powershell
.\run-dev.ps1
```

## What starts

- Postgres (Docker)
- Redis (Docker)
- Spring Boot app on:
  - `http://localhost:8080`
  - API base: `http://localhost:8080/api`

## Notes

- `dev.env.ps1` contains secrets. **Do not commit it**.
- Email verification is OTP-based (`POST /api/auth/verify-email-otp`).
- Medication autocomplete for onboarding uses:
  - `GET /api/medications/search?q=asp`
  - This endpoint is public in dev so the signup flow can use it.

## Handy endpoints

- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/verify-email-otp`
- `POST /api/auth/set-phone`
- `POST /api/auth/request-phone-otp`
- `POST /api/auth/verify-phone-otp`
