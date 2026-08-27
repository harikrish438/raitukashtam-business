# product-service — Claude Context

Product catalog domain service. See the repo-root `CLAUDE.md` first for
shared context (relationship to the platform repo, GitHub Packages auth,
the per-service directory convention) — this file only covers what's
specific to `product-service`.

## Overview (port 8081, debug 5007 in dev)
- PostgreSQL DB: `product-service-db` (host port 5434 via this directory's
  `docker-compose.yml`; port 5433 if run locally via
  `docker-compose.local-postgres.yml`)
- Endpoints: `POST /api/v1/products/`, `GET /api/v1/products/{productId}`
- Calls `auth-service` (this repo's own `backend/auth-service`) via a plain
  `RestTemplate` at its Docker Compose hostname (`http://auth-service:8080`)
  to fetch user details — no Eureka/service discovery involved (dropped
  2026-08-27, see repo-root `CLAUDE.md`)
- Validates JWT using `jwt-library` (`com.raitukashtam:jwt-library`,
  currently pinned to `1.0.0` in `pom.xml`), resolved from GitHub Packages
  (see repo-root `CLAUDE.md` for the auth setup and version-bump process)

Key DB table: `product` (id, name, description, price, user_id)

## Files in this directory
```
backend/product-service/
├── Dockerfile                      # Pulls jwt-library from GitHub Packages
├── settings.xml                    # Maven settings for the Docker build (env-var creds)
├── docker-compose.local-postgres.yml  # Local-only: Postgres on 5433 for `mvn spring-boot:run`
├── docker-compose.yml              # DEV stack (default: docker compose up)
├── docker-compose.test.yml         # TEST stack
├── docker-compose.prod.yml         # PROD stack
├── .env.example                    # Dev secrets template → copy to .env
├── .env.test.example               # Test secrets template → copy to .env.test
└── .env.prod.example               # Prod secrets template → copy to .env.prod
```

## Running Docker Compose

All commands run from this directory (`backend/product-service/`). This
repo's own root shared-infra stack (Vault + Redis + `raitukashtam-network`)
must already be running first — see repo-root `CLAUDE.md`. There is no
dependency on the raitukashtam platform repo's stack.

```sh
cp .env.example .env               # fill in PRODUCT_DB_PASSWORD, VAULT_TOKEN, GITHUB_TOKEN
docker compose up -d --build       # DEV
# or: docker compose -f docker-compose.test.yml --env-file .env.test up -d --build   # TEST
# or: docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build   # PROD
```

Tearing down or redeploying this stack does not touch the platform stack or
any other business service's stack, and vice versa — they're all separate
Compose projects sharing one Docker network.

Watch out for **container name collisions** with leftover containers from
other Compose projects (e.g. an old stack in the platform repo that used to
run `product-postgres` before this service was split out) — `container_name`
is a literal name, so Compose won't recognize or reuse a container created
under a different project. If `docker compose up` fails with a name
conflict, `docker inspect <id> --format '{{index .Config.Labels
"com.docker.compose.project"}}'` on the conflicting container tells you
which project actually owns it before you remove it.

## History

This service's git history (23 commits) was preserved from the platform
repo via `git subtree split` + `git subtree add`, so `git log` and
`git blame` on files under this directory predate `raitukashtam-business`'s
own first commit.
