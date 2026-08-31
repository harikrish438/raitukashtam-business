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
  `community_join_request` table; `V3` added the `announcement` table
  (Phase 2); `V4` added the `bill` table (Phase 3); `V5` added the
  `payment` table (Phase 4); `V6` added the `expense` table (Phase 5);
  `V7` added the `visitor` table (Phase 7); `V8` added the `amenity` and
  `amenity_booking` tables (Phase 8); `V9` added the `complaint` and
  `complaint_comment` tables (Phase 9). Phase 6 (Dashboard aggregation)
  added no migration — it's a pure read-model over existing tables, no
  new schema. See auth-service's own
  Flyway convention (`baseline-on-migrate`/`baseline-version` in
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
- **Announcement** (Phase 2, new 2026-08-31): community (FK), title,
  body (text), postedBy (FK → `CommunityMember`, not a raw identity id,
  so a display name is available in the response without another
  auth-service call). ADMIN-only to create/delete; any ACTIVE member can
  read. Pure data+API — no push notification delivery (deliberately
  deferred to a later phase; see repo-root `PROGRESS.md`'s 2026-08-31
  session entry (5) for why).
- **Bill** (Phase 3, new 2026-08-31): community (FK), member (FK →
  `CommunityMember`), period (`YYYY-MM`), amount (`BigDecimal`), status
  (`PENDING`/`PAID`), dueDate, paidAt. One bill per member per period
  (unique constraint) — `POST .../bills/generate` creates one for every
  currently-ACTIVE member at a single flat amount per batch (`Community`
  has no per-unit size/area field yet to vary it by; that's Phase 12).
  409s if that period was already generated for the community.
- **Payment** (Phase 4, new 2026-08-31): community (FK), bill (FK →
  `Bill`, unique — one full payment per bill, no partial payments in v1),
  amount (copied from the bill at recording time), method (enum `CASH`/
  `BANK_TRANSFER`/`UPI`/`CHEQUE`/`OTHER`), reference (nullable —
  transaction id/cheque number), paidAt (optional in the request,
  defaults to now — lets an admin back-date a payment that happened
  earlier), recordedBy (FK → `CommunityMember`). Recording a payment
  flips its `Bill.status` to `PAID`. **Replaces** Phase 3's bare `PATCH
  .../bills/{id}/mark-paid`, which is removed — no external caller
  existed yet (mobile app still has no networking code), so this was a
  straight replacement rather than keeping two ways to mark a bill paid.
- **Expense** (Phase 5, new 2026-08-31): community (FK), category
  (free-text, not an enum — unlike `PaymentMethod`, expense categories
  are open-ended), description, amount (`BigDecimal`), expenseDate
  (optional in the request, defaults to today, back-datable, cannot be
  future-dated), createdByMember (FK → `CommunityMember`). **ADMIN-only
  end to end** — create, list, get, and delete all require ADMIN, per the
  user's spec noted back when Phase 1 was planned. Unlike Bills/Payments,
  expenses have no natural "my expenses" subset (they belong to the
  community as a whole, not an individual member), so there's no
  resident-facing read path at all, not even for their own reference.

**Dashboard (Phase 6, new 2026-08-31)**: no new entity — a derived/union
read-model over Community/CommunityMember/Bill/Payment/Expense/
Announcement, computed on request rather than a separate activity-log or
snapshot table (same "don't duplicate data that already exists elsewhere"
reasoning the original data-model plan used). `GET .../dashboard` is
**ADMIN-only**, matching every other financial-aggregate endpoint
(`listBills`/`listPayments`/`listExpenses` "all" views). Returns:
occupied/vacant units (from `CommunityMember` ACTIVE count vs
`Community.totalUnits`), pending dues total (sum of `PENDING` bills, all
periods), maintenance collected this calendar month (sum of `Payment`
where `paidAt` falls in it — cash-flow view, not tied to the bill's
`period`), this/last month expenses (sum of `Expense` by `expenseDate`),
a running community balance (all-time payments minus all-time expenses),
the 5 most recent announcements, and a merged/sorted "recent activity"
feed (top 10 of Payment+Announcement combined, newest first — Visitor
activity will join this union once that phase exists; fetching each
source's own top 10 before merging guarantees the true top 10 overall is
never missed). `AnnouncementService.toResponse` was made package-private
for this phase to reuse, same pattern as `BillService.requireBill`
earlier.
- **Visitor** (Phase 7, new 2026-08-31): community (FK), host (FK →
  `CommunityMember`, the resident who invited/is hosting this guest),
  guestName, type (enum `GUEST`/`DELIVERY`/`STAFF`/`OTHER`), purpose
  (optional free text), status (`EXPECTED`/`CHECKED_IN`/`CHECKED_OUT`),
  entryTime, exitTime. Unlike Announcements/Bills/Expenses, the natural
  actor creating a Visitor record is the **host resident themselves**
  (any ACTIVE member), not the community admin — matches the "Add
  Visitor" quick-access card being available to every user, not just
  admins. `POST .../visitors` supports two flows in one call via a
  `checkedInNow` flag: pre-approval (`EXPECTED`, no entryTime — "I'm
  expecting a guest later") when false/omitted, or an already-arrived
  walk-in logged after the fact (`CHECKED_IN`, entryTime=now) when true.
  `check-in`/`check-out` transition the lifecycle (`EXPECTED`→
  `CHECKED_IN`→`CHECKED_OUT`, 409 if called out of order) and are
  callable by **either the host or an ADMIN** — this system has no
  dedicated gate-guard role yet, so ADMIN stands in for that at the gate.
  ADMIN additionally sees every visitor in the community
  (`GET .../visitors`); a resident only sees their own
  (`GET .../visitors/mine`) — same visibility split as Bills/Payments.
- **Amenity** (Phase 8, new 2026-08-31): community (FK), name,
  description, paid (boolean), fee (`BigDecimal`, only meaningful when
  paid — **informational only, no payment collection wired up to it**;
  a paid amenity's actual fee collection would go through the existing
  Bill/Payment system manually, out of band, not a new specialized flow),
  rules (free text), active (boolean, default true). ADMIN manages
  (create/deactivate), any ACTIVE member browses
  (`GET .../amenities`/`{id}`). No hard delete — an Amenity with booking
  history can't be removed without breaking that history, so retiring
  one soft-deactivates it (`PATCH .../amenities/{id}/deactivate`)
  instead; no reactivate endpoint yet (not asked for, trivial to add).
- **AmenityBooking** (Phase 8, new 2026-08-31): community (FK), amenity
  (FK), member (FK, the booker), bookingDate, slot (free-text label like
  `"18:00-19:00"`, not a structured time range), status (`PENDING`/
  `APPROVED`/`REJECTED`/`CANCELLED` — mirrors `CommunityJoinRequest`'s
  lifecycle, a deliberate reuse of an established pattern rather than a
  new one). Any ACTIVE member books for themselves
  (`POST .../amenities/{id}/bookings`, rejected 409 if the amenity is
  inactive or that amenity/date/slot combination already has a
  PENDING-or-APPROVED booking); ADMIN approves/rejects
  (`POST .../amenity-bookings/{id}/approve`/`reject`, 409 unless
  currently PENDING); the booker **or** ADMIN can cancel
  (`POST .../amenity-bookings/{id}/cancel`, 409 unless currently
  PENDING/APPROVED) — same booker-or-admin authorization shape Phase 7
  (Visitors) established for check-in/check-out. ADMIN sees every
  booking in the community (`GET .../amenity-bookings`); a resident sees
  only their own (`GET .../amenity-bookings/mine`).
- **Complaint** (Phase 9, new 2026-08-31): community (FK), raisedBy (FK →
  `CommunityMember`), category (free text, not an enum — same "too
  open-ended" reasoning as `Expense.category`), title, description,
  priority (enum `LOW`/`MEDIUM`/`HIGH`/`URGENT`, defaults to `MEDIUM`),
  status (enum `OPEN`/`IN_PROGRESS`/`RESOLVED`/`CLOSED` — **strictly
  linear, one step at a time**, `PATCH .../complaints/{id}/status` 409s
  on any skip or backward move, checked via enum ordinal + 1; no reopen
  in this phase), assignedTo (FK → `CommunityMember`, nullable — any
  ACTIVE member, not role-restricted to ADMIN, since this system has no
  staff/committee role yet). Any ACTIVE member raises one for themselves
  (`POST .../complaints`); ADMIN triages
  (`PATCH .../complaints/{id}/assign`, `PATCH .../complaints/{id}/status`).
  Visibility (`GET .../complaints/{id}`) is **ADMIN, the raiser, or the
  current assignee** — a deliberate widening of the owner-or-admin shape
  used elsewhere, since an assignee needs to see a ticket to work it.
  ADMIN sees every complaint (`GET .../complaints`); a resident sees only
  their own (`GET .../complaints/mine`). SLA/TAT tracking and reporting
  are deliberately not built here — a natural Dashboard-style follow-up
  once this data exists, not this phase's job.
- **ComplaintComment** (Phase 9, new 2026-08-31): complaint (FK), author
  (FK → `CommunityMember`), comment (text). Visibility for both adding
  (`POST .../complaints/{id}/comments`) and reading
  (`GET .../complaints/{id}/comments`) reuses the parent Complaint's own
  visibility rule exactly (`ComplaintService.requireVisibleToCaller`,
  package-private, reused rather than duplicated — same convention as
  `BillService.requireBill`/`AnnouncementService.toResponse`/
  `AmenityService.requireAmenity` earlier).

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
| POST | `/api/v1/communities/{id}/announcements` | Caller must be an ACTIVE ADMIN |
| GET | `/api/v1/communities/{id}/announcements` | Caller must be an ACTIVE member; newest first |
| GET | `/api/v1/communities/{id}/announcements/{announcementId}` | Caller must be an ACTIVE member |
| DELETE | `/api/v1/communities/{id}/announcements/{announcementId}` | Caller must be an ACTIVE ADMIN |
| POST | `/api/v1/communities/{id}/bills/generate` | Caller must be an ACTIVE ADMIN; one Bill per currently-ACTIVE member; 409 if the period was already generated |
| GET | `/api/v1/communities/{id}/bills` | Caller must be an ACTIVE ADMIN; all bills in the community |
| GET | `/api/v1/communities/{id}/bills/mine` | Any ACTIVE member; their own bills only |
| GET | `/api/v1/communities/{id}/bills/{billId}` | Caller must be an ACTIVE ADMIN, or the bill's own member |
| POST | `/api/v1/communities/{id}/bills/{billId}/payments` | Caller must be an ACTIVE ADMIN; 409 if the bill is already PAID; flips the bill to PAID |
| GET | `/api/v1/communities/{id}/bills/{billId}/payment` | Caller must be an ACTIVE ADMIN, or the bill's own member; 404 if no payment recorded yet |
| GET | `/api/v1/communities/{id}/payments` | Caller must be an ACTIVE ADMIN; all payments in the community |
| GET | `/api/v1/communities/{id}/payments/mine` | Any ACTIVE member; their own payments only |
| POST | `/api/v1/communities/{id}/expenses` | Caller must be an ACTIVE ADMIN |
| GET | `/api/v1/communities/{id}/expenses` | Caller must be an ACTIVE ADMIN; newest expenseDate first |
| GET | `/api/v1/communities/{id}/expenses/{expenseId}` | Caller must be an ACTIVE ADMIN |
| DELETE | `/api/v1/communities/{id}/expenses/{expenseId}` | Caller must be an ACTIVE ADMIN |
| GET | `/api/v1/communities/{id}/dashboard` | Caller must be an ACTIVE ADMIN |
| POST | `/api/v1/communities/{id}/visitors` | Any ACTIVE member; host = caller; `checkedInNow` picks EXPECTED vs CHECKED_IN |
| GET | `/api/v1/communities/{id}/visitors` | Caller must be an ACTIVE ADMIN; all visitors in the community |
| GET | `/api/v1/communities/{id}/visitors/mine` | Any ACTIVE member; their own hosted visitors only |
| GET | `/api/v1/communities/{id}/visitors/{visitorId}` | Caller must be an ACTIVE ADMIN, or the visitor's host |
| POST | `/api/v1/communities/{id}/visitors/{visitorId}/check-in` | Caller must be the host or an ACTIVE ADMIN; 409 unless EXPECTED |
| POST | `/api/v1/communities/{id}/visitors/{visitorId}/check-out` | Caller must be the host or an ACTIVE ADMIN; 409 unless CHECKED_IN |
| POST | `/api/v1/communities/{id}/amenities` | Caller must be an ACTIVE ADMIN; 400 if paid with no fee |
| GET | `/api/v1/communities/{id}/amenities` | Any ACTIVE member |
| GET | `/api/v1/communities/{id}/amenities/{amenityId}` | Any ACTIVE member |
| PATCH | `/api/v1/communities/{id}/amenities/{amenityId}/deactivate` | Caller must be an ACTIVE ADMIN; 409 if already inactive |
| POST | `/api/v1/communities/{id}/amenities/{amenityId}/bookings` | Any ACTIVE member; booker = caller; 409 if amenity inactive or slot already taken |
| GET | `/api/v1/communities/{id}/amenity-bookings` | Caller must be an ACTIVE ADMIN; all bookings in the community |
| GET | `/api/v1/communities/{id}/amenity-bookings/mine` | Any ACTIVE member; their own bookings only |
| GET | `/api/v1/communities/{id}/amenity-bookings/{bookingId}` | Caller must be an ACTIVE ADMIN, or the booking's own member |
| POST | `/api/v1/communities/{id}/amenity-bookings/{bookingId}/approve` | Caller must be an ACTIVE ADMIN; 409 unless PENDING |
| POST | `/api/v1/communities/{id}/amenity-bookings/{bookingId}/reject` | Caller must be an ACTIVE ADMIN; 409 unless PENDING |
| POST | `/api/v1/communities/{id}/amenity-bookings/{bookingId}/cancel` | Caller must be the booker or an ACTIVE ADMIN; 409 unless PENDING/APPROVED |
| POST | `/api/v1/communities/{id}/complaints` | Any ACTIVE member; raiser = caller |
| GET | `/api/v1/communities/{id}/complaints` | Caller must be an ACTIVE ADMIN; all complaints in the community |
| GET | `/api/v1/communities/{id}/complaints/mine` | Any ACTIVE member; their own raised complaints only |
| GET | `/api/v1/communities/{id}/complaints/{complaintId}` | Caller must be an ACTIVE ADMIN, the raiser, or the current assignee |
| PATCH | `/api/v1/communities/{id}/complaints/{complaintId}/assign` | Caller must be an ACTIVE ADMIN; 404 if assignee isn't a member of this community |
| PATCH | `/api/v1/communities/{id}/complaints/{complaintId}/status` | Caller must be an ACTIVE ADMIN; 409 unless advancing exactly one step |
| POST | `/api/v1/communities/{id}/complaints/{complaintId}/comments` | Caller must be an ACTIVE ADMIN, the raiser, or the current assignee |
| GET | `/api/v1/communities/{id}/complaints/{complaintId}/comments` | Caller must be an ACTIVE ADMIN, the raiser, or the current assignee |

A 13-phase roadmap for the remaining feature areas (staff/vendor,
documents, structured units, committee/RWA, push notification delivery)
was agreed with the user — see repo-root
`PROGRESS.md`'s 2026-08-31 session entry (5). The original full data
model sketch for these is in `~/.claude/plans/validated-rolling-pizza.md`
(from the session Phase 1 was planned in) or ask for it if that file
isn't available.

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
