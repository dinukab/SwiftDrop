# Merchant Module Implementation Summary

This document summarizes everything currently implemented in the **Merchant** module (`com.swiftdrop.merchant` on the backend and `frontend/app/(merchant)/` on the frontend) on branch `feature/merchant`.

---

## 1. Scope & Architecture

The merchant module implements the core merchant onboarding, API integration, order dashboard, and webhook subscription features (**MER-1** through **MER-4**, plus **ADM-3** admin approval endpoints) while adhering to [ADR 0003](adr/0003-backend-modules-and-boundaries.md):

- **Module dependencies:** `merchant` only imports public APIs from `identity.api` (`UserDirectory`), `delivery.api` (`ParcelCreation`, `ParcelQueries`, `NewMerchantParcel`, `CreatedParcel`, `DeliveryContact`, `ParcelStatus`, `ParcelSummary`, `PageRequest`, `PageResult`), and technical helpers in `shared` (`PageLimit`, `PageResponse`).
- **Cross-module references by ID:** Database foreign keys and JPA entities reference `users.id` and `parcels.id` by `UUID` only — no cross-module JPA entity relationships.
- **Security integration:** `com.swiftdrop.config.SecurityConfiguration` permits `POST /api/v1/merchants/register` publicly and registers `MerchantApiKeyFilter` (exposed in `merchant.api`) before `BearerTokenAuthenticationFilter`.
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
- [MerchantRegistrationService.java](../backend/src/main/java/com/swiftdrop/merchant/application/MerchantRegistrationService.java) & `MerchantRegistrationCommand`: Checks registration number uniqueness, creates the `MERCHANT` user via `UserDirectory.createMerchantUser`, and persists a `PENDING_APPROVAL` merchant profile.
- [MerchantApprovalService.java](../backend/src/main/java/com/swiftdrop/merchant/application/MerchantApprovalService.java): Admin review operations (`approve`, `reject`, `suspend`), paginated merchant listing with optional status filter, and profile lookup by ID or `userId`.
- [MerchantApiKeyService.java](../backend/src/main/java/com/swiftdrop/merchant/application/MerchantApiKeyService.java) & `GeneratedApiKey`: Generates `sd_live_` + 24 random bytes (48 hex chars), stores 8-char prefix + `PasswordEncoder` (BCrypt) hash, enforces `APPROVED` status and max `10` active keys per merchant, verifies incoming keys, and revokes keys.
- [MerchantOrderService.java](../backend/src/main/java/com/swiftdrop/merchant/application/MerchantOrderService.java): Builds `NewMerchantParcel` and delegates parcel creation to `ParcelCreation.createMerchantParcel` in `delivery`.
- [MerchantDashboardService.java](../backend/src/main/java/com/swiftdrop/merchant/application/MerchantDashboardService.java): Queries `ParcelQueries` (`findParcelsByMerchant`, `findByMerchantReference`) with clamped pagination.
- [WebhookEndpointService.java](../backend/src/main/java/com/swiftdrop/merchant/application/WebhookEndpointService.java): Enforces `APPROVED` status, validates `http`/`https` URLs with a valid host, enforces max `5` active endpoints per merchant, Base64-encodes the signing secret into `secret_encrypted`, lists active endpoints, and deactivates endpoints.
- [MerchantLookupService.java](../backend/src/main/java/com/swiftdrop/merchant/application/MerchantLookupService.java): Implements `MerchantLookup`.
- Exceptions: `MerchantNotFoundException`, `MerchantNotApprovedException`, `RegistrationNumberAlreadyRegisteredException`, `ApiKeyLimitExceededException`.

### 3.4 Web Layer (`web/`)
- **Controllers:**
  - [MerchantRegistrationController.java](../backend/src/main/java/com/swiftdrop/merchant/web/MerchantRegistrationController.java) (`POST /api/v1/merchants/register`)
  - [AdminMerchantController.java](../backend/src/main/java/com/swiftdrop/merchant/web/AdminMerchantController.java) (`GET /api/v1/admin/merchants`, `GET /api/v1/admin/merchants/{id}`, `PATCH /api/v1/admin/merchants/{id}/status`)
  - [MerchantPortalController.java](../backend/src/main/java/com/swiftdrop/merchant/web/MerchantPortalController.java) (`GET /api/v1/merchants/me`)
  - [MerchantApiKeyController.java](../backend/src/main/java/com/swiftdrop/merchant/web/MerchantApiKeyController.java) (`GET/POST /api/v1/merchants/me/api-keys`, `DELETE /api/v1/merchants/me/api-keys/{id}`)
  - [MerchantApiController.java](../backend/src/main/java/com/swiftdrop/merchant/web/MerchantApiController.java) (`POST /api/v1/merchant-api/orders`)
  - [MerchantDashboardController.java](../backend/src/main/java/com/swiftdrop/merchant/web/MerchantDashboardController.java) (`GET /api/v1/merchants/me/orders`, `GET /api/v1/merchants/me/orders/{orderReference}`)
  - [WebhookEndpointController.java](../backend/src/main/java/com/swiftdrop/merchant/web/WebhookEndpointController.java) (`GET/POST /api/v1/merchants/me/webhooks`, `DELETE /api/v1/merchants/me/webhooks/{id}`)
- **Error Handling:** [MerchantExceptionHandler.java](../backend/src/main/java/com/swiftdrop/merchant/web/MerchantExceptionHandler.java) maps module exceptions to RFC 9457 `ProblemDetail` (`400`, `403`, `404`, `409`, `422`).
- **DTOs:** `RegisterMerchantRequest`, `ReviewMerchantRequest`, `MerchantResponse`, `CreateApiKeyRequest`, `ApiKeyResponse`, `GeneratedApiKeyResponse`, `CreateMerchantOrderRequest`, `MerchantOrderAddress`, `MerchantOrderResponse`, `MerchantDashboardItemResponse`, `RegisterWebhookRequest`, `WebhookEndpointResponse`, `MerchantApiKeyAuthentication`.

---

## 4. Implemented REST Endpoints

| Method | Path | Auth / Role | Purpose |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/merchants/register` | Public | Register merchant user + `PENDING_APPROVAL` profile |
| `GET` | `/api/v1/admin/merchants` | JWT (`ROLE_DISPATCHER_ADMIN`) | List merchants (optional `page`, `size`, `status`) |
| `GET` | `/api/v1/admin/merchants/{id}` | JWT (`ROLE_DISPATCHER_ADMIN`) | Get single merchant details |
| `PATCH` | `/api/v1/admin/merchants/{id}/status` | JWT (`ROLE_DISPATCHER_ADMIN`) | Review merchant (`APPROVE`, `REJECT`, or `SUSPEND`) |
| `GET` | `/api/v1/merchants/me` | JWT (`ROLE_MERCHANT`) | Get authenticated merchant's profile & status |
| `GET` | `/api/v1/merchants/me/api-keys` | JWT (`ROLE_MERCHANT`) | List active API keys (prefix + label, no secret) |
| `POST` | `/api/v1/merchants/me/api-keys` | JWT (`ROLE_MERCHANT`) | Generate API key (returns `rawKey` once) |
| `DELETE` | `/api/v1/merchants/me/api-keys/{id}` | JWT (`ROLE_MERCHANT`) | Revoke an active API key |
| `POST` | `/api/v1/merchant-api/orders` | `X-API-Key` (`ROLE_MERCHANT_API`) | Create parcel (`201 Created`) or idempotent retry (`200 OK`) |
| `GET` | `/api/v1/merchants/me/orders` | JWT (`ROLE_MERCHANT`) | Paginated orders with optional `status` filter |
| `GET` | `/api/v1/merchants/me/orders/{orderReference}` | JWT (`ROLE_MERCHANT`) | Look up order by merchant reference |
| `GET` | `/api/v1/merchants/me/webhooks` | JWT (`ROLE_MERCHANT`) | List active webhook subscriptions |
| `POST` | `/api/v1/merchants/me/webhooks` | JWT (`ROLE_MERCHANT`) | Register webhook URL + secret |
| `DELETE` | `/api/v1/merchants/me/webhooks/{id}` | JWT (`ROLE_MERCHANT`) | Deactivate a webhook endpoint |

---

## 5. Frontend Implementation (`frontend/app/(merchant)/`)

The Merchant Workspace portal routes are wrapped in [layout.tsx](../frontend/app/(merchant)/layout.tsx) using `PortalShell` (`role="MERCHANT"`):

- `/merchant` ([page.tsx](../frontend/app/(merchant)/merchant/page.tsx)): Loads `GET /api/v1/merchants/me`, shows business name, registration number, approval status badge (`APPROVED`, `REJECTED`, `PENDING_APPROVAL`), navigation cards, and a sample `curl` snippet for `POST /api/v1/merchant-api/orders`.
- `/merchant/orders` ([orders/page.tsx](../frontend/app/(merchant)/merchant/orders/page.tsx)): Fetches `GET /api/v1/merchants/me/orders` with status filter tabs (`ALL`, `CREATED`, `PICKED_UP`, `IN_TRANSIT`, `OUT_FOR_DELIVERY`, `DELIVERED`, `FAILED`) and pagination.
- `/merchant/api-keys` ([api-keys/page.tsx](../frontend/app/(merchant)/merchant/api-keys/page.tsx)): Lists keys via `GET /api/v1/merchants/me/api-keys`, creates keys via `POST`, displays the one-time `rawKey` with a copy button, and revokes keys via `DELETE`.
- `/merchant/webhooks` ([webhooks/page.tsx](../frontend/app/(merchant)/merchant/webhooks/page.tsx)): Lists endpoints via `GET /api/v1/merchants/me/webhooks`, registers URL + secret via `POST`, and deactivates endpoints via `DELETE`.

---

## 6. Automated Tests (`backend/src/test/java/com/swiftdrop/`)

- **Merchant Unit & MockMvc Tests (13 test classes, 46 test methods):**
  - Domain: `MerchantTest`
  - Application: `MerchantApiKeyServiceTest`, `MerchantApprovalServiceTest`, `MerchantDashboardServiceTest`, `MerchantOrderServiceTest`, `MerchantRegistrationServiceTest`, `WebhookEndpointServiceTest`
  - Web: `AdminMerchantApiTest`, `MerchantRegistrationApiTest`, `MerchantApiKeyApiTest`, `MerchantOrderApiTest`, `MerchantDashboardApiTest`, `WebhookEndpointApiTest`
- **Architecture & Convention Tests:**
  - `MigrationConventionTest` updated to assert all 3 merchant Flyway migrations.
  - `ModuleBoundaryTest` verifies `merchant` respects ADR 0003 boundaries.

*(All 476 backend tests passed on the last test run in `backend/` with `-Djacoco.skip=true` on JDK 25.)*

---

## 7. Remaining Work (Not Yet Implemented)

1. **Webhook Event Dispatcher (MER-3 / ADR 0004 §7):** `merchant_webhook_deliveries` table, `WebhookDelivery` entity, and `WebhookDeliveryRepository` exist, but the RabbitMQ consumer (`parcel.status-changed`) and outbound signed HTTP delivery / retry worker have not been implemented yet.
2. **Symmetric Encryption for Webhook Secrets (ADR 0004 §7.2):** `WebhookEndpointService` currently Base64-encodes `secret_encrypted`; authenticated encryption (e.g., AES-GCM with a configured secret key) should replace Base64 before production use.
3. **Merchant Self-Registration & Admin Approval UI:** Backend registration (`POST /api/v1/merchants/register`) and admin review (`AdminMerchantController`) APIs are implemented, while dedicated frontend pages for merchant signup and admin merchant approval UI have not yet been added.
4. **Frontend Build / Lint Check:** `frontend` lint/typecheck has not yet been executed in this repository after installing `node_modules`.

