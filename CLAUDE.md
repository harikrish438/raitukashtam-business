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

## Convention for Business Services

Each service is fully self-contained under `backend/<service>/`:
- Source code, `Dockerfile`, Maven `settings.xml` (Docker-build-only, reads
  creds from env vars).
- All of its `docker-compose*.yml` and `.env*.example` files — no shared
  root-level compose file across services, even though services may depend
  on the same platform infrastructure. This keeps services independently
  buildable/deployable and avoids one service's stack colliding with
  another's.
- Its own `CLAUDE.md` with service-specific context.

The one exception is CI: workflow files must live at repo-root
`.github/workflows/` because that's the only location GitHub Actions reads
from — not a per-service choice. Each service still gets its own
path-filtered workflow there (e.g. `ci.yml` triggers only on
`backend/product-service/**` changes).

Current services:
- `backend/product-service/` — see `backend/product-service/CLAUDE.md`

## Relationship to raitukashtam (the platform repo)

No business service in this repo stands alone at runtime. Each one:
- Registers with Eureka (`discovery-service`) running in the platform repo's stack.
- Pulls config from `config-server-service` in the platform stack.
- Reads secrets from the same Vault instance the platform stack uses.
- Calls `auth-service` (platform repo) via load-balanced RestTemplate.
- Joins the `raitukashtam-network` Docker bridge network created by the
  platform repo's compose files — this repo's compose files declare it
  `external: true` and never create it. **The platform stack must already be
  running** before you bring any business-service stack up.

At build time, services may depend on shared libraries (e.g. `jwt-library`
for JWT validation) that live in the platform repo's source and are
**published to GitHub Packages** by the platform repo's CI whenever the
library's source changes on `main`. There is no local copy of any such
library's source in this repo — each service's `pom.xml` declares the
GitHub Packages URL as a `<repository>` and resolves it from there.

**If a shared library's public API changes** (in the platform repo), its
`pom.xml` version must be bumped and republished before a service here can
pick up the change (GitHub Packages rejects republishing an existing
version) — then the dependency version in the service's `pom.xml` must be
updated to match.

## Authenticating to GitHub Packages

Every business service that depends on a platform-repo library needs a
token with `read:packages` scope to resolve it. **`raitukashtam` (the
platform repo) is private, so packages published from it are private too**
— a repo's own auto-generated `secrets.GITHUB_TOKEN` is scoped only to that
repo and CANNOT read packages published under a different, private
repository (confirmed by a failed CI run, 2026-08-16: "Could not find
artifact com.raitukashtam:jwt-library:jar:1.0.0 in github", even though the
artifact had just been uploaded successfully). A real PAT is required
everywhere, for every service:
- **CI** (`.github/workflows/<service>-ci.yml` or similar): needs a classic
  PAT with `read:packages`, added as a repo secret named
  `PACKAGES_READ_TOKEN` (Settings > Secrets and variables > Actions > New
  repository secret). Create the PAT at https://github.com/settings/tokens.
- **Local `docker compose build`**: set `GITHUB_TOKEN` in that service's own
  `.env` (see its `.env.example`) to a classic PAT with `read:packages`.
  Dockerfiles consume it via a BuildKit secret mount
  (`--mount=type=secret`), so it never lands in an image layer.
- **Local `mvn` runs outside Docker**: add a `<server>` entry for id
  `github` with the same PAT to your own `~/.m2/settings.xml` (do not commit
  a personal settings.xml to this repo).

## History

This repo was split out of the `raitukashtam` platform repo on 2026-08-16
so business/domain services could be built and deployed independently.
