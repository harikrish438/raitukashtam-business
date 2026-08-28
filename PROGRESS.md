# raitukashtam-business — Progress Log

Tracks work done in this repo, session by session, until production
deployment. Read this at the start of a session for context on what's
already done and what's still open. Update it before ending a session —
see repo-root `CLAUDE.md`'s "Session Tracking" section for the convention.

## Status

Standalone dev stack (auth-service + mycommunity-service + root-owned
Vault/Redis) verified working locally. **Not yet deployed to AWS.**
`mycommunity-service` now has a real Phase 1 domain (Community +
CommunityMember: create a community, add/list/remove unit owners) for the
`mysociety` mobile app (github.com/harikrish438/mobile-apps) — verified
end-to-end in Docker with a hand-minted RS256 JWT. Later phases (dashboard,
bills/payments, expenses, visitors, announcements, amenities) are designed
but not built. This repo now has **zero dependency on the platform repo**,
build-time or runtime — `jwt-library` was removed (see Sessions below).
auth-service now has everything needed for mobile OTP login on the
backend side (`POST /otp/login`, `GET /users/me`) — what's left to make it
usable end-to-end is client-side (the mobile app's own PKCE networking
code, which doesn't exist yet) plus wiring mycommunity-service to actually
use it (see Open items).

## Open items / next steps

- Build the remaining `mycommunity-service` phases (dashboard aggregation,
  bills/payments, expenses, visitors, announcements, amenities) — full
  data model already designed, see the 2026-08-28 session entry below.
- **Mobile app has no networking/PKCE client code at all** (confirmed by
  reading every screen — `OtpActivity.verifyOtp()` is just a `TODO` that
  navigates straight to the dashboard). The backend side is now ready
  (`POST /otp/login` establishes a session, the `mycommunity-android`
  PKCE client already exists under its own `MYCOMMUNITY` product — onboarded
  via the PLATFORM_ADMIN API, not code, see the 2026-08-28 session entry —
  and `/oauth2/authorize` + `/oauth2/token` already work) — this is purely
  an Android app implementation gap now, not a backend one.
- No endpoint yet to link an invited `CommunityMember`'s `identityId` once
  they complete a real login (matched by mobile number). auth-service now
  has everything this needs (`GET /users/me` to resolve the caller's own
  mobile number from a Bearer token) — but the linking endpoint itself
  isn't built in `mycommunity-service` yet, and can't be tested for real
  until the mobile app can actually get a token (item above).
- Decide AWS deploy pipeline ownership for this repo: does
  raitukashtam-business get its own ECR + EC2 (or other) deploy workflow,
  or reuse infra some other way? Nothing exists yet — `auth-ci.yml` and
  `mycommunity-ci.yml` only build/test, they don't deploy.
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

### 2026-08-28

- Renamed `backend/product-service` to `backend/mycommunity-service`
  throughout this repo (this repo only — the platform repo's own
  `product-service` copy is untouched, per repo-root `CLAUDE.md`).
  Per user instruction: a mechanical rename now, with the real community
  domain model to be defined later — the old `Product` entity's shape
  (name, description, price, user_id) was carried over unchanged as a
  placeholder, just renamed to `Community`.
  - `git mv`'d the service directory, its Java package
    (`com.raitukashtam.product` → `com.raitukashtam.mycommunity`), and the
    Product-specific class files (`ProductServiceApplication` →
    `MyCommunityServiceApplication`, `ProductController` →
    `CommunityController`, `Product` entity → `Community`,
    `ProductRepository`/`Request`/`Response`/`Service` → `Community*`) —
    git history preserved on all of them.
  - Updated `pom.xml` (groupId/artifactId/name), `application.yml`
    (`spring.application.name`, Vault KV context, datasource URL/username),
    all four `docker-compose*.yml` files (service/container/volume names,
    `PRODUCT_DB_PASSWORD` → `MYCOMMUNITY_DB_PASSWORD`), `.env*.example`
    files, and this service's own `CLAUDE.md`.
  - REST endpoints moved from `/api/v1/products` to `/api/v1/communities`;
    DB table `product` → `community`.
  - Renamed `.github/workflows/ci.yml` → `mycommunity-ci.yml` (matching
    the `auth-ci.yml` naming convention), updated its path filters and
    job/step names.
  - Updated root `CLAUDE.md`, root `docker-compose*.yml` comments, root
    `.env*.example`, and `infra/vault/vault-init.sh` for the new service
    name and `MYCOMMUNITY_DB_PASSWORD` var — including the actual local
    (gitignored) `.env` files for both the repo root and the service
    directory, so the local dev stack keeps working without further edits.
  - Verified `mvn compile` and `mvn test-compile` both succeed offline
    against the renamed package/class structure.
  - Verified end-to-end in Docker: `docker compose up -d --build` in
    `backend/mycommunity-service/` built and started `mycommunity-service`
    + `mycommunity-postgres`. First boot failed (Postgres SCRAM auth error,
    no password) because Vault still held the secret at the *old* KV path
    `secret/product-service` — `vault-init` is a one-shot container that
    had already run (and exited) with the pre-rename script before this
    session's edit to `infra/vault/vault-init.sh` landed, so the new
    `secret/mycommunity-service` path was never seeded. Fixed by
    `docker compose up -d --force-recreate vault-init` from the repo root
    (re-seeds Vault from the current `.env`), then restarting
    `mycommunity-service`. After that: health check passes, Hibernate
    created the `community` table cleanly, `GET /actuator/health` → 200,
    `POST /api/v1/communities/` → 401 with no JWT (correct — confirms
    routing/security wiring, not a 404). **Takeaway for next time a KV
    path/name changes in `vault-init.sh`: `vault-init` must be
    force-recreated, a plain restart won't re-run it with new env/script.**
  - Left `backend/auth-service` completely untouched — it has its own,
    unrelated `Product`/`ProductMembership`/etc. concept (tenant/business-
    line scoping for roles), a different domain from the marketplace
    product catalog this rename was about.

- **Designed and built Phase 1 of `mycommunity-service`'s real domain**,
  for the `mysociety` Android app (github.com/harikrish438/mobile-apps,
  `mysociety/` dir — a UI-only prototype, no networking code yet). Read
  every screen/layout in that app to derive the domain, then read
  `auth-service`'s actual code (not assumed) to find the service
  boundary: auth's `Product`/`ProductMembership` is a business-line-level
  tenant (the single seeded `raitukashtam` product), not a
  per-apartment-complex one, so `Community` had to be a new entity owned
  by `mycommunity-service`, matching name/mobile typed directly into the
  app's own forms — no auth-service calls needed for this phase.
  - Added `Community` (name, totalUnits, street, area, district, state,
    pincode, landmark) and `CommunityMember` (community, name, unitNumber,
    mobileNumber, role ADMIN/OWNER, status INVITED/ACTIVE, identityId
    nullable) entities/repositories/DTOs, replacing the placeholder.
  - Five endpoints under `/api/v1/communities`: create (caller becomes
    ACTIVE ADMIN), get, add member (ADMIN-only, 409 on duplicate mobile),
    list members, remove member (ADMIN-only, 409 removing the last ADMIN).
    Authorization is entirely mycommunity-service's own — the JWT only
    carries an identity UUID (`sub`), auth's `roles` claim is scoped to
    its own product concept and useless here.
  - Added 7 Mockito unit tests for the authorization/business-rule logic
    (`CommunityServiceTest`) — all passing offline, no infra needed.
  - **Found and fixed a real, previously-undetected bug while doing this**:
    `jwt-library` 1.0.0 (what `mycommunity-service`'s `pom.xml` was pinned
    to) validates HMAC256 against a shared `jwt.secret` — but
    `auth-service` had since moved to Spring Authorization Server, which
    signs with RS256 via its own JWKS (`AuthorizationServerConfig`,
    confirmed by reading its source). These are incompatible signing
    algorithms: `mycommunity-service` could never have validated a real
    auth-service-issued token. Nobody had caught it because no real login
    flow exists yet to surface the mismatch. Per user instruction, removed
    `jwt-library` entirely rather than bump its version — replaced with
    Spring's own `oauth2ResourceServer` support pointed at auth-service's
    live JWKS endpoint (`${AUTH_SERVICE_URL}/oauth2/jwks`). This also
    removed mycommunity-service's *only* GitHub Packages / platform-repo
    build-time dependency: deleted `settings.xml`, the Dockerfile's
    BuildKit secret mount, `GITHUB_TOKEN` from every `.env*`
    file/example (including the real local `.env` files at repo root and
    in the service dir), `PACKAGES_READ_TOKEN` from `mycommunity-ci.yml`,
    the dead `jwt.secret` Vault entry in `vault-init.sh`, and the whole
    "Authenticating to GitHub Packages" section from repo-root `CLAUDE.md`
    (nothing in this repo depends on GitHub Packages any more).
  - Verified end-to-end in Docker: had to reset the dev Postgres volume
    (`docker compose down` + `docker volume rm
    mycommunity-service_mycommunity-pgdata`) since the old placeholder
    schema's `NOT NULL price`/`user_id` columns predated this change and
    Hibernate's `ddl-auto: update` won't drop/relax old columns. After
    that: minted a real RS256 test JWT (small throwaway script, not part
    of the app, using the same private key + `keyIDFromThumbprint()`
    auth-service actually signs with) and exercised all 5 endpoints
    against the running container — 401 with no token, 201 create
    community + admin membership, 200 get/list, 201 add owner, 409
    duplicate mobile, 403 for a caller with no membership at all, 204
    remove member, 409 removing the last ADMIN. All passed.
  - Not built (explicitly deferred): an endpoint to link an invited
    member's `identityId` on first real login — blocked on auth-service's
    mobile login gap (see Open items).

- **Added `GET /users/me` to `auth-service`**, closing part of the gap
  above: previously nothing let a resource server resolve "which real
  user is this JWT for" — the JWT's `sub` is the Identity UUID, but
  auth-service's only lookup (`GET /users/{id}`) needs a numeric `User.id`,
  and no endpoint bridged the two. Reused the existing
  `UserService.findByIdentityId(UUID)` (already used internally by
  `OAuth2TokenClaimsCustomizer`) rather than adding new lookup logic — new
  `UserController.getCurrentUser` reads `@AuthenticationPrincipal Jwt jwt`
  → `jwt.getSubject()`, same pattern just used in `mycommunity-service`'s
  `CommunityController`. No `SecurityConfig` change needed: the existing
  `.requestMatchers(HttpMethod.GET, "/users/*").authenticated()` rule
  already covers `/users/me` (Spring MVC's exact-path `/me` mapping wins
  over the `/{id}` variable one automatically). A malformed/non-UUID
  subject (what a client_credentials token's `sub` looks like — a client
  id, not a UUID) is caught and mapped to the same 404 as an unknown
  identity via the existing `UsernameNotFoundException` →
  `GlobalExceptionHandler` path, so no new exception type either.
  Verified against the rebuilt container: 200 with a real registered
  user's identity, 404 for a well-formed-but-unknown UUID, 404 for a
  non-UUID (client-style) subject, 401 with no token.
  - **This closes the auth-service side of the gap only.**
    `mycommunity-service` doesn't call this endpoint yet (nothing in its
    Phase 1 code talks to auth-service at all, by design) — an "activate
    invited member" flow would need to add that call back in. And it's
    still moot in practice until real mobile login exists, since without
    that the mobile app has no Bearer token to send in the first place.

- **Added `POST /otp/login` to `auth-service`** — the actual missing piece
  for real mobile login. Before writing any code, discussed with the user
  first (their explicit ask: auth-service's existing architecture is
  correct, only *add*, don't restructure it) and found the codebase had
  already anticipated exactly this: `CredentialType.OTP_PHONE` has existed
  since the very first migration (`V1__baseline_schema.sql`) but was never
  used anywhere. The whole feature turned out to be additive by following
  a precedent that already exists for the identical problem shape —
  `GoogleController.verifyGoogleToken()` already solves "authenticate via
  something that isn't a password, then let the client continue with the
  standard `/oauth2/authorize` PKCE call it already uses" for Google
  login. `POST /otp/login` mirrors that exactly, keyed by
  `IdentityCredential(OTP_PHONE, mobileNumber)` instead of
  `(GOOGLE, googleSubject)`:
  - Validates the OTP via the existing `OTPService.validateOtp` (same
    call `/otp/verify` already makes — `/otp/verify` is unchanged, still
    useful on its own as a bare yes/no check, e.g. for a future 2FA
    step-up on password login).
  - Find-or-create: an existing `OTP_PHONE` credential wins outright; failing
    that, a `User` already registered under this mobile number via another
    method (e.g. password) gets linked to rather than duplicated (new
    `UserRepository.findByMobileNumber`, the one genuinely new repository
    method this needed); only a truly never-seen phone number provisions a
    fresh `Identity`/`User`, same shape as `GoogleController.provisionUser`
    (membership handling described separately below, in the MyCommunity
    product entry — this original version attached to the shared default
    product, since MyCommunity's own product didn't exist yet at this point
    in the session).
  - `Identity.primaryEmail`/`User.email` are both `NOT NULL` + unique with
    no OTP-only path around that — synthesizes a deterministic placeholder
    (`<mobile>@phone.mysociety.internal`) for phone-only signups, the same
    kind of fallback `GoogleController.provisionUser` already does when
    Google supplies no name. No entity/schema change.
  - Ends by writing the authenticated session via
    `SecurityContextRepository`, byte-for-byte the same three lines
    `GoogleController` uses.
  - No `SecurityConfig`, `AuthorizationServerConfig`, or entity changes at
    all — genuinely additive, as asked.
  - Added 6 tests to `OtpControllerApiTest` (new phone number, wrong code,
    same phone twice doesn't duplicate, links to an existing
    password-registered account by mobile number, locked account → 403,
    rate limiting) — full suite now 84/84 passing (was 78).
  - **Still not enough for a working end-to-end mobile login** — the
    `mysociety` Android app itself has zero networking code (confirmed by
    reading every screen; `OtpActivity.verifyOtp()` is a `TODO` that just
    navigates to the dashboard). What's missing now is purely client-side:
    the app calling `/otp/generate` → `/otp/login` → the existing
    `/oauth2/authorize`+`/oauth2/token` PKCE dance (against the
    `mycommunity-android` client added below). Backend-side, this gap is
    closed.

- **Gave mycommunity its own auth-service Product**, at the user's explicit
  request after they noticed every signup (`registerUser`, Google login,
  and the new OTP login above) was hardcoding the single generic
  `RAITUKASHTAM` product (`raitukashtam.default-product-code`) — there was
  no dedicated product for the actual real app this repo serves. Found a
  latent inconsistency while investigating: `OAuth2TokenClaimsCustomizer`
  already computes the `roles` claim from **the calling Client's own
  product** (`client.getProduct().getCode()`), not the hardcoded default —
  invisible today because only one product exists, but it would silently
  break (empty roles) the moment a second product's client tried to log
  in while its users' memberships were still being created under the
  wrong one.
  - Added `Product(code=MYCOMMUNITY, name=MyCommunity)` + its `CONSUMER`/
    `ADMIN` roles via a new migration, `V13__seed_mycommunity_product_and_
    roles.sql` — **not** a `CommandLineRunner`, deliberately: `V11` (an
    existing migration, already in this codebase) documents exactly why —
    `RoleService.assignDefaultRole` needs the role to exist before any
    login flow runs, and a `CommandLineRunner`-seeded product isn't
    guaranteed to exist yet when an *earlier* migration tries to seed its
    role (confirmed live the first time, by hand: a first pass seeding the
    product via a `MyCommunityProductSeeder` `CommandLineRunner` left
    `/otp/login` 404-ing with "Default role 'CONSUMER' not found for
    product: MYCOMMUNITY" — deleted that seeder, replaced with the
    migration, matching `V11`'s already-proven pattern exactly).
  - Added `mycommunity-android` (ANDROID/PKCE client, redirect
    `mycommunity://callback`) via a new `MyCommunityClientSeeder`
    (`CommandLineRunner`, same idempotent pattern as the existing
    `ClientDataSeeder` — this one's fine as a runner since the product it
    depends on now comes from a migration, always seeded first).
  - **Only `OtpController` was repointed at the new product** — not
    `UserService.registerUser` or `GoogleController.provisionUser`, which
    still use the original shared `RAITUKASHTAM` default, completely
    unchanged. Deliberate: those two flows are exercised by existing tests
    that log in via the `raitukashtam-web`/`-android` clients (scoped to
    `RAITUKASHTAM`); repointing them too would have created the exact
    mismatch described above for every one of those tests. `POST
    /otp/login` now calls a new `ensureMyCommunityMembership(identity)`
    after resolving the identity (whether brand-new or linked to an
    existing account) — every successful OTP login guarantees a
    `MYCOMMUNITY` membership, without touching or replacing any existing
    membership under another product.
  - Added 2 more tests to `OtpControllerApiTest`: a brand-new OTP signup
    asserts membership exists under `MYCOMMUNITY` and *not* `RAITUKASHTAM`;
    linking to an existing password-registered (`RAITUKASHTAM`) account
    asserts *both* memberships end up present afterward. Full suite now
    85/85 passing.
  - Verified against the rebuilt container: `V13` applied
    ("Migrating schema \"public\" to version \"13 - seed mycommunity
    product and roles\""), `mycommunity-android` seeded, both `RAITUKASHTAM`
    and `MYCOMMUNITY` rows present in the real dev database.

- **Reverted the above migration/seeder in favor of API-driven onboarding**,
  after the user asked: "what if I need to onboard another product —
  does that need code changes?" Answer, confirmed by reading the code:
  no — `ProductController`/`RoleController`/`ClientController` already
  expose `POST /products`, `POST /products/{code}/roles`, and
  `POST /products/{code}/clients` as a generic `PLATFORM_ADMIN`-gated API,
  predating this session entirely. `MYCOMMUNITY`'s migration+seeder only
  existed because that's how `RAITUKASHTAM` (the *first* product) had to
  be bootstrapped — a genuine chicken-and-egg case (no product/client/
  admin-token exists yet on a fresh database to call any admin API with).
  `MYCOMMUNITY` never actually had that constraint, since `RAITUKASHTAM`'s
  bootstrap chain (and the `PlatformAdminSeeder` that promotes the first
  `PLATFORM_ADMIN`) already existed by the time it was added.
  - Deleted `V13__seed_mycommunity_product_and_roles.sql` and
    `MyCommunityClientSeeder.java`. Removed the now-dead
    `mycommunity-product-name`/`mycommunity-android-client.redirect-uri`
    config (kept `mycommunity-product-code` — `OtpController` still needs
    it at runtime for `ensureMyCommunityMembership`).
  - Tests still need `MYCOMMUNITY` to exist without any auto-seeding in
    application code now, so added a test-only fixture,
    `TestDataFactory.ensureMyCommunityProduct()` (direct repository
    access, same kind of test-only shortcut `registerAndPromoteToPlatformAdmin`
    already takes), called from `OtpControllerApiTest`'s `@BeforeEach`.
    Full suite still 85/85 passing.
  - **Found and fixed a real gap while restoring the dev environment**:
    `PLATFORM_ADMIN_EMAIL` was never actually wired through any of the
    three `docker-compose*.yml` files' `environment:` blocks or
    `.env*.example` files — `PlatformAdminSeeder` already existed and
    worked, but was unreachable via Docker, in every environment. Added
    it to all three compose files and all three env examples.
  - **Proved the whole runbook end-to-end against the live dev
    container**, not just by reading the code: reset the dev Postgres
    volume (it had `V13` recorded as applied), set
    `PLATFORM_ADMIN_EMAIL=platform-admin@raitukashtam.local` in `.env`,
    registered that email via `/users/register`, restarted once
    (`PlatformAdminSeeder` promoted it — confirmed in logs), then reused
    the test suite's own `PkceFlowClient` (via a throwaway scratchpad
    Java program, not committed) to perform a *real* password login +
    Authorization Code/PKCE dance and obtain a genuine `PLATFORM_ADMIN`
    access token — then called the three onboarding endpoints with it.
    All three returned `201 CREATED`; confirmed `MYCOMMUNITY`, its
    `CONSUMER`/`ADMIN` roles, and `mycommunity-android` all present in
    the real database afterward. Zero code involved in the onboarding
    itself — only the one-time admin bootstrap (which is inherent to any
    fresh environment, not specific to onboarding a second product).
  - Documented the full runbook in `backend/auth-service/CLAUDE.md`
    ("Products, roles, and clients — onboarding is a data operation, not
    a code change") for whoever onboards product #3.

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
