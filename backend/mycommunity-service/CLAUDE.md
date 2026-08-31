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
- **Calls `auth-service` over the network** (`http://auth-service:8080`,
  `AuthServiceClient` → `GET /users/me`, forwarding the caller's own
  Bearer token) to resolve the caller's real mobile number/name — added
  2026-08-31 to stop trusting a client-supplied admin mobile number at
  community creation, and reused for the invited-member-activation flow.
  This is a `RestTemplate` call (not `WebClient`), reintroducing the
  cross-service dependency this file used to say wasn't needed.
- **Uses Flyway** for schema migrations (`src/main/resources/db/
  migration/`), added 2026-08-31 — previously Hibernate `ddl-auto` only
  (`update` in dev, `validate` in test/prod already). `V1` is a baseline
  generated from `pg_dump` against the live dev schema at the time; `V2`
  renamed `CommunityRole.OWNER` to `RESIDENT` and added the
  `community_join_request` table. See auth-service's own Flyway
  convention (`baseline-on-migrate`/`baseline-version` in
  `application.yml`) — this service now follows the same pattern.

## Domain model (Phase 1, extended 2026-08-31 — registration, roles, join requests)

- **Community**: name, totalUnits, street, area, district, state, pincode,
  landmark. `(name, pincode)` (normalized) must be unique — `POST
  /communities` 409s with the existing community's id/name otherwise.
- **CommunityMember**: community (FK), name, unitNumber, mobileNumber
  (unique per community), email (nullable, self-service only — never set
  at invite time), role (`ADMIN`/`RESIDENT` — renamed from `OWNER`
  2026-08-31), status (`INVITED`/`ACTIVE`), identityId (nullable — the
  auth Identity UUID, linked once that mobile number completes a real
  login via `POST /communities/members/activate-invitations`).
- **CommunityJoinRequest** (new 2026-08-31): community (FK),
  requesterIdentityId, requesterMobileNumber (resolved server-side, never
  client-supplied), requesterName, status (`PENDING`/`APPROVED`/
  `REJECTED`). At most one `PENDING` request per identity per community
  (partial unique index) — a past `REJECTED` request doesn't block asking
  again. This is the "I wasn't invited" path, surfaced when `POST
  /communities` 409s on a duplicate community.

Endpoints (`/api/v1/communities`, all require a Bearer JWT):

| Method | Path | Auth rule |
|---|---|---|
| POST | `/api/v1/communities` | Any authenticated caller; becomes the community's first ACTIVE ADMIN (mobile/name resolved from auth-service `/users/me`, not client-supplied); 409 on duplicate `(name, pincode)` |
| GET | `/api/v1/communities/mine` | Any authenticated caller; lists their own communities+role+status |
| POST | `/api/v1/communities/members/activate-invitations` | Any authenticated caller; links any `INVITED` rows matching their real mobile number, returns the same shape as `/mine` |
| GET | `/api/v1/communities/{id}` | Caller must be an ACTIVE member (any role) |
| POST | `/api/v1/communities/{id}/members` | Caller must be an ACTIVE ADMIN; 409 on duplicate mobile in the same community; new member is `RESIDENT`/`INVITED` |
| GET | `/api/v1/communities/{id}/members` | Caller must be an ACTIVE member |
| PATCH | `/api/v1/communities/{id}/members/me` | Caller must be an ACTIVE member; updates own name/email/unitNumber |
| DELETE | `/api/v1/communities/{id}/members/{memberId}` | Caller must be an ACTIVE ADMIN; 409 if it's the last ACTIVE ADMIN |
| POST | `/api/v1/communities/{id}/join-requests` | Any authenticated caller not already an active member; 409 if already a member or a PENDING request exists |
| GET | `/api/v1/communities/{id}/join-requests` | Caller must be an ACTIVE ADMIN; lists PENDING requests |
| POST | `/api/v1/communities/{id}/join-requests/{reqId}/approve` | Caller must be an ACTIVE ADMIN; creates an ACTIVE RESIDENT member |
| POST | `/api/v1/communities/{id}/join-requests/{reqId}/reject` | Caller must be an ACTIVE ADMIN |

Later phases (dashboard aggregation, bills/payments, expenses, visitors,
announcements, amenities) are designed but not yet built — see
`~/.claude/plans/validated-rolling-pizza.md` in the session this was
planned in, or ask for the full data model if that file isn't available.
Expenses is explicitly ADMIN-only per the user's spec, not yet designed
in detail.

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
   dashboard). This also blocks live-testing the join-request
   approve/reject path with a genuine second identity (see 2026-08-31
   session in `PROGRESS.md`) and the mobile app's own missing screens
   (Select-Community, Set-Up-PIN) that this service's new endpoints exist
   to support.

## Files in this directory
```
backend/mycommunity-service/
├── Dockerfile                      # Plain Maven build, no GitHub Packages dependency
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

Schema changes now go through Flyway (`src/main/resources/db/migration/`,
added 2026-08-31), not `ddl-auto` — dev is `validate` now, same as
test/prod. Add a new versioned migration for any entity change; don't
edit `V1__baseline_schema.sql` after the fact. A migration that changes
an existing column in an incompatible way (like the `OWNER`→`RESIDENT`
enum rename) still needs the dev Postgres volume reset if old rows exist
that the new constraint would reject (`docker compose down` then `docker
volume rm mycommunity-service_mycommunity-pgdata`) — Flyway itself won't
touch or migrate existing row data beyond what you write into the
migration's SQL.

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
