# Connecting the ecommerce site to SwiftDrop (merchant integration)

Status: **In progress**, last updated 2026-11-22 (hub statuses, section 8b; merchant pickup address, section 8c). Branch `merchantandecommerce` (SwiftDrop) and
branch `dev3` (ecommerce store). The work is committed and pushed on those branches (SwiftDrop
latest: `33c1dbb`); merging to `DEV`/`main` is the team's decision.

This document says what is built, how it was checked, and what is still to do. It replaces the
earlier plan. The approved architecture decisions are in [ADR 0004](adr/0004-messaging-and-integration-reliability.md)
and [ADR 0006](adr/0006-delivery-lifecycle-and-transition-rules.md) (section 1A, merchant hold and
cancel, still *Proposed* pending team review).

Related: [merchant-module.md](merchant-module.md), [implementation-contract.md](implementation-contract.md),
[messaging-event-contract.md](messaging-event-contract.md), [rbac-authorization.md](rbac-authorization.md),
[PROGRESS.md](PROGRESS.md).

---

## 1. What the integration does

A customer buys on the **ecommerce store**. The store is a *merchant* in SwiftDrop. The store's buyers
do **not** have SwiftDrop accounts; the store links each delivery to its own buyer by the order
reference.

```
Buyer -> store checkout -> store creates a Shipment (booking)
  -> POST /api/v1/merchant-api/orders            store -> SwiftDrop   (API key + signature)
  -> parcel CREATED, visible to the merchant portal and the dispatcher
  -> dispatcher assigns a courier; courier moves PICKED_UP .. DELIVERED / FAILED
  -> merchant may Pause / Resume / Cancel the order, only before pickup
  -> every status change: outbox -> RabbitMQ -> merchant consumer -> signed webhook  SwiftDrop -> store
  -> store updates its Shipment and timeline; buyer sees it on /account/orders and /orders/[n]
  (fallback: the store polls the signed GET /api/v1/merchant-api/orders/{reference},
   which returns sequence numbers; the public tracked-parcels endpoint is no longer used by the store)
```

### Decisions taken (approved in chat)

- Pause and cancel only **before pickup** (status `CREATED`; a hold is released back to `CREATED`).
- Only the **merchant** may pause, resume or cancel. The dispatcher views and assigns; couriers cannot.
- Status reaches the store by **signed webhooks**; the store keeps polling as a fallback.
- The store's requests are authenticated by **API key plus a signature** over the request.
- Webhook delivery uses **Apache HttpClient 5** (`httpclient5`, version from the Spring Boot BOM).
- Existing outbox events are **not** sent as webhooks (backfilled as published).
- The notification queue is **declared but not bound** until the notification module has a consumer.
- Approved by the user: ADR 0006 amendment, `delivery.api` change and its migration, the `httpclient5`
  dependency, and the outbox backfill.

---

## 2. Status at a glance

| Phase | Area | Status | Notes |
|---|---|---|---|
| 1 | Pause / cancel lifecycle (delivery) | ✅ Built and tested | Courier rules untouched; migration `V202611151000` |
| 2 | Merchant actions and store status API | ✅ Built and tested | Portal page done; store API `GET /merchant-api/orders/{ref}` |
| 3 | Dispatcher queue, assignment, workload | ✅ Built and tested (dispatcher team, PR #23–#28) | Now hub-based: courier profile API, hub binding, legs, hub-to-hub transfers, assignment history ([ADR 0007](adr/0007-multi-leg-shipments-and-hub-handover.md), *Proposed*) |
| 4 | Webhook pipeline (relay, consumer, sender) | 🟡 Built and tested, **off by default** | History and redelivery API built; merchant UI view, demo receiver and live run missing |
| 5 | Store request signatures, duplicate-order check, rate limit | 🟡 Built and tested, **optional by default** | Store does not sign yet |
| 6 | Ecommerce store changes | ✅ Built and run live | Hub statuses added 2026-11-21; full hub route verified end to end (section 8b) |
| 7 | Connection runbook | 🟡 Written in section 5; needs the updates listed there | Not yet run end to end with the store |

Legend: ✅ done and verified · 🟡 partly done · ⬜ not started.

---

## 3. What is done, phase by phase

### Phase 1. Pause, resume and cancel (SwiftDrop delivery module) ✅

**What it does**
- New statuses `PAUSED` and `CANCELLED` in `delivery.api.ParcelStatus`. `CANCELLED` is final.
- Two separate transition tables in `ParcelLifecycle`: the **courier** table is unchanged (couriers can
  never see or request `PAUSED`/`CANCELLED`); a **merchant hold** table allows `CREATED -> PAUSED/CANCELLED`
  and `PAUSED -> CREATED/CANCELLED`.
- `Parcel.pause / resume / cancel` check ownership first (another merchant's order is "not found"),
  then the table, then take the next sequence number. Each change is written to the history with the
  acting merchant user and emits a `parcel.status-changed` event. The merchant's reason is stored in
  the history only, never in the event.
- `CANCELLED` was added to the three places that decide a parcel is finished (courier day list,
  courier workload count, live tracking stream), so a cancelled order leaves the courier's list and
  its tracking stream closes.
- Migration `V202611151000__delivery_add_pause_cancel_statuses.sql` widens four CHECK constraints.
  It rewrites no rows. It is one-way once a row uses the new values.
- ADR 0006 section 1A and `implementation-contract.md` D-06 describe the amendment.

**How it is verified**
- `ParcelLifecycleTest` checks all 64 status pairs against both tables, and that couriers can never
  move to or from `PAUSED`/`CANCELLED`.
- `ParcelTest` and `MerchantParcelControlServiceTest` cover sequences, ownership, conflicts after
  pickup, and the reason limit (500 characters).
- `MerchantParcelControlIT` (real PostgreSQL): the widened constraints accept the new values, history
  and outbox commit together, a rolled-back hold leaves no trace, and a hold racing a courier pickup has
  exactly one winner. This test found a real bug (a race that surfaced as a confusing optimistic-lock
  error); the fix is one locked lookup by order reference.

### Phase 2. Merchant actions and the store status API ✅

**Merchant portal** (`/api/v1/merchants/me/orders/{reference}`, JWT, approved merchant)
- `POST .../holds` pause (optional reason), `DELETE .../holds` resume, `POST .../cancellations` cancel.
- Each returns the updated order. `409` with the rule when the courier already has the parcel, `404`
  for another merchant's order.
- Every order carries `allowedActions` (`PAUSE`, `RESUME`, `CANCEL`), so the UI never guesses.

**Store-facing API** (`GET /api/v1/merchant-api/orders/{merchantOrderReference}`, `X-API-Key`)
- Returns the current status, `lastSequence`, and the full sequenced history (IDs, codes and times).
  No recipient name, phone or address. Only the key's own merchant's orders.
- A logged-in user's token cannot use it, and the API key cannot use the portal API.

**Merchant portal page** (`frontend/app/(merchant)/merchant/orders/page.tsx`)
- Pause, Resume and Cancel buttons per row, driven by `allowedActions`. Pause and Cancel open a
  confirm dialog with an optional reason. On hold and Cancelled filters and badges. The tracking
  number links to the public tracking page.

**How it is verified**
- `MerchantOrderActionsApiTest` and `MerchantOrderStatusApiTest` (MockMvc, real security chain).
- An end-to-end script against the running backend: 32 checks passed (register, approve, order,
  read status, hold, release, cancel, conflicts, 404s, boundary checks).
- Both frontend pages compile and respond. Lint still reports one pre-existing error on the orders
  page (a fetch inside an effect); the same pattern exists on other merchant pages.

### Phase 3. Dispatcher queue, assignment and workload 🟡

**Built**
- `GET /api/v1/unassigned-parcels`: parcels in `CREATED` with no courier, oldest first, paged. No
  sender or recipient personal data.
- `POST /api/v1/parcel-assignments` `{parcelId, courierUserId}`: the dispatcher is taken from the
  token. `dispatch` checks that the courier is eligible (enabled user, `COURIER` role, active courier
  profile, as contract section 5.4 requires); `delivery` checks the parcel is still `CREATED`.
  `409` says why; `404` for an unknown parcel.
- `GET /api/v1/courier-workloads`: active couriers with their active parcel count, least busy first,
  no phone numbers.
- Dispatcher screen (`frontend/app/(dispatch)/dispatcher/page.tsx`): queue table with an Assign dialog
  that shows each courier's load; workload panel; errors shown as the server's reason.
- The role is `DISPATCHER` (the RBAC doc says operational endpoints belong to the dispatcher; PROGRESS
  7.2 had said "admin"). An admin cannot use these endpoints. Confirm if you want admins to have access.

**How it is verified**
- `CourierEligibilityTest` (each eligibility rule on its own), `DispatchServiceTest`, and
  `DispatchApiTest` (401, 403 for every other role, 404, 409, success, workload order, no phone).
- `ModuleBoundaryTest` passes: `dispatch` uses only `identity.api` and `delivery.api`.
- End-to-end script: 37 checks passed (queue, workload, role rules, a held order off the queue, a
  merchant user refused as courier, unknown parcel, assignment, the merchant can still hold an
  assigned order, an inactive courier refused).

**Update 2026-11-21.** The dispatcher and courier teams have since built the courier profile admin API
(`/api/v1/couriers`, ADMIN), hubs and dispatcher-to-hub binding, multi-leg routes, hub-to-hub transfers and
assignment history. The queue and assignment are now scoped to the dispatcher's hub. See section 8b for what
this means for the store.

### Phase 4. Webhook pipeline, SwiftDrop to the store 🟡 (off by default)

Enable with `SWIFTDROP_MESSAGING_RELAY_ENABLED=true` (relay) and `SWIFTDROP_WEBHOOKS_ENABLED=true`
(consumer and sender). Both default to `false`.

**Built**
1. **Outbox relay** (`delivery/infrastructure/messaging/OutboxRelay`): publishes unpublished events in
   the order they were written, to exchange `swiftdrop.events` with key `parcel.status-changed`. It
   waits for the broker's confirmation and treats an unroutable message as a failure. The first
   failure stops the batch, so later events cannot overtake it. One relay instance only.
2. **Backlog backfill** (`V202611151100`): every event written before the relay existed is marked as
   published, so no old webhooks fire. One-way.
3. **Merchant consumer** (`merchant/infrastructure/messaging/ParcelStatusConsumer`): for each active
   endpoint of the merchant, creates one delivery per event, storing the exact event body (`V202611151110`).
   Redelivered events create nothing new. Malformed events go to the dead-letter queue.
4. **Sender** (`merchant/application/WebhookDeliveryService`, run by `WebhookScheduler`): signs the
   stored body with `X-SwiftDrop-Signature: v1=HMAC-SHA256(secret, timestamp.body)`, the same formula
   as the store's `verifyV1` (checked against a Node.js reference value in `WebhookSignerTest`). Posts
   with Apache HttpClient 5: 5 s connect, 10 s response, no redirects, no automatic retries. 2xx
   succeeds; connection failure, timeout, 408, 429 and 5xx retry (30 s base, doubling, ±20 % jitter,
   1 h cap, 8 attempts); other responses fail at once.
5. **URL and address rules**: https on port 443 only, no credentials, no private or reserved address
   (checked at registration and again at connect time by the DNS resolver, including IPv4-mapped and
   IPv6 unique-local addresses). `SWIFTDROP_WEBHOOK_ALLOW_LOCAL=true` allows `http://localhost` for
   development only.
6. **Encrypted webhook secrets**: AES-256-GCM under `SWIFTDROP_WEBHOOK_KEYS` (`keyId=base64(32 bytes)`
   pairs; `SWIFTDROP_WEBHOOK_ACTIVE_KEY_ID` picks the key for new secrets; older keys stay readable for
   rotation). Bound to the merchant id, so a secret moved to another merchant does not decrypt. Secrets
   stored before this (Base64) cannot be read; their deliveries fail without a send, and the merchant must
   register the endpoint again.
7. **Notification queue** is declared but not bound to `swiftdrop.events` (nothing consumes it; a bound,
   unread queue would reject every publish once full).

**How it is verified**
- Unit: `WebhookSignerTest`, `WebhookSecretCipherTest` (round trip, tamper, moved secret, legacy value,
  key rotation, startup checks), `WebhookUrlPolicyTest`, `AddressPolicyTest`, `WebhookDnsResolverTest`,
  `WebhookDeliveryServiceTest` (success, retry, final failure, deactivated endpoint, backoff), `ParcelStatusConsumerTest`,
  `OutboxRelayTest` (order, confirmation, unroutable, not-yet-due).
- Integration profile: `RabbitMessagingIT` (except the pre-existing timing test) and `DatabaseMigrationIT` pass with the notification change.
- **End to end** with relay and sender on: a receiver on port 3300 checked every signature with the
  store's own `verifyV1` code. CREATED, PAUSED, released and CANCELLED arrived in order, once each, and
  all were verified. A closed-port endpoint retried as designed. The secret was stored encrypted and
  appears in no table row or delivery body.

**Still to do**
- **Built (commit `33c1dbb`, migration `V202611151220`):** `GET /api/v1/merchants/me/webhook-deliveries`
  and `POST /api/v1/merchants/me/webhook-deliveries/{id}/redeliveries`. Only a `FAILED` delivery can be
  redelivered, at most 3 times (`MAX_MANUAL_REDELIVERIES`). Covered by domain, service and API tests.
- **Still to do:** a merchant-facing history view in the frontend (no frontend code calls these endpoints yet).
- **Demo webhook receiver and integration guide** (PROGRESS 5.7).
- Two-instance safety for the relay and sender is designed (ordering, row locks) but not tested with
  several instances.
- Webhook secret encryption uses the merchant id as associated data (the plan said the endpoint id;
  the id is not known before save). Equivalent for the threat it addresses; noted as a deliberate choice.

### Phase 5. Store request signatures, duplicate orders, rate limit 🟡 (optional by default)

**Built**
- **Signing secret per API key.** Each new key gets a secret (32 random bytes, returned once with the
  key, stored encrypted with the webhook keyring). Keys created before this have none.
- **Signature on store requests.** `X-SwiftDrop-Timestamp` and
  `X-SwiftDrop-Signature: v1=hex(HMAC-SHA256(secret, timestamp.METHOD.path.sha256hex(body)))`. The
  timestamp must be within 5 minutes. A missing, wrong, stale or moved signature gets a generic `401`.
  Keys without a secret cannot send a signature.
- **Switch.** `SWIFTDROP_MERCHANT_API_REQUIRE_SIGNATURE` (default `false`). When `true`, every
  store request must be signed, and keys without a secret are refused.
- **Duplicate orders.** Each parcel stores a fingerprint of its order fields (`V202611151200`). A
  repeat of a reference with the same content returns the existing parcel (`200`); with different
  content it is refused (`409`), so an old request cannot silently change an order.
- **Rate limit** for the store API: `SWIFTDROP_RATE_LIMIT_MERCHANT_API_PER_MINUTE` (default 120), per
  client address, counting every method. The existing login and registration limits are unchanged.
- Body size is capped at 64 KB before the signature is checked.

**How it is verified**
- `ApiRequestSignatureTest` pins the formula with a Node.js reference value, and checks tolerance,
  each changed field, and malformed headers.
- `OrderFingerprintTest`, `MerchantParcelCreationTest` (same content, different content, legacy parcel).
- `MerchantApiSignatureApiTest` and `MerchantApiSignatureRequiredApiTest` (MockMvc, the real filter),
  and `MerchantApiRateLimitApiTest`.
- **End to end**, against the running backend, once with signatures optional and once with them
  required: 14 checks each passed (valid signature accepted, wrong secret, stale, moved body, duplicate
  order `200`, changed content `409`, unsigned requests in both modes, key without a secret).

**Known limits**
- A captured signed request can be sent again within 5 minutes. For orders this returns the same parcel;
  a nonce check is not implemented.
- Keys created before this change have no secret and must be created again before signatures are required.

### Phase 6. Ecommerce store (`D:\ecommerce site\ecommerce`, branch `dev3`) ✅ (code)

**Done (committed by the team on `dev3`, commit `08a57cd`)**
- Prisma schema: `DeliveryStatus` gains `PAUSED` and `CANCELLED`; `ShipmentState` gains `CANCELLED`;
  `Shipment.lastSequence` added; `DeliveryEvent` keyed by sequence (one row per SwiftDrop change).
- Migration `20261115100000_delivery_pause_cancel_sequence` applied to the local `store` database.
  Existing timeline rows are renumbered by time; the one existing shipment is unchanged apart from
  its new column.
- Status logic: a change applies only if its sequence is higher than the last applied one (so a
  held order can be released). Terminal: `DELIVERED`, `FAILED`, `CANCELLED`.
- Cancelling restores the reserved stock in the same transaction.
- Poller, booking and admin actions use the sequence; admin simulators use sequence numbers.
- Timeline and customer pages show "On hold" and "Cancelled"; the admin shipments filter lists
  Cancelled.
- Checks: type-check clean, lint clean, 70 of 70 unit tests pass.

**Done on SwiftDrop's `/track` page and frontend types**
- `/track` accepts `PAUSED` and `CANCELLED`, shows the on-hold notice, treats `CANCELLED` as final.
- Customer history page labels both statuses.

**Done (uncommitted on `dev3`, 2026-11-15)**
- **Request signing:** `LiveSwiftDropClient` signs every `/merchant-api/**` call with `X-SwiftDrop-Timestamp` and
  `X-SwiftDrop-Signature` when `SWIFTDROP_SIGNING_SECRET` is set (`env.ts`, `.env.example`). Method, path
  without query, and the exact body bytes are signed (`signRequestV1`). With no secret, no signature headers
  are sent, so it works while SwiftDrop signatures are optional. A vitest case checks the reference value
  from `ApiRequestSignatureTest`.
- **Polling via the status API:** the poller, the admin "poll now" action and the booking replay path call
  `getOrderStatus(merchantRef)` (`GET /api/v1/merchant-api/orders/{ref}`, signed) and apply events with
  SwiftDrop's own sequence numbers and failure reasons (`applyOrderStatus`). The public tracking call stays
  in the client but nothing uses it.
- **Zone codes:** `swiftDropZoneCode` in `src/lib/districts.ts` (`UVA -> LK-UP`, `SAB -> LK-SGP`, others get
  `LK-`); `scripts/sync-zones.ts` uses it. Tests check all districts map to the nine seeded zones.
- **Checks:** type-check clean, lint clean, 77 of 77 unit tests pass. `npm run build` was **not run**: the
  running dev server holds the Prisma engine file (EPERM on `prisma generate`).

- **Buyer dashboard:** `/account/orders` already shows the tracking number of the latest shipment.
- **Admin simulators (mock mode):** new `simulateHold` action and Hold, Release and Cancel buttons on
  `/admin/shipments` send `PAUSED`, `CREATED` (release) and `CANCELLED` events with the next sequence
  number, so the new states can be tried without SwiftDrop. "Next" and "Fail" are unchanged.
- **Scheduling:** nothing new in code. `npm run cron:local` (every 60 s, needs `CRON_SECRET`) is the
  local runner; a deployed store must call `GET /api/cron/swiftdrop` with
  `Authorization: Bearer <CRON_SECRET>` on a schedule (for example every minute).

**Still to do (needs the running system, not code)**
1. Stop the dev server, then run `npm run build` (it could not regenerate Prisma while the server ran).
2. Run `npm run zones:sync` against the running backend; check checkout accepts every district.
3. Create a key with a signing secret, set `SWIFTDROP_SIGNING_SECRET`, and run the end-to-end test with
   signatures optional and then required (`SWIFTDROP_MERCHANT_API_REQUIRE_SIGNATURE=true`).
4. Keep `npm run cron:local` running during the test.

Not planned for the store: cancelling an order after payment (refunds are not modelled by either side).

### Phase 7. Connection runbook

See section 5. The steps still need these corrections, which are in the new code:
- The backend needs `SWIFTDROP_WEBHOOK_KEYS` and `SWIFTDROP_WEBHOOK_ACTIVE_KEY_ID` to create keys with
  signing secrets and to store webhook secrets. Without them, keys have no secret.
- Enable the relay and the webhook sender explicitly (`SWIFTDROP_MESSAGING_RELAY_ENABLED=true`,
  `SWIFTDROP_WEBHOOKS_ENABLED=true`). RabbitMQ credentials must then be set for the backend.
- Dispatchers must be bound to a hub and couriers need a profile with a home hub (see runbook step 8).

---

## 4. Database changes made by this work

| Module | Migration | Change | One-way? |
|---|---|---|---|
| delivery | `V202611151000` | Widens status CHECK constraints (`PAUSED`, `CANCELLED`) | Yes, once used |
| delivery | `V202611151100` | Marks existing outbox rows published (backfill) | Yes |
| merchant | `V202611151110` | `payload` column on webhook deliveries | No |
| delivery | `V202611151200` | `order_fingerprint` on parcels | No |
| merchant | `V202611151210` | `signing_secret_encrypted` on API keys | No |
| store (Prisma) | `20261115100000` | Statuses, `lastSequence`, sequence-keyed timeline | Renumbers existing timeline rows |

Earlier cleanup on this branch: the duplicate `merchants` table migration `V202611130901` was removed
(it made Flyway fail on any database; `V202611091200` is the schema kept), and the duplicate
`GET /api/v1/merchants/me` controller was removed.

---

## 5. Connection runbook (current)

Use this to bring up SwiftDrop and the store together on one machine. Values in angle brackets are
yours; never commit them.

1. **Infrastructure.** `docker compose up -d` in the SwiftDrop folder (PostgreSQL and RabbitMQ healthy).
   The store database is in its own `compose.yaml` (port 5434).
2. **Backend.** Set variables in the same shell that starts the backend (Spring does not read `.env`):
   - `SWIFTDROP_BOOTSTRAP_ADMIN_EMAIL`, `SWIFTDROP_BOOTSTRAP_ADMIN_PASSWORD` (first start creates the admin)
   - `SWIFTDROP_RABBITMQ_PASSWORD` (needed once the relay is on)
   - `SWIFTDROP_WEBHOOK_KEYS=<id>=<base64 of 32 random bytes>`, `SWIFTDROP_WEBHOOK_ACTIVE_KEY_ID=<id>`
   - `SWIFTDROP_MESSAGING_RELAY_ENABLED=true`, `SWIFTDROP_WEBHOOKS_ENABLED=true`
   - `SWIFTDROP_WEBHOOK_ALLOW_LOCAL=true` (development only; lets the store on `localhost` receive webhooks)
   - Then `.\mvnw.cmd spring-boot:run` from `backend/`. Migrations run at startup.
3. **Merchant account.** Sign up the store as a merchant (`POST /api/v1/merchants`, or the merchant tab
   on `/signup`). Sign-up **requires the warehouse (pickup) address**, including its province: this is where the
   courier collects the parcels (section 8c). Then an admin approves it (`POST /api/v1/merchants/{id}/approval`,
   or Admin → Users, where the queue shows the address). A merchant registered earlier adds its address in the
   merchant portal (card "Pickup address") or with `PUT /api/v1/merchants/me/pickup-address`.
4. **Key and webhook.** The merchant logs in and creates an API key (`POST /api/v1/merchants/me/api-keys`).
   The response contains the key and the signing secret, **both shown once**. Register the webhook
   (`POST /api/v1/merchants/me/webhooks`) with URL `http://localhost:3100/api/webhooks/swiftdrop`
   (development) or an HTTPS URL, and a secret of 16+ characters; the store uses the same secret to check
   webhook signatures.
5. **Zones.** `GET /api/v1/zones` is public and lists the nine provinces (id, code, name). `npm run zones:sync`
   maps the store's districts to them. The pickup zone is no longer needed in the store when the merchant has a
   registered address (step 6).
6. **Store environment** (`.env` of the store; never commit it): `SWIFTDROP_MODE=live`,
   `SWIFTDROP_API_URL=http://localhost:8080`, `SWIFTDROP_API_KEY`, `SWIFTDROP_WEBHOOK_SECRET`,
   `CRON_SECRET`, and `SWIFTDROP_MERCHANT_EMAIL` /
   `SWIFTDROP_MERCHANT_PASSWORD` for the zone sync. `SWIFTDROP_SIGNING_SECRET` (the key's signing secret). The
   `STORE_PICKUP_*` settings (name, phone, line 1, city and `STORE_PICKUP_ZONE_ID`; line 2 and postal code are
   optional) are **optional as a group**: leave all unset and the store sends no `pickup`, so SwiftDrop uses the
   registered address (recommended); set them and SwiftDrop checks that the zone is the registered province. Setting only
   some of them stops the store at startup and names the missing ones. Then `npm run zones:sync`, `npm run dev` (port 3100) and
   `npm run cron:local`.
7. **Signatures.** Leave `SWIFTDROP_MERCHANT_API_REQUIRE_SIGNATURE=false` until the store signs its
   requests (done in the store code). Then set it to `true` and create new keys for the store.
8. **Dispatch.** An admin creates dispatcher and courier accounts (staff endpoint), binds the dispatcher
   to a hub (`PUT /api/v1/dispatchers/{userId}/hub` with `{"hubCode":"LK-WP"}`), and creates a courier
   profile (`POST /api/v1/couriers` with `userId`, `phone`, `vehicleType`, `hubCode`). The store's orders
   appear only for the dispatcher of the hub whose code equals the province code of the pickup zone (the
   merchant's registered province, or `STORE_PICKUP_ZONE_ID` when set),
   and only couriers based at that hub (or with no hub) can be assigned. A delivery to another province also
   needs dispatchers at the Main Hub (`LK-WP`) and the destination hub for the transfers and the final leg.
9. **Check.** Place an order on the store, then check the merchant portal and the dispatcher screen.

---

## 6. Rollout order (what has been done and what is next)

1. ✅ Store tolerates the new statuses and sequence ordering (`dev3`).
2. ✅ SwiftDrop lifecycle, merchant actions, store status API.
3. ✅ Dispatcher queue, assignment and workload (courier admin API still missing).
4. ✅ Webhook pipeline built, off by default. ⬜ Enable it on a running system for the store.
5. ✅ Signature check built, optional by default. ⬜ Store signs requests, then require signatures.

Hard gate kept: the frontend `/track` page and the store accept `PAUSED` and `CANCELLED` before the
backend can produce them. Both do now.

---

## 7. Next steps, in order

1. ~~Store signing and status API~~ (done in code). Next: run it live and require signatures.
2. ~~Courier profile admin API~~ (built by the dispatcher team). ~~Hub route end to end~~ (done, section 8b).
   Next: repeat with signatures required, and a failure path.
3. ~~Store zone-code mapping~~ (done in code). Next: run `npm run zones:sync` and schedule the cron call.
4. ~~Webhook history and redelivery API~~ (built). Next: the merchant history view and the demo receiver (PROGRESS 5.7).
5. ~~Assignment history~~ (built). Team review of ADR 0007 (multi-leg, *Proposed*).
6. **Team review** of ADR 0006 section 1A, then acceptance or changes.
7. **Full end-to-end run** with the runbook above, and the store's test suite.

---

## 8. Verification summary

| Check | Result (last run) |
|---|---|
| Backend unit build (`mvnw clean verify`) | 780 tests, 0 failures, 0 errors (before commit `33c1dbb`, which added webhook history tests; **rerun to get the current count**) |
| Backend integration profile (`-Pintegration`) | 111 tests; 3 failures, all pre-existing (below) |
| Migrations on a fresh PostgreSQL | `DatabaseMigrationIT` passes with 22 migrations |
| Phase 1 to 5 end-to-end scripts | Orders and holds 32/32; dispatch 37/37; webhooks 17/17; signatures 14/14 optional and 14/14 required |
| Store unit tests and type-check | 77/77 tests; type-check and lint clean (after Phase 6). `npm run build` not run (dev server locked Prisma) |
| Frontend | Type-check clean; lint has pre-existing errors on merchant pages (not from this work) |

Pre-existing integration failures, unrelated to this work, still to be fixed:
- `DeliveryQueriesIT.rebuiltEventsEqualTheOutboxPayloads`: timestamp precision (nanoseconds against
  PostgreSQL's microseconds).
- `DispatchPersistenceIT.databaseEnforcesUniqueUserForeignKeyAndVehicleTypeConstraints`: reuses a
  transaction after a failed insert.
- `RabbitMessagingIT.deadLetterWaitsUntilMissingTargetQueueIsRestored`: timing-dependent; times out.

---

## 8a. Flow review (2026-11-15): is the order and tracking flow consistent with the plan?

Checked by reading code and docs; **no live run**. Header names, the signature formula and the status API
shape agree between SwiftDrop and the store. Remaining concerns:

- The store and backend have never run together; earlier end-to-end scripts used a test receiver.
- With default settings (relay and webhooks off), the store learns status only by polling: the cron call
  must run and the poll interval (default 300 s) must pass, so status can be minutes old.
- Orders cannot move past `CREATED` for real until a courier profile can be created (Phase 3 gap).
- Checkout refuses districts until `npm run zones:sync` has run against the live backend.
- Signatures are optional by default and older keys have no signing secret.

---

## 8b. Flow review (2026-11-21): after the dispatcher hub work

Checked by reading code in both repositories, then **run live end to end** (below).

**Order details to the dispatcher: works.** A store order is a `CREATED` parcel. `GET /api/v1/unassigned-parcels`
now lists only parcels whose pickup province equals the dispatcher's hub code. Hub codes (`LK-WP`, `LK-UP`,
`LK-SGP`, ...) are the zone codes the store syncs, so the order appears for the pickup hub's dispatcher.
Assignment plans the legs (same province: pickup and final mile; otherwise through the Colombo Main Hub).

**Tracking details back to the store: was broken, fixed in the store.** The courier lifecycle no longer has
`PICKED_UP -> IN_TRANSIT`; every parcel goes `PICKED_UP -> HUB_ARRIVED`, and hub-to-hub transfers by dispatchers
add `IN_TRANSIT -> HUB_ARRIVED`. All of these are recorded with the outbox, so webhooks and the status API carry
them. The store knew only eight statuses and validated them strictly, so:
- its webhook endpoint answered `400` to the first hub event (and every later one), which SwiftDrop treats as a
  final failure (no retry);
- its poller could not read `GET /api/v1/merchant-api/orders/{ref}` once the history had a hub event;
- the buyer's order stayed at "Picked up" and never closed.

**Fix (store, `dev3`, uncommitted, 2026-11-21).** `HUB_ARRIVED` and `HUB_SORTED` added to the Prisma enum
(migration `20261121100000_delivery_hub_statuses`, additive), the status list and labels; the buyer timeline
shows hub steps as "On the way"; badges "At a SwiftDrop hub" / "In transit"; the mock simulator follows the hub
route. For the future, the store no longer rejects a status it does not know: the webhook answers `200` and logs
it as `IGNORED`, and the status API parser skips such events, so later known events (ordered by sequence) still
apply. Checks: type-check and lint clean, 84 of 84 unit tests pass. SwiftDrop's merchant orders page also gained
filters and badges for the hub statuses. No SwiftDrop backend change.

**Live end-to-end run (2026-11-21).** Store migration applied to the local `store` database. Backend run with
relay and webhooks on (`SWIFTDROP_WEBHOOK_ALLOW_LOCAL=true`); store in live mode with a new merchant, API key with
signing secret and webhook; dispatcher and courier at `LK-WP` (pickup) and `LK-UP` (destination). A store order to
Monaragala (Uva) was planned as `PICKUP` (`LK-WP`), `INTER_HUB` (`LK-WP` to `LK-UP`), `FINAL_MILE` (`LK-UP`) and
driven through the API:

| Seq | Status | Actor | Webhook | Store |
|---|---|---|---|---|
| 1 | `CREATED` | store booking | 200 | `CREATED` |
| 2 | `PICKED_UP` | pickup courier | 200 | `PICKED_UP` |
| 3 | `HUB_ARRIVED` | pickup courier at `LK-WP` | 200 | `HUB_ARRIVED` |
| 4 | `IN_TRANSIT` | `LK-WP` dispatcher transfer | 200 | `IN_TRANSIT` |
| 5 | `HUB_ARRIVED` | `LK-UP` dispatcher receipt | 200 | `HUB_ARRIVED` |
| 6 | `OUT_FOR_DELIVERY` | final-mile courier | 200 | `OUT_FOR_DELIVERY` |
| 7 | `DELIVERED` | final-mile courier, with proof | 200 | `DELIVERED`, order closed |

All seven webhooks succeeded on the first attempt; the store processed seven and rejected none, and its timeline
has one row per sequence. Courier assignments produce no event, as designed. Not covered: signatures required
(`SWIFTDROP_MERCHANT_API_REQUIRE_SIGNATURE=true`), failure paths, and a same-province route.

**If deliveries failed before the store fix:** they recover through polling (the status API returns the full
sequenced history); a merchant can also redeliver them
(`POST /api/v1/merchants/me/webhook-deliveries/{id}/redeliveries`, at most 3 times).

---

## 8c. Merchant pickup address (2026-11-22)

**Problem.** SwiftDrop did not know where a merchant's goods are. The pickup address arrived only inside each API order, copied
by the store from its own settings, and was trusted blindly: a wrong zone would send the order to another hub's dispatcher and the
courier to the wrong place, and an admin approved a merchant without seeing where it operates.

**Change** (details in [merchant-module.md](merchant-module.md) section 4.2):
- A merchant registers **one pickup address with a province** at sign-up (required) and can edit it in the portal; the admin
  approval queue shows it. `GET /api/v1/zones` is public so sign-up can list the provinces.
- API orders may **omit `pickup`** (the registered address is used) or send one in the **same province** (accepted as sent);
  a pickup in another province is refused (`422`). Merchants without an address keep working unchecked until they add one.
- The store makes `STORE_PICKUP_*` optional as a group and omits `pickup` when they are unset.

**Checked live (backend with the change, store on `main`, 2026-11-22):**

| Check | Result |
|---|---|
| Sign-up without address / with an unknown province | `400`; no login account left behind for the unknown province |
| Sign-up with a Galle (Southern Province) address | `201`; the admin list shows it; approval `200` |
| API order without `pickup` | `201`; parcel pickup = the registered warehouse |
| API order with a Colombo pickup (merchant registered in Galle) | `422`, `invalidFields: ["pickup.zoneId"]`, no parcel |
| API order with another Galle pickup | `201`, accepted as sent |
| Browser: sign-up form, admin queue, portal card (edit, save, reload) | all as designed |
| Store order, `STORE_PICKUP_*` set (Galle) | booked; SwiftDrop kept the store's pickup (province matches the registered one) |
| Store order, `STORE_PICKUP_*` unset | booked; the parcel's pickup was the registered address (a marker in line 2 of the registered
  address showed it) |

Both orders went to the Southern hub dispatcher's queue; the courier of an assigned parcel sees the pickup address.

**Limits.** The address is not validated beyond format and province (no map, no geocoding). One address per merchant; several
warehouses would need a locations table. Existing merchants have no address until they add one, so their orders are not checked
yet; making it mandatory for them would be a separate decision. The store's checkout label "pickup district" still comes from
`STORE_PICKUP_DISTRICT` (display only).

---

## 9. Risks and limits

- **Relay and sender are single-instance.** Several instances would break event order and need more
  testing before use.
- **Signatures are replayable within 5 minutes** (no nonce yet). Orders are idempotent, so a replay
  cannot create a second parcel.
- **New SwiftDrop statuses break strict consumers.** The store now skips statuses it does not know
  (section 8b), but any other merchant integration that validates statuses strictly would reject them.
- **ADR 0006 section 1A is still "Proposed"**; the team has not yet reviewed it.
- **Database changes are one-way** where noted in section 4 (status constraints, outbox backfill).
- **Webhooks and signing depend on keys** (`SWIFTDROP_WEBHOOK_KEYS`). Losing a key makes the secrets
  stored under it unreadable; keep keys in a secret store, not in a repository.
- **Test data** from the end-to-end runs remains in the local databases (test merchants, keys,
  orders). It was not deleted.
- **Refunds** for orders cancelled after payment are not modelled by either system.
