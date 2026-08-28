# mycommunity-service — Claude Context

Community domain service for the MySociety mobile app (see
`github.com/harikrish438/mobile-apps`, `mysociety/` directory — a UI-only
Android prototype for apartment/gated-community management). Renamed from
`product-service` on 2026-08-28 — see repo-root `PROGRESS.md`. See the
repo-root `CLAUDE.md` first for shared context (the per-service directory
convention) — this file only covers what's specific to `mycommunity-service`.

## Overview (port 8081, debug 5007 in dev)
- PostgreSQL DB: `mycommunity-service-db` (host port 5434 via this
  directory's `docker-compose.yml`; port 5433 if run locally via
  `docker-compose.local-postgres.yml`)
- **No GitHub Packages / cross-repo build dependency at all** (see
  History below — jwt-library was removed 2026-08-28). `mvn`/`docker
  build` need nothing beyond the public Maven Central mirror.
- Validates JWTs the same way auth-service's own resource-server chain
  does: Spring's `oauth2ResourceServer` support, fetching auth-service's
  live JWKS (`${AUTH_SERVICE_URL}/oauth2/jwks`, RS256) at runtime — no
  shared secret, no library, no static key copy. `SecurityConfig` has no
  custom JWT beans; Spring Boot autoconfigures the `JwtDecoder` from the
  `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` property alone.
  A controller reads the caller's identity via
  `@AuthenticationPrincipal Jwt jwt` → `jwt.getSubject()` (the auth
  Identity UUID) — see `CommunityController`.
- Calling `auth-service` over the network (`http://auth-service:8080`) is
  **not currently needed** — Phase 1 never looks up user profile data
  from auth-service; admins/unit-owners type their own name/mobile
  directly into the mobile app's forms. A later phase that needs to
  resolve identityId → profile will need to reintroduce a
  `RestTemplate`/`WebClient` bean (removed along with the old
  product-service-era `/users/{id}` call in this rename).

## Domain model (Phase 1 — Community + unit-owner onboarding only)

- **Community**: name, totalUnits, street, area, district, state, pincode,
  landmark.
- **CommunityMember**: community (FK), name, unitNumber, mobileNumber
  (unique per community), role (`ADMIN`/`OWNER`), status
  (`INVITED`/`ACTIVE`), identityId (nullable — the auth Identity UUID,
  linked once that mobile number completes a real login; **there is no
  "activate" endpoint yet** — see Known gaps below).

Endpoints (`/api/v1/communities`, all require a Bearer JWT):

| Method | Path | Auth rule |
|---|---|---|
| POST | `/api/v1/communities` | Any authenticated caller; becomes the community's first ACTIVE ADMIN |
| GET | `/api/v1/communities/{id}` | Caller must be an ACTIVE member (any role) |
| POST | `/api/v1/communities/{id}/members` | Caller must be an ACTIVE ADMIN; 409 on duplicate mobile in the same community |
| GET | `/api/v1/communities/{id}/members` | Caller must be an ACTIVE member |
| DELETE | `/api/v1/communities/{id}/members/{memberId}` | Caller must be an ACTIVE ADMIN; 409 if it's the last ACTIVE ADMIN |

Later phases (dashboard aggregation, bills/payments, expenses, visitors,
announcements, amenities) are designed but not yet built — see
`~/.claude/plans/validated-rolling-pizza.md` in the session this was
planned in, or ask for the full data model if that file isn't available.

## Known gaps (not this service's to fix, but block real end-to-end use)

1. **The mobile app itself has no networking code yet**, so it can't
   actually get a Bearer token — not a backend gap any more.
   auth-service's backend side is done (`POST /otp/login`, added
   2026-08-28, plus the already-working `/oauth2/authorize`+`/oauth2/token`
   PKCE flow against the already-seeded `mycommunity-android` client, which
   has its own dedicated `MYCOMMUNITY` product — see auth-service's
   `PROGRESS.md` entry, 2026-08-28) — what's missing now is purely the
   Android app calling those endpoints (confirmed by reading every screen:
   `OtpActivity.verifyOtp()` is a `TODO` that just navigates to the
   dashboard).
2. **No endpoint yet to link an invited member's `identityId`** once they
   complete a real login (matching by mobile number). auth-service now
   exposes `GET /users/me` (added 2026-08-28) to resolve a caller's own
   mobile number from their Bearer token, so the pieces exist to build
   this — but nothing here calls it yet, and it's moot until #1 (real
   mobile login) actually produces a Bearer token to test it with. Phase 1
   only ever creates `CommunityMember` rows with `identityId` left null
   for invited (non-admin) owners.

## Files in this directory
```
backend/mycommunity-service/
├── Dockerfile                      # Plain Maven build, no external auth needed
├── docker-compose.local-postgres.yml  # Local-only: Postgres on 5433 for `mvn spring-boot:run`
├── docker-compose.yml              # DEV stack (default: docker compose up)
├── docker-compose.test.yml         # TEST stack
├── docker-compose.prod.yml         # PROD stack
├── .env.example                    # Dev secrets template → copy to .env
├── .env.test.example               # Test secrets template → copy to .env.test
└── .env.prod.example               # Prod secrets template → copy to .env.prod
```

## Running Docker Compose

All commands run from this directory (`backend/mycommunity-service/`). This
repo's own root shared-infra stack (Vault + Redis + `raitukashtam-network`)
must already be running first — see repo-root `CLAUDE.md`. There is no
dependency on the raitukashtam platform repo's stack, at build time or
runtime.

```sh
cp .env.example .env               # fill in MYCOMMUNITY_DB_PASSWORD, VAULT_TOKEN
docker compose up -d --build       # DEV
# or: docker compose -f docker-compose.test.yml --env-file .env.test up -d --build   # TEST
# or: docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build   # PROD
```

Tearing down or redeploying this stack does not touch the platform stack or
any other business service's stack, and vice versa — they're all separate
Compose projects sharing one Docker network.

Watch out for **container name collisions** with leftover containers from
other Compose projects (e.g. an old stack in the platform repo that used to
run `product-postgres` before this service was split out, or this
directory's own pre-rename `product-postgres`/`product-service` containers)
— `container_name` is a literal name, so Compose won't recognize or reuse a
container created under a different project. If `docker compose up` fails
with a name conflict, `docker inspect <id> --format '{{index .Config.Labels
"com.docker.compose.project"}}'` on the conflicting container tells you
which project actually owns it before you remove it.

If you change the `Community`/`CommunityMember` entity shape again, the
dev Postgres volume (`mycommunity-service_mycommunity-pgdata`) will need
resetting (`docker compose down` then `docker volume rm
mycommunity-service_mycommunity-pgdata`) — Hibernate's dev-profile
`ddl-auto: update` only adds columns, it won't drop/relax old ones, so a
changed NOT NULL column from a prior schema will make every insert fail.

## History

This service's git history (23 commits) was preserved from the platform
repo via `git subtree split` + `git subtree add`, so `git log` and
`git blame` on files under this directory predate `raitukashtam-business`'s
own first commit. It was renamed from `product-service` to
`mycommunity-service` on 2026-08-28 — see repo-root `PROGRESS.md` for the
full list of what changed in that rename.

**`jwt-library` was removed entirely on 2026-08-28** (same session, right
after the rename). It validated JWTs with HMAC256 against a shared
`jwt.secret` — but auth-service had since moved to Spring Authorization
Server, which signs with RS256 via its own JWKS, so jwt-library could
never actually have validated a real auth-service-issued token; nobody had
caught the drift because no real login flow existed yet to surface it
(see Known gaps above). Removing it also removed this service's *only*
GitHub Packages / platform-repo build-time dependency — `settings.xml`,
the Dockerfile's BuildKit secret mount, `GITHUB_TOKEN`, and the CI
workflow's `PACKAGES_READ_TOKEN` step are all gone as a result. This repo
now has zero dependency on the platform repo, at build time or runtime.
