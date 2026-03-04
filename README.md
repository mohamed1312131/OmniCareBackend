# Omnicare API (Dev Setup)

## Quickstart (recommended)

1) Start dependencies + API with one command:

```powershell
Copy-Item dev.env.ps1.example dev.env.ps1
# Edit dev.env.ps1 and fill in values
./run-dev.ps1
```

`dev.env.ps1` contains local secrets and is ignored by git.

## Prerequisites
- Java 17+
- Maven 3.9+
- Docker Desktop

## 1) Start Postgres + Redis
From the repo root:

```powershell
docker compose up -d
```

The API expects:
- Postgres: `localhost:5432` (db: `app`, user: `postgres`, pass: `postgres`)
- Redis: `localhost:6379`

## 2) Set required environment variables

### 2.1 JWT signing secret (required)
The API signs/verifies JWTs using `JWT_SECRET`. Set it once as a **permanent user env var**:

```powershell
# Generate a strong random secret (Base64)
$bytes = New-Object byte[] 64
[Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
$secret = [Convert]::ToBase64String($bytes)

# Persist for your Windows user
[Environment]::SetEnvironmentVariable("JWT_SECRET", $secret, "User")
```

Close/reopen your terminal (or restart VS Code), then verify:

```powershell
$env:JWT_SECRET
```

### 2.2 Google OAuth2 credentials (required for login)
You need a Google OAuth2 Client.

Set these env vars (you can also persist them with the same `SetEnvironmentVariable(..., "User")` approach):

```powershell
$env:GOOGLE_CLIENT_ID="<your-client-id>"
$env:GOOGLE_CLIENT_SECRET="<your-client-secret>"
```

These are read by `application.properties` and mapped into Spring Security's Google OAuth2 client registration.
If they are not set, the API will not be able to start the Google OAuth2 login flow.

Google Console requirements (OAuth client):
- Authorized redirect URI:
  - `http://localhost:8080/login/oauth2/code/google`

## 3) Run the API

```powershell
mvn spring-boot:run
```

API base URL:
- `http://localhost:8080`

## 4) Test the auth flow

### 4.1 Start Google login
Open:
- `http://localhost:8080/oauth2/authorization/google`

Google is configured to always show:
- account picker
- consent screen

### 4.2 After successful login
The backend redirects to:
- Backend-only (default in this repo right now): `http://localhost:8080/auth/dev/callback?token=...`
- Frontend flow: `http://localhost:5173/auth/callback?token=...`

To switch between these, update `app.oauth2.redirect-uri` in `src/main/resources/application.properties`.

Your frontend should extract the `token` query param and store it.

### 4.3 Call secured endpoints
Use:
- `Authorization: Bearer <token>`

Example:
- `GET http://localhost:8080/api/account/me`

### 4.4 Logout
- `POST http://localhost:8080/api/auth/logout`
(with `Authorization: Bearer <token>`)

## Notes
- Custom config lives in `src/main/resources/application.properties` under the `app.*` keys.
- If you change `JWT_SECRET`, previously issued JWTs become invalid.

## Troubleshooting
- If `users` stays empty after a successful Google login, ensure Google credentials are set and re-run the login flow. The API persists users during the OAuth2/OIDC user-info step.
