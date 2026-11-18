# Merchant Module Implementation Summary

This document summarizes everything currently implemented in the **Merchant** module (`com.swiftdrop.merchant` on the backend and `frontend/app/(merchant)/` on the frontend) on branch `feature/merchant`.

---

## 1. Scope & Architecture

The merchant module implements the core merchant onboarding, API integration, order dashboard, and webhook subscription features (**MER-1** through **MER-4**, plus **ADM-3** admin approval endpoints) while adhering to [ADR 0003](adr/0003-backend-modules-and-boundaries.md):

- **Module dependencies:** `merchant` only imports public APIs from `identity.api` (`UserDirectory`), `delivery.api` (`ParcelCreation`, `ParcelQueries`, `NewMerchantParcel`, `CreatedParcel`, `DeliveryContact`, `ParcelStatus`, `ParcelSummary`, `PageRequest`, `PageResult`), and technical helpers in `shared` (`PageLimit`, `PageResponse`).
- **Cross-module references by ID:** Database foreign keys and JPA entities reference `users.id` and `parcels.id` by `UUID` only — no cross-module JPA entity relationships.
- **Security integration:** `com.swiftdrop.config.SecurityConfiguration` permits `POST /api/v1/merchants` publicly and registers `MerchantApiKeyFilter` (exposed in `merchant.api`) before `BearerTokenAuthenticationFilter`.
- **Lazy bean initialization:** Merchant controllers, services, and `MerchantApiKeyFilter` are marked `@Lazy` (with `ObjectProvider<MerchantApiKeyService>` inside `MerchantApiKeyFilter`) so existing non-merchant `@SpringBootTest` slice tests that exclude JPA auto-configuration can boot `SecurityConfiguration` without mocking merchant repositories.

---

## 2. Database Schema (Flyway Migrations)

Three Flyway SQL migrations exist under `backend/src/main/resources/db/migration/merchant/`:

| Migration File | Tables / Objects Created | Purpose |
| :--- | :--- | :--- |
| [V202611091200__merchant_create_merchants.sql](../backend/src/main/resources/db/migration/merchant/V202611091200__merchant_create_merchants.sql) | `merchants`, index `ix_merchants_status_created` | Stores merchant profile (`user_id` unique FK to `users`, `business_name`, `registration_number` unique, `contact_email`, `contact_phone`), `status` check (`PENDING_APPROVAL`, `APPROVED`, `REJECTED`, `SUSPENDED`), and review metadata (`reviewed_by`, `reviewed_at`, `rejection_reason`). |
| [V202611091210__merchant_create_api_keys.sql](../backend/src/main/resources/db/migration/merchant/V202611091210__merchant_create_api_keys.sql) | `merchant_api_keys`, index `ix_merchant_api_keys_merchant` | Stores hashed API keys (`key_prefix` unique 8-char prefix, BCrypt `key_hash`, `label`, `is_active`, `last_used_at`, `created_at`). |
| [V202611091220__merchant_create_webhooks.sql](../backend/src/main/resources/db/migration/merchant/V202611091220__merchant_create_webhooks.sql) | `merchant_webhook_endpoints`, `merchant_webhook_deliveries`, partial index `ix_webhook_deliveries_pending` | Stores subscribed webhook URLs (`url`, `secret_encrypted`, `is_active`) and delivery tracking records (`endpoint_id`, `merchant_id`, `parcel_id`, `event_id` with unique `(event_id, endpoint_id)`, `attempt_count`, status `PENDING`/`IN_FLIGHT`/`SUCCESS`/`FAILED`, `next_attempt_at`, `last_http_status`). |

---

## 3. Backend Implementation (`backend/src/main/java/com/swiftdrop/merchant/`)

### 3.1 Public Module API (`api/`)
- [MerchantLookup.java](../backend/src/main/java/com/swiftdrop/merchant/api/MerchantLookup.java) & [MerchantSummary.java](../backend/src/main/java/com/swiftdrop/merchant/api/MerchantSummary.java): Read-only cross-module lookup interface (`findById`, `findByUserId`, `isApproved`).
- [MerchantApiKeyFilter.java](../backend/src/main/java/com/swiftdrop/merchant/api/MerchantApiKeyFilter.java): `OncePerRequestFilter` applied only to `/api/v1/merchant-api/**`. Reads the `X-API-Key` header, verifies it via `MerchantApiKeyService`, and populates `SecurityContextHolder` with `MerchantApiKeyAuthenticationToken` granting `ROLE_MERCHANT_API`.

### 3.2 Domain Layer (`domain/`)
- [Merchant.java](../backend/src/main/java/com/swiftdrop/merchant/domain/Merchant.java), [MerchantStatus.java](../backend/src/main/java/com/swiftdrop/merchant/domain/MerchantStatus.java), [MerchantRepository.java](../backend/src/main/java/com/swiftdrop/merchant/domain/MerchantRepository.java): Aggregate managing registration state transitions (`approve`, `reject`, `suspend`, `isApproved`).
- [MerchantApiKey.java](../backend/src/main/java/com/swiftdrop/merchant/domain/MerchantApiKey.java), [MerchantApiKeyRepository.java](../backend/src/main/java/com/swiftdrop/merchant/domain/MerchantApiKeyRepository.java): Tracks key prefix, BCrypt hash, active state, `revoke()`, and `recordUsed()`.
- [WebhookEndpoint.java](../backend/src/main/java/com/swiftdrop/merchant/domain/WebhookEndpoint.java), [WebhookEndpointRepository.java](../backend/src/main/java/com/swiftdrop/merchant/domain/WebhookEndpointRepository.java): Tracks webhook URL, encoded secret, and soft deactivation.
- [WebhookDelivery.java](../backend/src/main/java/com/swiftdrop/merchant/domain/WebhookDelivery.java), [WebhookDeliveryRepository.java](../backend/src/main/java/com/swiftdrop/merchant/domain/WebhookDeliveryRepository.java): Tracks webhook delivery attempts and retry/failure state transitions (`recordSuccess`, `recordRetry`, `recordFailure`).

### 3.3 Application Services (`application/`)
- [MerchantOnboardingService.java](../backend/src/main/java/com/swiftdrop/merchant/application/MerchantOnboardingService.java): The single service for the merchant lifecycle. Registration checks registration number uniqueness, creates the `MERCHANT` user via `UserDirectory.createMerchantUser`, and persists a `PENDING_APPROVAL` profile. Admin review: `approve` and `reject` (only from `PENDING_APPROVAL`) and `suspend` (only from `APPROVED`); other states return 409. Also provides paginated listing with an optional status filter, `requireOwnMerchant(userId)` and `requireApproved(userId)`.
- [MerchantApiKeyService.java](../backend/src/main/java/com/swiftdrop/merchant/application/MerchantApiKeyService.java) & `GeneratedApiKey`: Generates `sd_live_` + 24 random bytes (48 hex chars), stores 8-char prefix + `PasswordEncoder` (BCrypt) hash, enforces `APPROVED` status and max `10` active keys per merchant, verifies incoming keys, and revokes keys.
- [MerchantOrderService.java](../backend/src/main/java/com/swiftdrop/merchant/application/MerchantOrderService.java): Builds `NewMerchantParcel` and delegates parcel creation to `ParcelCreation.createMerchantParcel` in `delivery`.
- [MerchantDashboardService.java](../backend/src/main/java/com/swiftdrop/merchant/application/MerchantDashboardService.java): Queries `ParcelQueries` (`findParcelsByMerchant`, `findByMerchantReference`) with clamped pagination.
- [WebhookEndpointService.java](../backend/src/main/java/com/swiftdrop/merchant/application/WebhookEndpointService.java): Enforces `APPROVED` status, validates `http`/`https` URLs with a valid host, enforces max `5` active endpoints per merchant, Base64-encodes the signing secret into `secret_encrypted`, lists active endpoints, and deactivates endpoints.
- [MerchantLookupService.java](../backend/src/main/java/com/swiftdrop/merchant/application/MerchantLookupService.java): Implements `MerchantLookup`.
- Exceptions: `MerchantNotFoundException`, `MerchantNotApprovedException`, `MerchantConflictException`, `RegistrationNumberAlreadyRegisteredException`, `ApiKeyLimitExceededException`.

### 3.4 Web Layer (`web/`)
- **Controllers:**
  - [MerchantController.java](../backend/src/main/java/com/swiftdrop/merchant/web/MerchantController.java) (`POST /api/v1/merchants`, `GET /api/v1/merchants/me`, `GET /api/v1/merchants`, `POST /api/v1/merchants/{id}/approval`, `.../rejection`, `.../suspension`)
  - [MerchantApiKeyController.java](../backend/src/main/java/com/swiftdrop/merchant/web/MerchantApiKeyController.java) (`GET/POST /api/v1/merchants/me/api-keys`, `DELETE /api/v1/merchants/me/api-keys/{id}`)
  - [MerchantApiController.java](../backend/src/main/java/com/swiftdrop/merchant/web/MerchantApiController.java) (`POST /api/v1/merchant-api/orders`)
  - [MerchantDashboardController.java](../backend/src/main/java/com/swiftdrop/merchant/web/MerchantDashboardController.java) (`GET /api/v1/merchants/me/orders`, `GET /api/v1/merchants/me/orders/{orderReference}`)
  - [WebhookEndpointController.java](../backend/src/main/java/com/swiftdrop/merchant/web/WebhookEndpointController.java) (`GET/POST /api/v1/merchants/me/webhooks`, `DELETE /api/v1/merchants/me/webhooks/{id}`)
- **Error Handling:** [MerchantExceptionHandler.java](../backend/src/main/java/com/swiftdrop/merchant/web/MerchantExceptionHandler.java) maps module exceptions to RFC 9457 `ProblemDetail` (`400`, `403`, `404`, `409`, `422`).
- **DTOs:** `RegisterMerchantRequest`, `RejectMerchantRequest`, `MerchantResponse`, `CreateApiKeyRequest`, `ApiKeyResponse`, `GeneratedApiKeyResponse`, `CreateMerchantOrderRequest`, `MerchantOrderAddress`, `MerchantOrderResponse`, `MerchantDashboardItemResponse`, `RegisterWebhookRequest`, `WebhookEndpointResponse`, `MerchantApiKeyAuthentication`.

---

## 4. Implemented REST Endpoints

| Method | Path | Auth / Role | Purpose |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/merchants` | Public | Register merchant user + `PENDING_APPROVAL` profile (409 on duplicate email or registration number) |
| `GET` | `/api/v1/merchants` | JWT (`ROLE_ADMIN`) | List merchants (optional `page`, `size`, `status`) |
| `POST` | `/api/v1/merchants/{id}/approval` | JWT (`ROLE_ADMIN`) | Approve a `PENDING_APPROVAL` merchant |
| `POST` | `/api/v1/merchants/{id}/rejection` | JWT (`ROLE_ADMIN`) | Reject a `PENDING_APPROVAL` merchant with a `reason` |
| `POST` | `/api/v1/merchants/{id}/suspension` | JWT (`ROLE_ADMIN`) | Suspend an `APPROVED` merchant |
| `GET` | `/api/v1/merchants/me` | JWT (`ROLE_MERCHANT`) | Get authenticated merchant's profile & status |
| `GET` | `/api/v1/merchants/me/api-keys` | JWT (`ROLE_MERCHANT`) | List active API keys (prefix + label, no secret) |
| `POST` | `/api/v1/merchants/me/api-keys` | JWT (`ROLE_MERCHANT`) | Generate API key (returns `rawKey` once) |
| `DELETE` | `/api/v1/merchants/me/api-keys/{id}` | JWT (`ROLE_MERCHANT`) | Revoke an active API key |
| `POST` | `/api/v1/merchant-api/orders` | `X-API-Key` (`ROLE_MERCHANT_API`) | Create parcel (`201 Created`) or idempotent retry (`200 OK`) |
| `GET` | `/api/v1/merchants/me/orders` | JWT (`ROLE_MERCHANT`) | Paginated orders with optional `status` filter |
| `GET` | `/api/v1/merchants/me/orders/{orderReference}` | JWT (`ROLE_MERCHANT`) | Look up order by merchant reference |
| `POST` | `/api/v1/merchants/me/orders/{orderReference}/holds` | JWT (`ROLE_MERCHANT`, approved) | Pause the order before pickup; optional body `{"reason"}` (max 500, kept in history only). `200` with the updated order, `409` if past `CREATED`, `404` if not this merchant's (ADR 0006 §1A) |
| `DELETE` | `/api/v1/merchants/me/orders/{orderReference}/holds` | JWT (`ROLE_MERCHANT`, approved) | Release a hold (`PAUSED` to `CREATED`); `409` unless paused |
| `POST` | `/api/v1/merchants/me/orders/{orderReference}/cancellations` | JWT (`ROLE_MERCHANT`, approved) | Cancel before pickup (final); `409` once picked up or already cancelled |
| `GET` | `/api/v1/merchant-api/orders/{merchantOrderReference}` | `X-API-Key` (`ROLE_MERCHANT_API`) | Store-facing status: current status, `lastSequence` and the full sequenced history (IDs, codes and times only, no recipient details). Only the key's own merchant's orders; others are `404`. Not reachable with a login token |

The order list and detail responses carry `allowedActions` (`PAUSE`, `RESUME`, `CANCEL`) so a client never has to know the lifecycle rules. `PAUSED` and `CANCELLED` are new order statuses; a courier can neither set nor leave them.
| `GET` | `/api/v1/merchants/me/webhooks` | JWT (`ROLE_MERCHANT`) | List active webhook subscriptions |
| `POST` | `/api/v1/merchants/me/webhooks` | JWT (`ROLE_MERCHANT`) | Register webhook URL + secret |
| `DELETE` | `/api/v1/merchants/me/webhooks/{id}` | JWT (`ROLE_MERCHANT`) | Deactivate a webhook endpoint |

---

## 5. Frontend Implementation (`frontend/app/(merchant)/`)

The Merchant Workspace portal routes are wrapped in [layout.tsx](../frontend/app/(merchant)/layout.tsx) using `PortalShell` (`role="MERCHANT"`):

- `/merchant` ([page.tsx](../frontend/app/(merchant)/merchant/page.tsx)): Loads `GET /api/v1/merchants/me`, shows business name, registration number, approval status badge (`APPROVED`, `REJECTED`, `PENDING_APPROVAL`), navigation cards, and a sample `curl` snippet for `POST /api/v1/merchant-api/orders`.
- `/merchant/orders` ([orders/page.tsx](../frontend/app/(merchant)/merchant/orders/page.tsx)): Fetches `GET /api/v1/merchants/me/orders` with status filter tabs (`ALL`, `CREATED`, `PAUSED` shown as "On hold", `PICKED_UP`, `IN_TRANSIT`, `OUT_FOR_DELIVERY`, `DELIVERED`, `FAILED`, `CANCELLED`) and pagination. Each row has Pause, Resume and Cancel buttons driven by the server's `allowedActions`; Pause and Cancel open a confirm dialog with an optional reason, and the tracking number links to the public tracking page.
- `/merchant/api-keys` ([api-keys/page.tsx](../frontend/app/(merchant)/merchant/api-keys/page.tsx)): Lists keys via `GET /api/v1/merchants/me/api-keys`, creates keys via `POST`, displays the one-time `rawKey` with a copy button, and revokes keys via `DELETE`.
- `/merchant/webhooks` ([webhooks/page.tsx](../frontend/app/(merchant)/merchant/webhooks/page.tsx)): Lists endpoints via `GET /api/v1/merchants/me/webhooks`, registers URL + secret via `POST`, and deactivates endpoints via `DELETE`.

---

## 6. Automated Tests (`backend/src/test/java/com/swiftdrop/`)

- **Merchant Unit & MockMvc Tests (13 test classes, 46 test methods):**
  - Domain: `MerchantTest`
  - Application: `MerchantApiKeyServiceTest`, `MerchantOnboardingServiceTest`, `MerchantDashboardServiceTest`, `MerchantOrderServiceTest`, `WebhookEndpointServiceTest`
  - Web: `MerchantAuthorizationTest` (role matrix for registration and admin review), `MerchantApiKeyApiTest`, `MerchantOrderApiTest`, `MerchantDashboardApiTest`, `WebhookEndpointApiTest`
- **Architecture & Convention Tests:**
  - `MigrationConventionTest` updated to assert all 3 merchant Flyway migrations.
  - `ModuleBoundaryTest` verifies `merchant` respects ADR 0003 boundaries.

*(All 476 backend tests passed on the last test run in `backend/` with `-Djacoco.skip=true` on JDK 25.)*

---

### 4.1 Store API request signatures (Phase 5)

Every request to `/api/v1/merchant-api/**` is authenticated with `X-API-Key`. A key created with
encryption configured also has a **signing secret**, returned once together with the key. The store
signs each request:

```
X-SwiftDrop-Timestamp: <unix seconds>
X-SwiftDrop-Signature: v1=hex(HMAC-SHA256(signingSecret, timestamp + "." + METHOD + "." + path + "." + sha256hex(body)))
```

`path` is the request path without the query string; `body` is empty for `GET`. The timestamp must be
within 5 minutes of the server clock. Signatures are optional while
`SWIFTDROP_MERCHANT_API_REQUIRE_SIGNATURE=false`; once it is `true`, every request must be signed and
keys without a secret are refused. A repeated order reference with the same content returns the existing
parcel (`200`); with different content it is refused (`409`), so an old request cannot silently change an
order. Store-API requests are limited to `SWIFTDROP_RATE_LIMIT_MERCHANT_API_PER_MINUTE` per client address.

Checked end to end on a running backend, with signatures optional and then required: a valid signature is
accepted; a wrong secret, a stale timestamp, a signature moved to a different body, a missing signature
(when required) and a key without a secret are refused; a reused reference with different content gets
`409`. Known limit: a captured signed request can be sent again within the 5-minute window; for orders this
returns the same parcel, but a nonce check is not implemented.

## 7. Remaining Work (Not Yet Implemented)

1. **Merchant webhook history view:** the backend is built (`GET /api/v1/merchants/me/webhook-deliveries` and `POST /api/v1/merchants/me/webhook-deliveries/{id}/redeliveries`, manual redelivery capped at 3), but the merchant webhooks page (`frontend/app/(merchant)/merchant/webhooks/page.tsx`) has no delivery history or redeliver control yet.
2. **Admin suspend button:** merchant signup (`/signup`, merchant tab) and approve/reject (Admin → Users) exist in the frontend; the admin UI has no button for `POST /api/v1/merchants/{id}/suspension` yet.
3. **Frontend Build / Lint Check:** `frontend` lint/typecheck has not yet been executed in this repository after installing `node_modules`.

