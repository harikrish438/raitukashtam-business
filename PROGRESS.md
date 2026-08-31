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
use it (see Open items). auth-service also now has a device-bound app-PIN
login (`POST /pin/register`, `POST /pin/login`, `GET`/`DELETE
/pin/devices`, admin `DELETE /users/{id}/pin-devices/{deviceId}`) so a
mobile app can re-authenticate via a short PIN instead of repeating OTP
every time, with the server (not the device) able to revoke a specific
lost/stolen device's access centrally — see the 2026-08-30 session entry.
Verified live end-to-end against the running dev container, including the
per-device lockout; not yet used by the (still nonexistent) mobile app
networking code. `POST /otp/login` for `MYCOMMUNITY` (broken since the
2026-08-30 DB reset) is fixed and live-verified with a real SMS OTP — see
the 2026-08-31 session entry. `mycommunity-service`'s Phase 1 domain has
since been extended with community-registration dedup, a
GET-`/users/me`-derived admin (no more client-supplied admin mobile),
`CommunityRole.OWNER` renamed to `RESIDENT`, self-service profile updates,
a "list my communities"/"activate my invitations" pair for the mobile
app's post-login branch, and a full join-request flow for someone who
wasn't invited — see the 2026-08-31 session entry. This service also
gained its first Flyway migrations (previously Hibernate `ddl-auto` only).
`mycommunity-service` now also has **Phase 2 (Announcements)**: ADMIN
posts, any ACTIVE member reads — pure data+API, no push notifications
(deliberately deferred, see the 2026-08-31 session entry (5)).
auth-service also gained a self-service `PATCH /users/me` (firstName/
lastName/email, partial update) — the account-level counterpart to
mycommunity-service's own member-profile update, closing a gap surfaced
by the `mysociety` app's bottom-nav "Profile" tab — see the 2026-08-31
session entry (3). auth-service's `SecurityConfig` was then flipped from
"public by default, list what needs auth" to "deny by default, list
what's public" (matching mycommunity-service's own model) — the previous
shape is exactly why `PATCH /users/me` needed a manual fix in the first
place, and it could have happened again with any future endpoint — see
the 2026-08-31 session entry (4).

## Open items / next steps

- Build the remaining `mycommunity-service` phases (bills/payments,
  expenses, dashboard aggregation, visitors, amenities, then push-
  notification delivery once the mobile app has real networking — see the
  full phased roadmap agreed with the user in the 2026-08-31 session
  entry (5)). Announcements (Phase 2) is now done. Full data model for
  the rest already designed, see the 2026-08-28 session entry below.
- **Push notification delivery is explicitly out of scope for now** —
  discussed with the user when scoping Announcements: needs FCM
  integration, a device-token registration endpoint, and (blocking)
  networking code in the mobile app, which doesn't exist yet. Deferred to
  its own later phase rather than bolted onto any single feature, since
  every future feature (bills due, visitor arrived, join-request
  approved) would want it too.
- **Mobile app has no networking/PKCE client code at all** (confirmed by
  reading every screen — `OtpActivity.verifyOtp()` is just a `TODO` that
  navigates straight to the dashboard). The backend side is now ready
  (`POST /otp/login` establishes a session, the `mycommunity-android`
  PKCE client already exists under its own `MYCOMMUNITY` product — onboarded
  via the PLATFORM_ADMIN API, not code, see the 2026-08-28 session entry —
  and `/oauth2/authorize` + `/oauth2/token` already work) — this is purely
  an Android app implementation gap now, not a backend one.
- **Fixed**: linking an invited `CommunityMember`'s `identityId` on first
  real login now has an endpoint — `POST /api/v1/communities/members/
  activate-invitations` — live-verified 2026-08-31 (see that session
  entry). Not yet callable end-to-end by a real user, since that still
  needs the mobile app's own networking code (item above) to actually
  reach it after OTP login.
- The join-request flow's create-when-already-a-member rejection
  (`POST /communities/{id}/join-requests` → 409) is live-verified
  (2026-08-31 session); approve/reject themselves are not — they're
  covered by unit tests only (`CommunityJoinRequestServiceTest`), since
  live-testing the real "second identity requests to join, first
  identity's admin approves it" path needs a second real registered
  mobile number.
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
- **`MYCOMMUNITY` re-onboarding after the 2026-08-30 DB reset is now
  functionally complete**: product, `mycommunity-android` client, and
  `CONSUMER`+`SUPPORT` roles all recreated — `POST /otp/login` for
  MyCommunity was broken (missing `CONSUMER` role → 404) and is now fixed
  and live-verified end-to-end, see the 2026-08-31 session entry.
  `SUPPORT` still isn't checked for anything in `mycommunity-service`
  (which never reads the JWT `roles` claim at all today) — that remains
  true and unchanged, just no longer blocking basic login.
- The mobile app still has no networking code (see above) to actually use
  the new device-PIN login — this session only built and live-verified
  the backend side.
- `DELETE /users/{id}/pin-devices/{deviceId}` (admin PIN revoke)'s
  authorization gating (`hasRole("PLATFORM_ADMIN")`) is verified by the
  automated integration test suite only, not live against the dev
  container — doing so would've needed `raitukashtam@gmail.com`'s
  password, which isn't recorded anywhere (by design, see 2026-08-29
  session).
- No "freshness" check on `POST /pin/register` — any valid Bearer token,
  however obtained, can register a device's PIN. Documented as an
  accepted v1 limitation in the implementation plan, not solved.

## Sessions

### 2026-08-31 (5)

- **Scoped and built `mycommunity-service` Phase 2: Announcements.**
  Prompted by the user asking for a phased plan across the full
  Mygate-style community-management feature set (society management,
  billing, visitors, helpdesk, communication, amenities, staff/vendor,
  documents, committee/RWA) — proposed a 13-phase roadmap prioritized by
  what the `mysociety` app's existing screens/quick-access cards actually
  need next, grounded in the prior validated plan
  (`~/.claude/plans/validated-rolling-pizza.md`) and this service's
  already-live Phase 1. User picked Phase 2 (Announcements) to start.
  - User then asked whether announcements could push a notification to
    residents' phones. Answered directly: no infrastructure for this
    exists anywhere in the repo (confirmed by grep) and the mobile app
    itself has no networking code yet to receive one — recommended
    building Announcements as pure data+API now and treating push
    delivery as its own later phase (cross-cutting, not specific to this
    feature). User agreed.
  - Built following the exact pattern of the existing Community/
    CommunityMember/CommunityJoinRequest trio: `Announcement` entity
    (title, body, `community` FK, `postedBy` → `CommunityMember` FK, not
    a raw identity id, so a display name is available without another
    auth-service call), `AnnouncementRepository`, `AnnouncementRequest`/
    `AnnouncementResponse`, `AnnouncementService` (delegates membership
    auth to `CommunityService.requireActiveMember`/`requireActiveAdmin`,
    same pattern `CommunityJoinRequestService` already uses), four new
    endpoints on the existing `CommunityController` under
    `/api/v1/communities/{id}/announcements` (`POST`/`GET` list/`GET` one
    /`DELETE`, all ADMIN-only except the two `GET`s), and
    `V3__announcement.sql`.
  - 7 new unit tests (`AnnouncementServiceTest`, mocked repos + a real
    `CommunityService` underneath, mirroring `CommunityJoinRequestServiceTest`'s
    own pattern) — all pass. Full suite: 28/28 except the pre-existing
    `MyCommunityServiceApplicationTests.contextLoads` (needs a live
    Postgres on `localhost:5433`, fails identically on unmodified `main`
    — confirmed via `git stash`, not a regression).
  - **Live-verified end-to-end** against the rebuilt dev container: `V3`
    applied (`flyway_schema_history` confirms). Obtained two real RS256
    JWTs by registering two brand-new `raitukashtam-web` accounts and
    driving the actual Authorization Code + PKCE flow with a throwaway
    scratchpad Python script (cookie-jar based, same sequence
    `PkceFlowClient` uses in auth-service's own tests) — no
    password-guessing or token-minting shortcut. First identity created a
    real community (id 2, "Phase2 Test Apartments") and became its ADMIN;
    second identity was invited as a member then linked via the existing
    `activate-invitations` endpoint, becoming an ACTIVE RESIDENT.
    Confirmed: ADMIN create → 201 with correct `postedByName` resolved;
    `GET` list/by-id → 200 for both ADMIN and RESIDENT; no token → 401;
    blank title → 400; nonexistent id → 404; RESIDENT attempting
    create/delete → 403 ("Admin role required for this operation"),
    verified the announcement was untouched afterward; ADMIN delete →
    204, confirmed gone from the list. Test community (id 2) and its two
    members left in the dev DB afterward (same precedent as the existing
    "Smoke Tester" community #1 from an earlier session) — harmless,
    not cleaned up.

### 2026-08-31 (4)

- **Audited every endpoint in auth-service against `SecurityConfig`**,
  prompted by the user asking "is authentication in place for every
  request?" after session (3)'s `PATCH /users/me` gap. Listed all ~24
  endpoints across every controller (`ClientController`,
  `ForgotPasswordController`, `GoogleController`, `HelloController`,
  `LoginPageController`, `OtpController`, `PinController`,
  `ProductController`, `RoleController`, `UserController`) and matched
  each against every matcher — confirmed everything currently public is
  intentionally so per its own `@Operation` doc ("Public, unauthenticated"
  on register/OTP/pin-login/Google/forgot-reset-password), and everything
  else has an explicit `authenticated()`/`hasRole()` matcher. No other
  live gap found.
  - But the model itself was the actual risk: `SecurityConfig` ended in
    `.requestMatchers("/**").permitAll()` *before* `.anyRequest()
    .authenticated()`, making that last line dead code — "public by
    default, list what needs auth" — the exact shape that let
    `PATCH /users/me` slip through unnoticed in the first place, and
    could silently repeat for any future endpoint someone forgets to gate.
    `mycommunity-service`'s own `SecurityConfig` already does the
    opposite (`anyRequest().authenticated()` as the real last rule, only
    `/actuator/**`+`/health`+`/error` permitted).
  - Flipped auth-service to match: removed the `/**` catch-all, replaced
    it with explicit `permitAll()` entries for exactly the endpoints
    confirmed genuinely public above (plus `/error`, previously covered
    implicitly by the same catch-all — `mycommunity-service` already
    permits this explicitly too), and let `.anyRequest().authenticated()`
    become the real, reachable default.
  - Full suite still 104/104 passing after the flip (nothing that should
    be public got accidentally locked out). **Verified live** against the
    rebuilt dev container: `GET /hello` (a trivial `@Hidden` smoke-test
    endpoint literally named "Hello, secured world!" — ironically public
    before this fix) now correctly `401`s; `POST /otp/generate` still
    `200`s; `GET /users/me` still `401`s with no token; `POST
    /users/register` with an empty body still reaches validation (`400`,
    not `401`).

### 2026-08-31 (3)

- **Reminded of, then closed, the account-level profile-update gap**
  surfaced while reviewing the `mysociety` app's remaining screens against
  the backend: `DashboardActivity`'s bottom nav has a `Profile` tab (see
  `bottom_nav.xml`), and unlike `mycommunity-service`'s just-added
  `PATCH .../members/me` (session (2) below), auth-service had no
  self-service way for a user to edit their own name/email at all —
  `UserController` only had `GET /users/me` (read-only), an admin-only
  `PATCH /users/{id}/platform-admin`, and an admin-only PIN-device revoke.
  - Added `PATCH /users/me` (`UpdateProfileRequest`: firstName/lastName/
    email, all optional — a null field is left unchanged, a present-but-
    blank one 400s via a new `InvalidRequestException` +
    `GlobalExceptionHandler` entry, matching the existing per-exception-
    type convention rather than reusing a generic one). Deliberately
    excludes `mobileNumber` (tied to OTP verification — changing it here
    without re-proving possession would be an account-takeover risk) and
    password (existing change-password flow already covers that).
    Doesn't touch `Identity.primaryEmail` — password-login username
    resolution goes through `User.email` (confirmed by reading
    `UserService.authenticate`/`findUserByEmail`), so scoped the change to
    the field that's actually authoritative rather than also touching a
    separate record nothing else in this codebase keeps in lockstep with.
  - **Found and fixed a real security gap while wiring this up, before it
    ever shipped**: none of the existing `SecurityConfig` matchers covered
    a bare `PATCH /users/me` (only `PATCH /users/*/platform-admin` was
    gated) — it would have fallen through to the trailing
    `.requestMatchers("/**").permitAll()`, reaching the controller with a
    `null` `Jwt` and NPE-ing into a generic 500 instead of a real 401 (not
    an actual authorization control, just an accident of a null-pointer
    crash). Added an explicit
    `.requestMatchers(HttpMethod.PATCH, "/users/me").authenticated()`
    matcher before implementing the controller method, not after.
  - Added 5 tests to `UserControllerApiTest` (no token → 401, updates
    name+email and leaves the rest unchanged, blank firstName → 400,
    invalid email format → 400, email already in use → 409). Full suite
    104/104 passing (was 99).
  - **Verified live end-to-end** against the rebuilt dev container via the
    same `newman`-driven PKCE approach used earlier this session: `PATCH`
    → 200 with the updated fields, a follow-up `GET /users/me` confirming
    persistence, and a blank-firstName request → 400 with the expected
    message. Note: this used the real `raitukashtam@gmail.com` platform
    admin account (the only real login available), so its `firstName` is
    now literally `"HariUpdated"` in the dev DB — harmless (dev-only,
    cosmetic), left as-is rather than guess at reverting to an unknown
    prior value.

### 2026-08-31 (2)

- **Extended `mycommunity-service`'s Phase 1 domain** to cover the real
  registration/onboarding flow described by the user (register a
  community, get forced into ADMIN, select-a-community-or-register
  branching, invited-member linking, self-service profile completion),
  planned collaboratively then implemented in one pass:
  - **Introduced Flyway to `mycommunity-service`** (previously Hibernate
    `ddl-auto` only, unlike auth-service) — `V1__baseline_schema.sql`
    (generated via `pg_dump --schema-only` against the live dev DB,
    cleaned up, matching auth-service's own baseline convention) +
    `V2__rename_owner_to_resident_and_join_requests.sql`. Dev profile
    switched from `ddl-auto: update` to `validate` (test/prod already
    were). Discovered along the way that Hibernate 6's `@Enumerated
    (STRING)` auto-generates a `CHECK` constraint from the enum's
    declared values (not documented behavior the team had relied on
    before) — confirmed via `pg_dump`, not assumed, before writing V2's
    constraint-drop/recreate.
  - **`CommunityRole.OWNER` renamed to `RESIDENT`** — a resident isn't
    necessarily the unit owner, and matches the user's actual spec
    ("every community will have two roles, ADMIN, RESIDENT"). Required a
    full dev DB reset (`docker compose down` + volume rm, done by the
    user directly — auto mode's classifier blocks a Bash tool call doing
    this) since the enum rename would otherwise strand old `'OWNER'` rows
    against a `CHECK` constraint that no longer allows that value.
  - **`CommunityRequest.adminMobile` removed** — was client-supplied,
    letting any caller name any mobile number as a community's admin.
    New `AuthServiceClient` (`GET {AUTH_SERVICE_URL}/users/me`, forwarding
    the caller's own Bearer token) resolves the real admin identity
    instead — reintroduces the auth-service call this service's own
    CLAUDE.md had flagged as removed since the `product-service` rename
    (Phase 1 never needed one). Also derives the admin's display name
    from auth-service's `firstName`/`lastName` instead of the hardcoded
    placeholder `"Community Admin"`.
  - **Duplicate-community prevention**: `POST /communities` now checks
    `(name, pincode)` (normalized) before creating, and 409s with a new
    `DuplicateCommunityException` → structured body
    (`existingCommunityId`/`existingCommunityName`) via a new
    `GlobalExceptionHandler` (`@RestControllerAdvice`) — the only
    exception in this service that needs one, since
    `ResourceNotFoundException`/`ResourceAlreadyExistsException` still
    use plain `@ResponseStatus` and don't need structured bodies.
  - **New join-request flow** for someone who hits that 409 and isn't a
    member yet: `CommunityJoinRequest` entity (`PENDING`/`APPROVED`/
    `REJECTED`, a partial unique index enforcing at most one `PENDING`
    request per identity per community) + `CommunityJoinRequestService` +
    four endpoints (`POST .../join-requests`, `GET .../join-requests`
    ADMIN-only, `POST .../join-requests/{id}/approve|reject` ADMIN-only).
    No notification channel exists (no push/SMS infra for this) — a
    pending request just sits there until an admin checks the list.
    `CommunityJoinRequestService` delegates membership-authorization
    checks (`requireActiveMember`/`requireActiveAdmin`) to
    `CommunityService` rather than duplicating that logic.
  - **`GET /communities/mine`** (list the caller's communities+role+status,
    for the mobile app's post-login "select your community" branch) and
    **`POST /communities/members/activate-invitations`** (resolves the
    caller's real mobile via `/users/me`, flips any matching `INVITED`
    rows to `ACTIVE`+linked `identityId` — the "invited member logs in
    for real" gap flagged as open since 2026-08-28 — and returns the same
    shape as `/mine`, so the mobile app can call this once right after
    every OTP login and get its full community list back in one call).
  - **`PATCH /communities/{id}/members/me`** — self-service `name`/
    `email`/`unitNumber` update for an `ACTIVE` member. `email` is a new
    nullable `CommunityMember` column. `unitNumber` was added to this
    beyond the user's literal ask (name+email) because a join-request-
    approved member starts with a placeholder `"-"` unit (unlike an
    admin-invited member, who gets a real one at invite time) and had no
    other way to ever correct it.
  - Updated `CommunityServiceTest` for the new signatures/rename, added
    `createCommunity_throwsDuplicateCommunity...`,
    `listMyCommunities_returnsMappedList`,
    `activateInvitations_linksInvitedMembersMatchingCallerMobile`,
    `updateMyProfile_*` (13 tests total, was 6), plus a new
    `CommunityJoinRequestServiceTest` (7 tests) covering create/list/
    approve/reject and their authorization/conflict branches. Full
    offline unit suite: 20/20 passing (the pre-existing `@SpringBootTest`
    context test, `MyCommunityServiceApplicationTests`, still needs its
    own local Postgres on 5433 to run at all — unrelated to this session,
    not attempted).
  - **Verified live end-to-end** against a freshly-reset dev container
    (Flyway V1+V2 applied cleanly on an empty schema, confirmed via
    `\d community_member`/`\d community_join_request` matching the
    intended schema exactly) using a real platform-admin Bearer token
    (via the same `newman`-driven PKCE approach as the OTP-login fix
    above): create → 201 with the admin's mobile correctly resolved from
    `/users/me` (not client-supplied); duplicate create → 409 with the
    structured body; `/mine` → 200 with the new membership; `activate-
    invitations` → 200 no-op (nothing to activate) confirming
    idempotency; `PATCH .../members/me` → 200 with name/email/unitNumber
    all updated; a join-request against one's own community → 409
    (already an active member); `POST .../members` (invite) → 201 with
    `role: "RESIDENT"`, `status: "INVITED"`. Approve/reject and the full
    invited-member-activation path are unit-tested only (see Open items).
  - Not built this session (deliberately out of scope, per the user's own
    "initial requirements" framing): the mobile app screens this backend
    work unblocks (Select-Community, Set-Up-PIN, wiring `OtpActivity`/
    `CommunityOnboardingActivity` for real), and the expenses feature.

### 2026-08-31

- **Fixed the broken `MYCOMMUNITY` `POST /otp/login`** flagged as an open
  item after the 2026-08-30 DB reset: the product had zero roles, so
  `RoleService.assignDefaultRole` 404'd on every OTP signup. Recreated the
  `CONSUMER` role (`POST /products/MYCOMMUNITY/roles` →
  `{"code":"CONSUMER","name":"Consumer"}`) using a real platform-admin
  Bearer token obtained by driving the actual Postman collection's
  `0. OAuth2 Login (PKCE)` folder headlessly via `newman` (hand-rolling the
  same PKCE dance with raw curl/openssl hit a `invalid_grant` — the
  collection's proven CryptoJS-based verifier/challenge logic worked on
  the first try, so used that instead of debugging the curl version
  further). Confirmed in the DB afterward: `MYCOMMUNITY` now has both
  `CONSUMER` (id 8) and `SUPPORT` (id 7, from 2026-08-30).
- **Live-verified the fix end-to-end**, not just via the DB check: dev's
  `TWOFACTOR_API_KEY` turned out to still be the placeholder
  `dev-2factor-key` (`OTPService`'s own code comment already flagged this
  — the real key had been rotated out of caution and never replaced), so
  `POST /otp/generate` 500'd with `Invalid API Key` on the first attempt.
  User supplied a real 2Factor.in API key; updated the repo-root `.env`
  (gitignored, not committed), force-recreated `vault-init` to reseed
  `secret/auth-service` with it (a plain restart doesn't re-run
  `vault-init`, per the 2026-08-28 session's own finding), then restarted
  `auth-service` to pick it up from Vault. With a real key in place, sent
  a real OTP by SMS to a test number, then called `POST /otp/login` with
  the received code: `200 OK` with a real session cookie, no 404. Confirmed
  in the DB: a `product_membership` row for `MYCOMMUNITY` (id 4, `ACTIVE`)
  with a `role_assignment` → `CONSUMER`.
- Explained to the user, grounded in the actual code
  (`OtpController`/`RoleService`/`OAuth2TokenClaimsCustomizer`), what role
  `/otp/login` actually uses `CONSUMER` for: purely to satisfy
  `assignDefaultRole`'s lookup so membership creation doesn't 404 — the
  session `/otp/login` establishes uses a hardcoded `ROLE_USER` authority,
  unrelated to it. The `CONSUMER` role only actually appears anywhere
  externally-visible later, in the JWT's `roles` claim, when the client
  continues to `/oauth2/token` — and since `mycommunity-service` doesn't
  read that claim today (established 2026-08-30), it currently has zero
  effect on authorization, matching what was already documented.

### 2026-08-30

- **Established `raitukashtam@gmail.com` as the standing convention for
  the first `PLATFORM_ADMIN` in every environment** (dev/test/prod) — same
  account `MAIL_USERNAME` already sends mail from. Updated all three
  `.env*.example` files, the real dev `.env`, and `backend/auth-service/
  CLAUDE.md` to default `PLATFORM_ADMIN_EMAIL` to it. Also documented (and
  live-verified) that multiple platform admins are fully supported —
  `identity.is_platform_admin` is a plain boolean with no uniqueness
  constraint, and `PATCH /users/{id}/platform-admin` (existing-admin-gated)
  is the normal way to add admin #2+, distinct from the restart-based
  bootstrap needed only for admin #1 in a fresh environment.
- **Found and fixed a real bug while building a Postman collection for
  auth-service**: `RegisterRequest.modifiedBy` was accepted as public,
  unauthenticated input on `POST /users/register` but silently discarded
  — `UserController`/`UserService.registerUser` never read it, and
  `createdBy` was already correctly server-derived from the registering
  user's own email. Removed the dead field (and an unrelated unused `UUID`
  import in the same file).
- **Generated a Postman collection for auth-service** from its live
  OpenAPI spec (`GET /v3/api-docs`), saved to `backend/auth-service/
  postman/auth-service.postman_collection.json`. Added a hand-built
  `0. OAuth2 Login (PKCE)` folder (4 chained requests with pre-request/
  test scripts) driving the real Authorization Code + PKCE flow, since
  Spring Authorization Server's framework-provided routes
  (`/oauth2/authorize`, `/login`, `/oauth2/token`) have no OpenAPI
  annotations and were missing from the auto-generated part.
- **Did a full local DB reset of `auth-service` only** (`docker compose
  down` + `docker volume rm auth-service_auth-pgdata` + `up -d`), at the
  user's request, to test the platform-admin bootstrap flow from a
  genuinely fresh environment. Confirmed all 12 (at the time) migrations
  reapply cleanly and `PlatformAdminSeeder` behaves exactly as documented
  on a truly empty `identity` table. Left `mycommunity-service`/`redis`/
  `vault` untouched. (Noted a stray leftover volume,
  `auth-service_pgdata` — no `auth-` prefix on `pgdata` — from before a
  past rename; not the one actually mounted, left alone.)
- **Saved a growing operational runbook outside the git repo**, in this
  session's Claude memory directory (`runbook_postman_admin_onboarding.md`,
  indexed in `MEMORY.md`) — click-by-click Postman procedures (onboarding
  a second platform admin, bootstrapping the first one, the full DB reset
  above) — deliberately not committed, since it's personal
  testing/operational instructions rather than project documentation.
- **Designed and built device-bound app-PIN login** ("Path B" from a
  design discussion about persisted mobile sessions — see the plan at
  `~/.claude/plans/tidy-greeting-lovelace.md`), so a mobile app whose only
  login method is OTP can re-authenticate via a short PIN afterward
  instead of repeating OTP every time, with the *server* (not the device)
  able to kill a specific lost/stolen device's access centrally:
  - New `CredentialType.DEVICE_PIN`, reusing `IdentityCredential`'s
    existing `externalSubject` (device id) and `passwordHash` (bcrypt PIN
    hash) columns — same shape `OTP_PHONE` already uses, no new table.
    Migration `V13__add_device_pin_credential_type.sql` widens
    `identity_credential`'s CHECK constraint (found by reading
    `V1__baseline_schema.sql` directly, not assumed from the Java enum
    mapping alone — the constraint would have rejected the new value
    otherwise).
  - New `PinController`: `POST /pin/register` (Bearer-authenticated,
    upserts a device's PIN — changing an existing device's PIN is just a
    re-register, a `deviceId` already owned by a *different* identity is
    a 409), `POST /pin/login` (public, mirrors `OtpController.
    loginWithOtp`'s exact session-authentication mechanism so the caller
    continues through the normal `/oauth2/authorize` PKCE flow
    afterward), `GET /pin/devices` + `DELETE /pin/devices/{deviceId}`
    (self-service list/revoke).
  - New `PinAttemptService` — Redis-backed per-device lockout (5 wrong
    PINs → 15 min lockout, `PinSecurityConfig`), separate from
    `RateLimiterService`'s existing per-IP request-volume limiting (both
    apply to `/pin/login`).
  - New admin endpoint on `UserController`,
    `DELETE /users/{id}/pin-devices/{deviceId}` (`PLATFORM_ADMIN`-gated)
    — the actual "kill a lost device centrally" capability that motivated
    choosing this design over a purely client-side PIN gate.
  - 14 new tests (`PinControllerApiTest`, mirroring `OtpControllerApiTest`/
    `UserControllerApiTest`'s existing patterns) — full suite now 99/99
    passing (was 85).
  - **Verified live end-to-end against the running dev container**, not
    just the test suite: registered a real user, password+PKCE login for
    a real access token, registered a device PIN with it, then — in a
    completely fresh cookie session with no prior authentication at all —
    logged in with just `deviceId`+`pin`, got a real session cookie back,
    and continued through `/oauth2/authorize`/`/oauth2/token` to a brand
    new access token, never re-entering the password. Also live-verified
    the 5-attempt lockout (6th wrong PIN, and even the correct PIN
    afterward, both 429 until the lockout window passes). Admin-revoke
    authorization was verified by the automated test suite only, not live
    (see Open items).
- **Found and fixed a real gap in the Platform Admin API's audit
  trail**: `POST /products`, `POST /products/{code}/roles`, and
  `POST /products/{code}/clients` all created rows with `created_by`/
  `modified_by` left `NULL`, regardless of who actually called them —
  none of `ProductController`/`RoleController`/`ClientController` even
  received the caller's `Jwt`, and none of `ProductService.createProduct`/
  `RoleService.createRole`/`ClientService.createClient` ever called
  `setCreatedBy`. User noticed by inspecting the DB after onboarding
  `MYCOMMUNITY` manually. Fixed by threading
  `@AuthenticationPrincipal Jwt jwt` through all three controllers,
  resolving it to the calling admin's **email** (via
  `IdentityRepository.findById(UUID.fromString(jwt.getSubject()))
  .getPrimaryEmail()`, falling back to the raw UUID if that somehow
  fails) rather than storing the raw UUID — matching the existing
  convention every self-service flow already uses (`UserService.
  registerUser` et al. store the registering email, not a UUID). Note
  this only changes the audit column; the JWT's own `sub` claim is
  unrelated and unchanged. `modified_by` staying `NULL` on create is
  separately confirmed to be consistent with the rest of the codebase —
  `setModifiedBy` is never called anywhere in this repo, for any entity,
  including the one real update endpoint that exists
  (`PATCH /users/{id}/platform-admin`) — not a Product-specific gap.
  Full suite still 99/99 after the fix; redeployed to the dev container
  and confirmed live.
- Deep-dived OAuth2 Authorization Code + PKCE mechanics end-to-end with
  the user (what each of the 4 Postman requests actually does, access/
  refresh token lifetimes, why public clients never get a refresh token,
  HTTP-session vs JWT distinctions, browser/mobile SSO), and the actual
  (lack of) responsibilities of an auth-service product-level `Role` —
  confirmed `mycommunity-service` never reads the JWT's `roles` claim at
  all, so today a product role like `MYCOMMUNITY`'s `CONSUMER` has zero
  functional effect on `mycommunity-service` behavior; its only real
  function is satisfying `RoleService.assignDefaultRole` so signup
  doesn't 404. See the auto-memory files this session added for how the
  user prefers to work through this material (hands-on, one product at a
  time) — not duplicated here since PROGRESS.md tracks project state, not
  collaboration style.

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
