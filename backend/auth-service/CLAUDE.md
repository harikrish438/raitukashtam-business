# auth-service — Claude Context

Identity/auth domain service (OAuth2 Authorization Server, JWT issuance,
OTP/2FA, password + Google login). See the repo-root `CLAUDE.md` first for
shared context (this repo's standalone architecture, the per-service
directory convention) — this file only covers what's specific to
`auth-service`.

## Overview (port 8080, debug 5008 in dev)
- PostgreSQL DB: `auth-service-db` container `auth-postgres` (host port
  5435 via this directory's `docker-compose.yml`)
- Issues and validates its own JWTs (RS256, its own JWKS at
  `/oauth2/jwks`) via Spring Authorization Server. `mycommunity-service`
  validates the same tokens using Spring's own `oauth2ResourceServer`
  support pointed at that JWKS endpoint — no shared library or secret
  (`jwt-library` was removed from this repo entirely, 2026-08-28; neither
  service has a GitHub Packages dependency any more)
- Redis: rate-limiting (`RateLimiterService`) and OTP storage
  (`OTPService`) — a real functional dependency, not just a session store
- Secrets (JWT signing keypair, DB password, Redis password, mail
  password, reCAPTCHA secret, 2Factor API key) come from Vault under KV
  path `secret/auth-service`, seeded by the repo root's `vault-init.sh`
- No Eureka, no config-server — both were dropped when this service moved
  here (see repo-root `CLAUDE.md`); `mycommunity-service` reaches this
  service by its plain Docker Compose hostname (`http://auth-service:8080`)

Key env-var-driven config (see `.env.example`): `AUTH_DB_PASSWORD`,
`REDIS_PASSWORD`, `MAIL_USERNAME`, `GOOGLE_CLIENT_ID`, `RECAPTCHA_SITE_KEY`,
`WEB_CLIENT_REDIRECT_URI`, `CORS_ALLOWED_ORIGINS`, `APP_BASE_URL`,
`PLATFORM_ADMIN_EMAIL`.

## Products, roles, and clients — onboarding is a data operation, not a code change

auth-service is multi-tenant at the "product" level: a `Product` is a
business-line-level tenant (e.g. `RAITUKASHTAM`, `MYCOMMUNITY`), each with
its own `Role`s and OAuth2 `Client`s (`Client.product`). Registering a new
product for a new app **does not need a migration, a `CommandLineRunner`
seeder, or any other code change** — `ProductController`/`RoleController`/
`ClientController` already expose exactly this as a `PLATFORM_ADMIN`-gated
REST API. `MYCOMMUNITY` itself was onboarded this way (2026-08-28) — a
first attempt used a bespoke migration + seeder class before realizing
that was unnecessary once a platform admin already exists (see
repo-root `PROGRESS.md` for the full story, including a latent bug this
surfaced: role lookups are scoped by *the calling OAuth2 client's own
product*, not any hardcoded default, which self-service signup flows must
respect once more than one product exists).

**One-time per environment — bootstrap the first `PLATFORM_ADMIN`** (skip
if one already exists): set `PLATFORM_ADMIN_EMAIL` in `.env`, register
that email via the public `POST /users/register`, then restart this
service once — `PlatformAdminSeeder` promotes it on that boot. This step
alone still needs a restart (a fresh environment has no admin token yet
to call anything with) — that's the one genuine chicken-and-egg case
here, not something an API can route around.

`raitukashtam@gmail.com` is the standing convention for this first
`PLATFORM_ADMIN` in every environment (dev/test/prod) — same account
`MAIL_USERNAME` already sends mail from. All three `.env*.example` files
default `PLATFORM_ADMIN_EMAIL` to it. This is just which email gets
promoted first, not a secret — additional platform admins can be added
any time afterward via `PATCH /users/{id}/platform-admin`, which any
existing platform admin can call (no restart needed for admin #2+, and
`is_platform_admin` has no uniqueness constraint — multiple platform
admins are fully supported).

**Onboarding a product (repeat any time, no restart, no deploy):**
1. Log in as the platform admin through the normal Authorization Code +
   PKCE flow (`GET /oauth2/authorize` → `/login` → `POST /oauth2/token`)
   to get a Bearer token — same flow the mobile/web clients use.
2. `POST /products` `{"code":"NEWPRODUCT","name":"New Product"}`
3. `POST /products/NEWPRODUCT/roles` `{"code":"CONSUMER","name":"Consumer"}`
   (required — `RoleService.assignDefaultRole` needs this before any
   signup/login flow can provision a membership into the product) and any
   other roles the product needs (e.g. `ADMIN`).
4. `POST /products/NEWPRODUCT/clients`
   `{"clientId":"newproduct-android","clientType":"ANDROID","redirectUris":["newproduct://callback"]}`
   for each OAuth2 client the new app needs.

All three endpoints are documented under the "Platform Admin" tag in the
running service's OpenAPI/Swagger UI (`/swagger-ui.html`).

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
