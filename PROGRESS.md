# raitukashtam-business — Progress Log

Tracks work done in this repo, session by session, until production
deployment. Read this at the start of a session for context on what's
already done and what's still open. Update it before ending a session —
see repo-root `CLAUDE.md`'s "Session Tracking" section for the convention.

## Status

Standalone dev stack (auth-service + product-service + root-owned
Vault/Redis) verified working locally. **Not yet deployed to AWS.**

## Open items / next steps

- Decide AWS deploy pipeline ownership for this repo: does
  raitukashtam-business get its own ECR + EC2 (or other) deploy workflow,
  or reuse infra some other way? Nothing exists yet — `auth-ci.yml` and
  `ci.yml` only build/test, they don't deploy.
- `product-service-pg` — a stray leftover Postgres container from before
  the original platform-repo split (predates this repo's existence
  effectively), still running locally on this machine, untouched. Confirm
  whether it holds anything worth keeping before removing it.
- No TLS/reverse-proxy in front of either service yet (the platform repo
  used Caddy for this — not replicated here).
- Production secrets (`.env.prod` values, real JWT signing keypair, Vault
  token, DB passwords) haven't been generated yet — `.env.prod.example`
  files only have placeholders.

## Sessions

### 2026-08-27

- Copied `auth-service` from the `raitukashtam` platform repo into this
  repo via `git subtree split` + `git subtree add` (52 commits of history
  preserved), parallel to the existing `product-service`.
- Made this repo fully standalone — no runtime dependency on the platform
  repo's stack:
  - Added a root-level shared-infra stack (`docker-compose.yml`/`.test.yml`/
    `.prod.yml`, `infra/vault/vault-init.sh`) providing Vault, Redis, and
    the `raitukashtam-network` itself (previously external, created by the
    platform repo).
  - Dropped Eureka and config-server entirely. `product-service` now calls
    `auth-service` directly via Docker Compose hostname
    (`http://auth-service:8080`) instead of a `@LoadBalanced`/Eureka-resolved
    `RestTemplate`.
  - Added `auth-service`'s own `Dockerfile` + `docker-compose.{yml,test,prod}`
    + `.env*.example`, matching `product-service`'s existing conventions.
- Tore down the platform repo's local Docker stack (containers + volumes)
  per user request. The platform repo's own git history/files were left
  completely untouched — its copies of `auth-service`/`product-service`
  are known, intentional duplicates.
- Removed now-dead dependencies after the user asked why image sizes were
  still large despite no new business logic: `spring-cloud-starter-netflix-
  eureka-client`, `spring-cloud-starter-config`, `micrometer-tracing-bridge-
  brave`, `zipkin-reporter-brave` from both services' `pom.xml`, plus the
  dead code that went with them (an orphaned `EurekaConfig.java` in
  `auth-service` that I'd missed on the first pass, unused `brave.*`
  imports, dead `eureka:`/`management.tracing`/`management.zipkin` config
  blocks).
- Verified end-to-end locally: both services build, start healthy (DB +
  Vault UP), `product-service` reaches `auth-service` directly over the
  shared Docker network.
- Committed (`decb7f4`) and pushed to `origin/main`.
