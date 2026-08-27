# auth-service as a Multi-Product Identity Platform

Status: Approved direction (custom build), Phase 0 complete
Owner: auth-service
Last updated: 2026-08-18

## 1. Goal

`auth-service` today authenticates users for a single product (raitukashtam)
with a single client type (implicitly, one web/API surface). The platform
ambition is different: this service should be able to onboard **multiple,
independently-owned, business-unrelated software products**, each of which
may have **multiple client apps** (web/React, Android, iOS, backend
services) calling it.

This document records why the current schema cannot do that, the target
architecture, and the phased migration plan. It is a living design doc —
update it as phases land or assumptions change.

## 2. Why the current design doesn't generalize

- `User.role` is a single-column Java enum (`ADMIN, FARMER, BUYER,
  DELIVERY_PARTNER, CONSUMER`) compiled into the JAR. It encodes one
  product's business vocabulary directly into the identity core. A second,
  unrelated product has nowhere to put its own roles without polluting
  this enum or forking the service.
- `Tenant` (`name`, `code`, `region`, `pincode`) is not a product boundary —
  it's a geography/org field left over from thinking about raitukashtam
  specifically. There is no table that represents "a distinct onboarded
  software product."
- There is no concept of a **client app** anywhere. No `client_id`, no
  redirect URI registry, no distinction between a public client (SPA/mobile
  — needs PKCE, no secret) and a confidential client (backend service —
  can hold a secret). Every token is minted with one hardcoded audience
  (`jwt.audience: raitukashtam-client`), regardless of which product or
  platform asked for it.
- `User.role` is a single value, not a set — a person cannot hold different
  roles in different products, or multiple roles within one product.
- Credentials are conflated with identity: `User.password` assumes password
  is the only login method. Google OAuth is bolted on as a disconnected
  controller (`GoogleController`) that verifies a token and does nothing
  with the result — no identity is created or linked.

These aren't independent nits — they all stem from the same root cause:
**the schema has no representation of "product" or "client app" at all**,
so there's structurally nowhere to put per-product roles even if the enum
were fixed.

## 3. Target model

Four concepts, cleanly separated:

1. **Identity** — a human, independent of any product. Who they are.
2. **Credential** — how that identity proves who they are (password,
   Google OAuth, passkey, phone OTP, ...). Multiple credentials per
   identity.
3. **Product** — one onboarded software system (what `Tenant` should have
   been). raitukashtam is Product #1.
4. **Client** — one app belonging to one Product: `raitukashtam-web`,
   `raitukashtam-android`, `raitukashtam-ios`, or a backend service. Each
   has its own `client_id`, grant type, redirect URIs, token TTLs.

Roles belong to **Product**, not to the identity core — auth-service
stores and asserts role assignments as opaque, product-scoped strings; it
does not know or care what a role authorizes. That authorization logic
lives downstream, in each product's own services.

### 3.1 Schema sketch

```
identity(id UUID pk, primary_email, primary_phone, status, created_at)

identity_credential(id pk, identity_id fk, credential_type[PASSWORD|GOOGLE|APPLE|PASSKEY|OTP_PHONE],
                     external_subject nullable,   -- e.g. Google's `sub`
                     password_hash nullable,
                     verified bool)
   unique(credential_type, external_subject)

product(id pk, code unique, name, status, created_at)

client(id pk, product_id fk, client_id unique, client_type[WEB_SPA|ANDROID|IOS|BACKEND_SERVICE],
       client_secret_hash nullable,   -- null for public clients (SPA/mobile) -- PKCE instead
       redirect_uris, allowed_grant_types, access_token_ttl, refresh_token_ttl)

product_membership(id pk, identity_id fk, product_id fk, status[ACTIVE|SUSPENDED|PENDING], joined_at)
   unique(identity_id, product_id)

role(id pk, product_id fk, code, name)
   unique(product_id, code)

role_assignment(id pk, product_membership_id fk, role_id fk, granted_at, granted_by)

refresh_token(id pk, identity_id fk, product_id fk, client_id fk,
              token_hash,          -- never store the raw token
              family_id,           -- rotation chain; reuse revokes the whole family
              device_info, issued_at, expires_at, revoked)
```

A separate **platform role** axis (e.g. `PLATFORM_ADMIN`, "who can onboard
a new product") is intentionally kept out of the per-product `role` table —
mixing platform-operator roles with product-business roles is exactly the
mistake `UserRole` already made once.

### 3.2 Token design

- `aud` = the real `client_id`, not one global string. A resource server
  can reject a token minted for a different client/product.
- `sub` = identity UUID, never email (emails change).
- `type` claim (`access`/`refresh`/`password_reset`) is generated **and
  checked** at validation time — today it's generated but never enforced,
  meaning a refresh token currently authenticates like an access token
  everywhere.
- Refresh tokens: store only a hash (same discipline as a password), rotate
  on every use, detect reuse by revoking the entire `family_id` — turns a
  stolen refresh token into a one-time nuisance instead of a standing
  backdoor.
- Client-appropriate flows: Authorization Code + PKCE for web/mobile (no
  client secret — a secret embedded in an APK or JS bundle isn't one);
  `client_credentials` for backend-service integrations.

## 4. Phased migration plan

Each phase ships independently; raitukashtam keeps working throughout.

### Phase 0 — Fix the foundation before scaling it (in progress)
- Enforce the `type` claim on token validation.
- Hash refresh tokens at rest; rotate on use; reuse-detection via
  token family.
- Wire up (or remove) account locking — currently dead/commented-out code.
- Fix registration validation (missing `@Valid`, broken name regex).

### Phase 1 — Introduce `Product` as the real tenancy boundary
- Add `product` table; migrate raitukashtam in as the first row.
- Fold the existing `Tenant` concept into an optional sub-scope *within*
  a Product (for a future B2B product needing org-level tenancy), not the
  top-level boundary. Existing users become `product_membership` rows
  against the `raitukashtam` product.

### Phase 2 — Split `User` into `Identity` + `Credential`
- New `identity` / `identity_credential` tables; migrate `User.password`
  into a `PASSWORD` credential row.
- Fix Google OAuth to actually create/link an identity via a `GOOGLE`
  credential row keyed on `external_subject`.

### Phase 3 — Roles become data, scoped per Product
- Replace the `UserRole` enum with `role` (scoped by `product_id`) and
  `role_assignment` (many-to-many via `product_membership`).
- Migrate existing roles into rows under the `raitukashtam` product.
- Introduce the separate `PLATFORM_ADMIN`-style platform role axis.
- Token issuance puts roles in the token scoped to the product the token
  was issued for.

### Phase 4 — `Client` registry and per-platform token scoping
- Add `client` table; register `raitukashtam-web`, `raitukashtam-android`,
  `raitukashtam-ios`, and any backend-service caller.
- Move web/mobile to Authorization Code + PKCE; `aud` becomes the real
  `client_id`.

### Phase 5 — Product onboarding workflow
- Admin API (guarded by `PLATFORM_ADMIN`) to register a new Product + its
  Clients + seed initial Roles — turns the schema into a repeatable
  onboarding operation.

## 5. Non-goals (for now)

- Fine-grained permission/policy engine (ABAC, OPA-style rules) — role
  assignment is coarse-grained by design; each product's own services own
  fine-grained authorization.
- Multi-region / data-residency partitioning.
- SCIM or enterprise directory sync.

These may become real requirements later, but are explicitly out of scope
for the phases above.
