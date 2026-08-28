# Raitukashtam Business — Claude Context

## Project Summary

**raitukashtam-business** holds the business/domain services for the
Raitukashtam farmer-marketplace platform. It was split out of the
[raitukashtam](https://github.com/harikrish438/raitukashtam) platform repo
(2026-08-16) so domain services can be built, deployed, and redeployed
independently of platform infrastructure and of each other.

This file holds only what's shared across every business service in this
repo. Service-specific detail (ports, DB tables, endpoints, dependency
versions, local dev instructions) lives in that service's own
`backend/<service>/CLAUDE.md` — read it too whenever you're working inside
a specific service directory.

## Session Tracking

This repo is under active development toward a production deployment
(currently: auth-service + mycommunity-service on AWS) and work continues
across many sessions. `PROGRESS.md` at the repo root is the running log —
read it at the start of a session for context on what's already done and
what's still open, and update it (a new dated entry under "Sessions",
plus "Status"/"Open items" if they changed) before ending a session or at
a natural stopping point. This is default behavior for this repo — do it
without being asked.

## Convention for Business Services

Each service is fully self-contained under `backend/<service>/`:
- Source code, `Dockerfile`.
- All of its `docker-compose*.yml` and `.env*.example` files.
- Its own `CLAUDE.md` with service-specific context.

**Exception 1 — CI**: workflow files must live at repo-root
`.github/workflows/` because that's the only location GitHub Actions reads
from — not a per-service choice. Each service still gets its own
path-filtered workflow there (e.g. `auth-ci.yml` triggers only on
`backend/auth-service/**` changes).

**Exception 2 — shared infra**: `docker-compose.yml`/`.test.yml`/`.prod.yml`
and `.env*.example` also exist at the repo root, for infrastructure genuinely
shared by every service (Vault, Redis, the `raitukashtam-network` itself —
see below) rather than duplicating a Vault+Redis pair per service. Bring
this stack up first, before any individual service's stack.

Current services:
- `backend/mycommunity-service/` — see `backend/mycommunity-service/CLAUDE.md`
- `backend/auth-service/` — see `backend/auth-service/CLAUDE.md`

## This repo is fully standalone — no dependency on raitukashtam (the platform repo)

This repo owns all the runtime infrastructure its services need. It does
**not** register with, read from, or otherwise require the platform repo's
docker-compose stack to be running:
- **Vault + Redis** are run from this repo's own root `docker-compose.yml`
  (see `infra/vault/vault-init.sh` for how Vault gets seeded). Each
  service's own compose file joins them via the shared
  `raitukashtam-network`, which this repo's root compose *creates* — service
  compose files reference it as `external: true`.
- **No Eureka, no config-server.** Both were dropped (2026-08-27) when
  auth-service moved into this repo — with only two services, direct Docker
  Compose hostname resolution (`http://auth-service:8080`) replaces
  Eureka-based service discovery, and every config value that used to come
  from config-server is now a plain env var (see each service's
  `.env*.example`) or a Vault secret.
- **auth-service issues and validates its own JWTs directly** (Spring
  Authorization Server, RS256, its own JWKS at `/oauth2/jwks`).
  `mycommunity-service` validates those same JWTs using Spring's own
  `oauth2ResourceServer` support, fetching auth-service's JWKS live over
  the network at runtime — no shared secret, no library. **This repo has
  no build-time or runtime dependency on the platform repo at all** —
  `jwt-library` (the one remaining link) was removed 2026-08-28 (see
  `backend/mycommunity-service/CLAUDE.md`'s History section for why: it
  signed/validated with HMAC256 against a shared secret, but auth-service
  had since moved to RS256/JWKS, so it could never actually have validated
  a real auth-service-issued token).

**Do not modify the platform repo (`raitukashtam`) as part of work in this
repo.** Its own copies of `backend/auth-service` and `backend/product-service`
(the platform repo's own directory names — unrenamed, untouched)
are known, intentional duplicates — left in place, untouched. This repo is
the one going to production for these two services; the platform repo's
copies and its own deploy pipeline are a separate, out-of-scope concern
unless a user explicitly asks otherwise.

## History

This repo was split out of the `raitukashtam` platform repo on 2026-08-16
so business/domain services could be built and deployed independently.
`auth-service` was added the same way on 2026-08-27, at which point this
repo also stopped depending on the platform repo's runtime stack entirely
(see above) — the AWS deployment target is just this repo's own
auth-service + mycommunity-service, not the platform repo's full stack.
`product-service` was renamed to `mycommunity-service` within this repo on
2026-08-28 (see `PROGRESS.md`) — the platform repo's own copy keeps its
original `product-service` name, untouched. Later the same day,
`jwt-library` was removed from `mycommunity-service` (see above) — this
repo has had no dependency on the platform repo whatsoever, build-time or
runtime, since then.
