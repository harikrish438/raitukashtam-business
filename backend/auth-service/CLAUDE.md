# auth-service — Claude Context

Identity/auth domain service (OAuth2 Authorization Server, JWT issuance,
OTP/2FA, password + Google login). See the repo-root `CLAUDE.md` first for
shared context (this repo's standalone architecture, the per-service
directory convention) — this file only covers what's specific to
`auth-service`.

## Overview (port 8080, debug 5008 in dev)
- PostgreSQL DB: `auth-service-db` container `auth-postgres` (host port
  5435 via this directory's `docker-compose.yml`)
- Issues and validates its own JWTs (RSA keypair) — does NOT depend on
  `jwt-library` (that's consumed by `product-service` to validate tokens
  this service issues; auth-service itself has no GitHub Packages
  dependency and needs no `settings.xml`/`PACKAGES_READ_TOKEN`)
- Redis: rate-limiting (`RateLimiterService`) and OTP storage
  (`OTPService`) — a real functional dependency, not just a session store
- Secrets (JWT signing keypair, DB password, Redis password, mail
  password, reCAPTCHA secret, 2Factor API key) come from Vault under KV
  path `secret/auth-service`, seeded by the repo root's `vault-init.sh`
- No Eureka, no config-server — both were dropped when this service moved
  here (see repo-root `CLAUDE.md`); `product-service` reaches this service
  by its plain Docker Compose hostname (`http://auth-service:8080`)

Key env-var-driven config (see `.env.example`): `AUTH_DB_PASSWORD`,
`REDIS_PASSWORD`, `MAIL_USERNAME`, `GOOGLE_CLIENT_ID`, `RECAPTCHA_SITE_KEY`,
`WEB_CLIENT_REDIRECT_URI`, `CORS_ALLOWED_ORIGINS`, `APP_BASE_URL`.

## Files in this directory
```
backend/auth-service/
├── Dockerfile                      # Self-contained build (no GitHub Packages)
├── docker-compose.yml              # DEV stack (default: docker compose up)
├── docker-compose.test.yml         # TEST stack
├── docker-compose.prod.yml         # PROD stack
├── .env.example                    # Dev secrets template → copy to .env
├── .env.test.example               # Test secrets template → copy to .env.test
└── .env.prod.example               # Prod secrets template → copy to .env.prod
```

## Running Docker Compose

All commands run from this directory (`backend/auth-service/`). The repo
root's own shared-infra stack (Vault + Redis + `raitukashtam-network`)
must already be running first — see repo-root `CLAUDE.md`.

```sh
cp .env.example .env               # fill in AUTH_DB_PASSWORD, REDIS_PASSWORD, etc.
docker compose up -d --build       # DEV
# or: docker compose -f docker-compose.test.yml --env-file .env.test up -d --build   # TEST
# or: docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build   # PROD
```

`AUTH_DB_PASSWORD`, `REDIS_PASSWORD`, and `VAULT_TOKEN` must match the
values in the repo root's own `.env` — the root stack's `vault-init.sh`
seeds Vault from the root `.env`, and `auth-postgres`/`redis` here are
started directly from this directory's own `.env`, so a mismatch between
the two means auth-service authenticates against a DB/Redis password
Vault doesn't actually have.

## History

This service's git history (52 commits) was preserved from the
`raitukashtam` platform repo via `git subtree split` + `git subtree add`
(2026-08-27), so `git log`/`git blame` on files under this directory
predate this repo's own first commit. The platform repo's own copy of
`backend/auth-service` was intentionally left in place, untouched — this
repo does not depend on it and never modifies it.
