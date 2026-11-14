# RBAC & User Management — Authorization and Token Reference

Scope: identity (users, roles, JWT), merchant onboarding approval, courier registration
approval, and the security configuration that enforces them. Module boundaries follow ADR 0003.

## Roles

Exactly four roles exist; there is no separate dispatcher role:

| Role | Who gets it | How |
|---|---|---|
| `CUSTOMER` | Public sign-up | `POST /api/v1/users` (role is never client-settable) |
| `MERCHANT` | Public sign-up | `POST /api/v1/merchants` → user + `PENDING` merchant row |
| `COURIER` | Public sign-up **or** admin-created | `POST /api/v1/courier-registrations` (approval required) or `POST /api/v1/staff-users` (admin, immediate) |
| `DISPATCHER_ADMIN` | Admin-created only | `POST /api/v1/staff-users` or env bootstrap (`BootstrapAdminService`) |

## Token lifecycle

- Access tokens are JWTs (HS256, `roles` claim, issuer checked), TTL `PT15M`
  (`SWIFTDROP_JWT_ACCESS_TOKEN_TTL`). Stored in `sessionStorage` on the frontend.
- **No refresh tokens.** Rationale: the TTL is short, re-login is cheap, and refresh-token
  rotation/storage adds complexity disproportionate to this scope. Logout discards the token
  client-side; there is no server-side session to invalidate.
- **Revocation is per-request, not per-token.** `AccountStatusJwtAuthenticationConverter`
  (config package, wraps the standard JWT converter) re-validates account state on every
  authenticated request via `UserDirectory`:
  - unknown or `enabled=false` user → `BadCredentialsException` → **401**
  - courier approval row present and not `APPROVED` → `ROLE_COURIER` stripped → courier
    endpoints return **403**
  - Cost: one indexed `users` PK lookup per request; the approval lookup only runs when the
    token carries `ROLE_COURIER`. Admin-created couriers have no approval row and keep access.
- `AccessTokenIssuer.effectiveRoles()` applies the same approval rule at issuance, so pending
  couriers never receive `ROLE_COURIER` in the first place; the per-request check additionally
  makes approval *revocation* effective on the very next request (no re-login needed).
- Google OAuth login/exchange flows issue tokens through the same `AccessTokenIssuer` and are
  covered by identical rules.

## Endpoint permission matrix

`permitAll` (no token): `POST /api/v1/users`, `POST /api/v1/auth/tokens`,
`POST /api/v1/auth/google/exchanges`, `POST /api/v1/merchants`,
`POST /api/v1/courier-registrations`, `GET /api/v1/tracked-parcels/**` (public tracking by
design), actuator health, OpenAPI/Swagger, `/error`.

Everything else requires a valid, enabled-account JWT. Method security adds role checks.

| Endpoint | CUSTOMER | MERCHANT | COURIER | DISPATCHER_ADMIN | Anonymous |
|---|---|---|---|---|---|
| `POST /users`, `/auth/*`, `POST /merchants`, `POST /courier-registrations` | 201 | 201 | 201 | 201 | allowed |
| `GET /users/me` | 200 | 200 | 200 (+`courierApprovalStatus`) | 200 | 401 |
| `GET /users` (list), `PATCH /users/{id}` | 403 | 403 | 403 | 200 | 401 |
| `POST /users/staff`, `POST /staff-users` | 403 | 403 | 403 | 201 | 401 |
| `GET /merchants` list, `POST /{id}/approval|rejection` | 403 | 403 | 403 | 200 | 401 |
| `GET /merchants/me` | 404 | 200 (pending ok) | 404 | 404 | 401 |
| `GET /courier-registrations` (admin list) | 403 | 403 | 403 | 200 | 401 |
| `POST /courier-registrations/{userId}/approval|rejection` | 403 | 403 | 403 | 200 | 401 |
| `GET /courier-registrations/me` | 404 | 404 | 200/404 | 404 | 401 |
| `GET /courier/parcels`, `POST /parcels/{id}/status-changes`, `PUT /parcels/{id}/proof` | 403 | 403 | 200 if approved else 403 | 403 | 401 |
| Own parcels / delivery booking | own only | — | — | — | — |

Notes on non-403/404 outcomes:

- **404** is used intentionally for resources outside a caller's scope (e.g. a courier
  requesting another courier's parcel) so existence is not disclosed.
- **409** on repeated approval/rejection transitions and duplicate emails.
- **400** on bean-validation failures and invalid enum filters.
- **401** for missing/invalid/expired/tampered tokens, disabled accounts, and disabled
  `/users/me` lookups.
- **403** for authenticated users lacking the required role or effective authority.
- **429** when rate limits are exceeded (see below).

## Rate limiting

`RateLimitingFilter` (config package) applies fixed-window per-IP limits, returning **429**
with an RFC 9457 problem body. In-memory, per application instance.

| Group | Paths | Default |
|---|---|---|
| auth | `POST /api/v1/auth/tokens`, `POST /api/v1/auth/google/exchanges` | 10 req/min/IP |
| registration | `POST /api/v1/users`, `POST /api/v1/merchants`, `POST /api/v1/courier-registrations` | 5 req/min/IP |

Configure via `SWIFTDROP_RATE_LIMIT_AUTH_PER_MINUTE` and
`SWIFTDROP_RATE_LIMIT_REGISTRATION_PER_MINUTE` (properties
`swiftdrop.rate-limit.*`). Only these POST routes are limited; all other traffic is unaffected.

## Approval states

- `courier_approvals` (identity module): one row per publicly registered courier,
  `user_id` PK, `PENDING → APPROVED | REJECTED`, optimistic `@Version` locking,
  `reviewed_by`/`reviewed_at`/`rejection_reason` audit fields. Admin-created couriers have
  no row and are treated as approved.
- `merchants` (merchant module): `PENDING → APPROVED | REJECTED | SUSPENDED`;
  capability endpoints call `requireApproved()`.
- Approval/rejection endpoints are `DISPATCHER_ADMIN`-only; only `PENDING` rows can
  transition (409 otherwise). A courier cannot approve their own registration.

## `/users/me` response

Returns assigned DB `roles` plus `courierApprovalStatus` (`PENDING`/`APPROVED`/`REJECTED`,
`null` for accounts without a courier approval row). Assigned roles are kept for frontend
compatibility; effective authority is enforced by the token converter. Merchant approval
status is exposed by the merchant module's own `GET /api/v1/merchants/me`.
