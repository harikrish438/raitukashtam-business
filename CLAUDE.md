# Raitukashtam Business — Claude Context

## Project Summary

**raitukashtam-business** holds the business/domain services for the
Raitukashtam farmer-marketplace platform. It was split out of the
[raitukashtam](https://github.com/harikrish438/raitukashtam) platform repo
(2026-08-16) so domain services can be built, deployed, and redeployed
independently of platform infrastructure and of each other. `product-service`
is the first domain service; future ones (e.g. an order-service) follow the
same pattern: `backend/<service>/`, a `docker-compose.<service>*.yml` (or its
own root compose file if this repo ever grows to hold more than one service),
and a path-filtered CI workflow.

## Relationship to raitukashtam (the platform repo)

This repo does **not** stand alone. At runtime, `product-service`:
- Registers with Eureka (`discovery-service`) running in the platform repo's stack.
- Pulls config from `config-server-service` in the platform stack.
- Reads secrets from the same Vault instance the platform stack uses.
- Calls `auth-service` (platform repo) via load-balanced RestTemplate.
- Joins the `raitukashtam-network` Docker bridge network created by the
  platform repo's compose files — this repo's compose files declare it
  `external: true` and never create it. **The platform stack must already be
  running** before you bring this stack up.

At build time, `product-service` depends on the shared `jwt-library` for JWT
validation. `jwt-library`'s source stays in the platform repo (it's also
used by `auth-service` there) and is **published to GitHub Packages** by the
platform repo's CI (`platform-ci.yml`, `publish-jwt-library` job) whenever
`backend/jwt-library/**` changes on `main`. This repo's
`backend/product-service/pom.xml` declares that GitHub Packages URL as a
`<repository>` and resolves `jwt-library` from there — there is no local copy
of jwt-library's source in this repo.

**If jwt-library's public API changes** (in the platform repo), its
`pom.xml` version must be bumped and republished before `product-service`
here can pick up the change (GitHub Packages rejects republishing an
existing version). Update the dependency version in
`backend/product-service/pom.xml` to match.

## Authenticating to GitHub Packages

Both local Docker builds and CI need a token with `read:packages` scope to
resolve `jwt-library`:
- **CI** (`.github/workflows/ci.yml`): uses the repo's built-in
  `secrets.GITHUB_TOKEN`, which already has `read:packages` for packages
  published under the `harikrish438` account/org — no extra secret needed.
- **Local `docker compose build`**: set `GITHUB_TOKEN` in your `.env` (see
  `.env.example`) to a classic PAT with `read:packages`. The Dockerfile
  consumes it via a BuildKit secret mount (`--mount=type=secret`), so it
  never lands in an image layer.
- **Local `mvn` runs outside Docker** (e.g. `mvn spring-boot:run` against
  `backend/product-service/docker-compose.yml`'s local Postgres): add a
  `<server>` entry for id `github` with the same PAT to your own
  `~/.m2/settings.xml` (do not commit a personal settings.xml to this repo —
  `backend/product-service/settings.xml` here is the Docker-build-only copy
  that reads credentials from env vars).

## Repository Layout

```
raitukashtam-business/
├── backend/
│   └── product-service/         # Product catalog service (domain service)
│       ├── Dockerfile           # Pulls jwt-library from GitHub Packages
│       ├── settings.xml         # Maven settings for the Docker build (env-var creds)
│       └── docker-compose.yml   # Local-only: Postgres on 5433 for `mvn spring-boot:run`
├── docker-compose.yml           # DEV stack (default: docker compose up)
├── docker-compose.test.yml      # TEST stack
├── docker-compose.prod.yml      # PROD stack
├── .env.example                 # Dev secrets template → copy to .env
├── .env.test.example            # Test secrets template → copy to .env.test
├── .env.prod.example            # Prod secrets template → copy to .env.prod
└── .github/workflows/ci.yml     # Build/test on changes to backend/product-service/**
```

## product-service (port 8081, debug 5007 in dev)
- PostgreSQL DB: `product-service-db` (host port 5434 via this repo's Docker
  Compose; port 5433 if run locally via `backend/product-service/docker-compose.yml`)
- Endpoints: `POST /api/v1/products/`, `GET /api/v1/products/{productId}`
- Calls auth-service (platform repo) via load-balanced RestTemplate to fetch user details
- Validates JWT using `jwt-library`, resolved from GitHub Packages (see above)

Key DB table: `product` (id, name, description, price, user_id)

## Running Docker Compose

```sh
# In the raitukashtam (platform) repo first:
docker compose up -d                       # DEV platform stack
# or: docker compose -f docker-compose.test.yml --env-file .env.test up -d   # TEST
# or: docker compose -f docker-compose.prod.yml --env-file .env.prod up -d   # PROD

# Then in this repo:
docker compose up -d                       # DEV
# or: docker compose -f docker-compose.test.yml --env-file .env.test up -d   # TEST
# or: docker compose -f docker-compose.prod.yml --env-file .env.prod up -d   # PROD
```

Tearing down or redeploying this stack does not touch the platform stack,
and vice versa — they're separate Compose projects sharing one network.

## History

`backend/product-service`'s git history (23 commits) was preserved from the
platform repo via `git subtree split` + `git subtree add`, so `git log` and
`git blame` on files under `backend/product-service/` predate this repo's
own first commit.
