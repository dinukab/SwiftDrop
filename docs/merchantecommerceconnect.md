# Connecting the ecommerce site to the SwiftDrop merchant module

Status: **Proposed** (2026-11-15). This is an implementation plan and a connection guide, not a
statement of what is built. Nothing here changes an accepted ADR until the amendments listed under
[Approvals needed](#3-approvals-needed-before-implementing) are reviewed and accepted.

Related documents: [merchant-module.md](merchant-module.md),
[implementation-contract.md](implementation-contract.md),
[messaging-event-contract.md](messaging-event-contract.md),
[ADR 0004](adr/0004-messaging-and-integration-reliability.md),
[ADR 0006](adr/0006-delivery-lifecycle-and-transition-rules.md),
[rbac-authorization.md](rbac-authorization.md), [PROGRESS.md](PROGRESS.md).

## 1. Context

The ecommerce site (`D:\ecommerce site\ecommerce`, Next.js 15, Prisma/PostgreSQL, dev port 3100)
already contains a complete SwiftDrop client: checkout creates a `Shipment`, `bookShipment`
calls `POST /api/v1/merchant-api/orders`, a webhook receiver verifies HMAC signatures, a poller reads
public tracking, and the customer pages (`/account/orders`, `/orders/[n]`) show the timeline.

What is missing is mostly on the SwiftDrop side:

| Need | Current state |
|---|---|
| Pause and cancel an order before pickup | No `PAUSED` or `CANCELLED` status. ADR 0006 (Proposed) forbids cancellation; `ParcelLifecycle` has no such transition; database CHECK constraints reject the values. |
| Push status to the store | `parcel.status-changed` is written to the outbox, but nothing relays it to RabbitMQ, nothing consumes it in `merchant`, and no HTTP sender exists. Webhook endpoints can be registered but are never called. |
| Prove an order comes from the store | Hashed `X-API-Key` only. No request signing, no replay protection, no rate limit on `/api/v1/merchant-api/**`. |
| Merchant actions per order | The merchant orders page is read-only. |
| Dispatcher view | `dispatcher/page.tsx` is a placeholder and no dispatcher endpoints exist (PROGRESS 7.2 to 7.5). |

### Decisions taken

- Pause and cancel are allowed **before pickup only** (status `CREATED`; resume returns to `CREATED`).
- **Only the merchant** may pause, resume or cancel. The dispatcher views orders and assigns couriers.
- Status reaches the store by **signed webhooks**; the store keeps polling as a fallback.
- Orders are verified by **API key plus an HMAC signature** over the request.

## 2. Target flow

```
Customer -> store checkout -> Shipment(PENDING_BOOKING)
  -> POST /api/v1/merchant-api/orders   (X-API-Key + signature headers)      [store -> SwiftDrop]
  -> Parcel CREATED (source MERCHANT)   -> merchant portal + dispatcher screen
  -> dispatcher assigns a courier; courier moves PICKED_UP ... DELIVERED / FAILED
  -> merchant portal: Pause / Resume / Cancel per order (only while CREATED or PAUSED)
  -> each status change = outbox event -> RabbitMQ -> merchant consumer
  -> signed webhook POST /api/webhooks/swiftdrop on the store               [SwiftDrop -> store]
  -> store updates Shipment + DeliveryEvent -> customer dashboard
  (fallback: store polls GET /api/v1/tracked-parcels/{trackingNumber})
```

## 3. Approvals needed before implementing

Required by [ai-agent-guidelines.md](ai-agent-guidelines.md):

1. **Amend ADR 0006** (currently Proposed) to add `PAUSED` and `CANCELLED`, and update
   `implementation-contract.md` D-06.
2. **`delivery.api` change** (`ParcelStatus`, new `MerchantParcelControl` contract) and a **new
   delivery migration**: another module's `api` package and migrations need explicit review.
3. **New dependency** `org.apache.httpcomponents.client5:httpclient5` for the SSRF-safe sender (the
   ADR 0004 section 7.3 decision). Alternative without a dependency: the JDK `HttpClient` with a
   pre-resolved, validated address, which is weaker against DNS rebinding.
4. **New environment variables** (placeholders in `.env.example` only):
   `SWIFTDROP_WEBHOOK_KEYS`, `SWIFTDROP_WEBHOOK_ACTIVE_KEY_ID`, `SWIFTDROP_WEBHOOK_ALLOW_LOCAL`,
   `SWIFTDROP_MERCHANT_API_REQUIRE_SIGNATURE`.

## 3A. Impact on other modules and required safeguards

A review of the existing code found places where the plan, built naively, would affect other
modules. Each row is a requirement on the phases below.

| # | Area | What would go wrong | Required safeguard |
|---|---|---|---|
| 1 | **Courier** | `ParcelLifecycle.ALLOWED` is shared. `CourierParcelService` returns `ParcelLifecycle.nextStatuses()` to the courier app and `ParcelStatusService.changeStatus` validates with `canMove`. Adding `PAUSED` or `CANCELLED` to that table would let a courier see and request them. | Leave the courier transitions unchanged. Add a **separate merchant-hold transition table** used only by `MerchantParcelControlService`. Test that a courier request to `PAUSED` or `CANCELLED` is rejected. |
| 2 | **Terminal statuses** | The terminal set is duplicated in `ParcelQueryService.TERMINAL`, `CourierParcelService.TERMINAL` (used by `ParcelRepository.findCourierDay` and `countActiveByCourier`) and `LiveTracking.isFinal`. A cancelled parcel with an assigned courier would stay in the courier's list and workload count, and its SSE stream would never close. | Add `CANCELLED` to all three and route them through `ParcelLifecycle.isTerminal`. `PAUSED` stays non-terminal and still counts toward the assigned courier's workload. |
| 3 | **Test contexts** | Nothing uses `@Scheduled`, `@EnableScheduling` or `@RabbitListener` today. About 25 `@SpringBootTest` classes exclude DataSource, JPA and Flyway, so a new bean that needs a repository or a broker connection breaks all of them (as the missing `CourierProfileRepository` bean did). | Put the relay, consumer and sender behind `swiftdrop.messaging.relay.enabled` (off in tests, on through the environment at runtime) and set `spring.rabbitmq.listener.simple.auto-startup=false` in tests. Run the full `clean verify` as soon as the first scheduled bean exists. |
| 4 | **Outbox backlog** | No relay has ever run, so every historic `parcel_outbox_events` row has `published_at IS NULL`. The first run would publish all old events, and the new consumer would fire **webhooks for old merchant orders** at the store. | A delivery migration marks existing outbox rows as published (or the relay ignores events before `swiftdrop.messaging.relay.since`). Reviewed with the migration. |
| 5 | **Notification queue** | `notification.parcel-status-changed` is declared and bound but has no consumer. Once the relay publishes, messages accumulate until the 100000-message limit and then dead-letter. | Either add a minimal logging consumer or keep that binding off until the notification module exists. Record the choice in `messaging-stage-2.md`. |
| 6 | **Identity rate limiting** | `RateLimitingFilter` also protects login and registration. Changing how it trusts `X-Forwarded-For` could make all users behind a proxy share one limit, or let clients bypass it. | **Not part of this work.** Only add a separate limit group for `/api/v1/merchant-api/**`; existing auth and registration behaviour stays as it is. |
| 7 | **Public tracking and the store** | `app/track/tracking-page.tsx` (`isPublicTrackingView`) and the store's zod schema reject unknown statuses, so a paused or cancelled parcel would show an error page and fail webhook parsing. | Hard gate: the frontend `/track` page and the store must accept the new statuses **before** the backend can produce them. |

Checked and **not** affected:

- **Customer flow:** `bookForCustomer`, customer history and receipts are unchanged. Pause and cancel
  apply only to parcels with `source = MERCHANT` owned by the caller, with an explicit guard.
  `ReceiptResponse` only fills extra fields for `DELIVERED` and `FAILED`, so other statuses are safe.
- **Identity, roles and JWT:** unchanged. The API-key filter runs only for `/api/v1/merchant-api/**`.
- **Module boundaries** (`ModuleBoundaryTest`): `merchant` uses `delivery.api` and `identity.api`
  only, and `dispatch` uses `delivery.api` and `identity.api`, as ADR 0003 allows.
- **Pricing and zones:** untouched.
- **Existing data:** only constraint relaxations, plus the outbox backfill in row 4.

Residual risks:

- The new statuses are **forward-only in the database**. Once `PAUSED` or `CANCELLED` rows exist, the
  constraints cannot be tightened again without a data fix. Treat the delivery migration as one-way.
- Replacing the CHECK constraints briefly locks the small `parcels` and `parcel_status_history`
  tables during the migration.
- When the admin reports (PROGRESS 8.6 and 8.7) are built, success rate must not count `CANCELLED` or
  `PAUSED` as failures.
- Pause or cancel racing with a courier assignment or pickup is resolved by the existing `@Version`
  optimistic lock (409). Add a test for it.

## 4. Implementation phases

### Phase 1. PAUSED and CANCELLED (delivery module)

- `delivery/api/ParcelStatus.java`: add `PAUSED`, `CANCELLED`.
- `delivery/domain/ParcelLifecycle.java`: keep the existing courier transition table unchanged and add
  a separate **merchant-hold table**: `CREATED -> {PAUSED, CANCELLED}`, `PAUSED -> {CREATED, CANCELLED}`.
  `CANCELLED` is terminal (`isTerminal`). Couriers never see or request the new statuses
  (see section 3A, rows 1 and 2).
- Add `CANCELLED` to the three terminal sets: `ParcelQueryService.TERMINAL`,
  `CourierParcelService.TERMINAL` and `LiveTracking.isFinal`, preferably by calling
  `ParcelLifecycle.isTerminal`.
- `delivery/domain/Parcel.java`: add `pause`, `resume`, `cancel`. They check `ParcelLifecycle.canMove`
  and bump `lastSequence`. They must not go through `changeStatus`, which is courier-only.
- New `delivery/api/MerchantParcelControl` (`pause`, `resume`, `cancel` taking `merchantId`,
  `merchantOrderReference`, `merchantUserId`, `reason`), implemented by
  `delivery/application/MerchantParcelControlService`. It loads the parcel through
  `findByMerchantIdAndMerchantOrderReference` (another merchant's order is a 404), rejects any parcel
  whose `source` is not `MERCHANT`, and records the
  change with `saveAndRecord(parcel, new Actor(userId, ActorRole.MERCHANT), now)`, the same path
  `ParcelBookingService.bookForMerchant` uses.
- Migration `V<yyyyMMddHHmm>__delivery_add_pause_cancel_statuses.sql`: replace `ck_parcels_status`,
  `ck_parcel_status_history_from_status` and `_to_status` with the eight statuses, and relax
  `ck_parcels_courier_after_created` to
  `status IN ('CREATED','PAUSED','CANCELLED') OR assigned_courier_id IS NOT NULL`. Never edit merged
  migrations.
- A pause or cancel reason goes in the history note only. Events carry codes, not free text;
  `ParcelStatusChangedData` already accepts the new statuses.
- Paused parcels leave the dispatcher queue automatically (`findUnassignedParcels` selects `CREATED`).
  A courier cannot pick up a `PAUSED` parcel because the lifecycle has no such transition.

### Phase 2. Merchant order actions (backend and merchant portal)

Backend, in `merchant/web/MerchantDashboardController.java` (JWT `ROLE_MERCHANT`, approved merchant):

- `POST /api/v1/merchants/me/orders/{orderReference}/holds` pauses (body `{reason?}`).
- `DELETE /api/v1/merchants/me/orders/{orderReference}/holds` resumes.
- `POST /api/v1/merchants/me/orders/{orderReference}/cancellations` cancels (body `{reason?}`).
- Illegal transitions return 409 with a clear message (extend `MerchantExceptionHandler`).
- `MerchantDashboardItemResponse` gains `allowedActions`: `CREATED -> [PAUSE, CANCEL]`,
  `PAUSED -> [RESUME, CANCEL]`, otherwise empty. An invalid `status` filter returns 400.

Frontend, in `frontend/app/(merchant)/merchant/orders/page.tsx` and `frontend/lib/api.ts`:

- Add `PAUSED` and `CANCELLED` to `ParcelStatus`, the filter pills and badge colours; extract one
  shared `StatusBadge` (it is duplicated in three pages today).
- Actions column with per-row Pause / Resume / Cancel buttons driven by `allowedActions`, a confirm
  dialog with an optional reason (reuse the modal pattern from `admin/users/page.tsx`), per-row
  loading state, an inline error banner, and a refetch on success. Add a tracking link per row.
- `app/track/tracking-page.tsx`: add the two statuses to `PARCEL_STATUSES` (otherwise
  `isPublicTrackingView` rejects them); `CANCELLED` is final, `PAUSED` is not.

### Phase 3. Dispatcher (dispatch module and dispatcher portal)

- Backend (PROGRESS 7.2 to 7.5, `ROLE_DISPATCHER`): `GET /api/v1/unassigned-parcels`
  (`ParcelQueries.findUnassignedParcels`), `GET /api/v1/couriers` with workloads
  (`ParcelQueries.countActiveParcelsByCourier`), `POST /api/v1/parcel-assignments`
  (`ParcelAssignment.assignCourier`, 409 unless the parcel is `CREATED`). Reuse `GET /api/v1/parcels`
  for the full list, merchant parcels included.
- Frontend `app/(dispatch)/dispatcher/page.tsx`: tabs Unassigned / On hold / In progress / Closed,
  a merchant-order badge (source `MERCHANT`, reference) and an assign-courier dialog.
- Courier eligibility must follow the current RBAC: couriers are created by an admin and have no
  approval step (see [rbac-authorization.md](rbac-authorization.md)).

### Phase 4. Webhook pipeline (SwiftDrop to the store)

Follows ADR 0004 sections 2, 7 and 9. Safeguards from section 3A apply to the whole phase: the relay,
consumer and sender are conditional on `swiftdrop.messaging.relay.enabled` (off in tests, with
listeners not auto-started), a delivery migration marks the existing outbox rows as published so no
historic webhooks fire, and the notification queue is either given a logging consumer or left unbound.

1. **Outbox relay** (`delivery/infrastructure/messaging`): a scheduled poller selects unpublished
   `OutboxEvent` rows with `FOR UPDATE SKIP LOCKED`, publishes the stored JSON to exchange
   `swiftdrop.events` with key `parcel.status-changed` (publisher confirms), then sets `publishedAt`
   or bumps `attempts` and `nextAttemptAt`.
2. **Merchant consumer** (`merchant/infrastructure/messaging`): `@RabbitListener` on
   `merchant.parcel-status-changed`. Skip events without `ownerMerchantId`. For each active
   `WebhookEndpoint` create a `WebhookDelivery` in `PENDING`, unique on `event_id` plus `endpoint_id`.
   Acknowledge only after the row is saved.
3. **Sender** (`merchant/application`): a scheduled worker over `findDueDeliveries` (add
   `SKIP LOCKED`). Body is the stored event JSON (IDs and codes only). Headers:
   `X-SwiftDrop-Event-Id`, `X-SwiftDrop-Event-Type`, `X-SwiftDrop-Timestamp`,
   `X-SwiftDrop-Signature: v1=<hex HMAC-SHA256("<timestamp>.<raw body>")>`. This is what the store's
   `verifyV1` already expects. Timeouts 5 s connect and 10 s response, no redirects. 2xx is success;
   connection errors, timeouts, 5xx, 408 and 429 are retried (30 s base, x2, +-20 % jitter, 1 h cap,
   8 attempts); other 4xx fail. Never log bodies or secrets.
4. **URL and SSRF rules** in `WebhookEndpointService.validateUrl`: https on 443, no userinfo, resolve
   and block private, loopback, link-local and metadata ranges, and re-check on every attempt. The
   flag `SWIFTDROP_WEBHOOK_ALLOW_LOCAL=true` permits `http://localhost:3100/api/webhooks/swiftdrop`
   for development only (off by default).
5. **Encrypt webhook secrets** with AES-256-GCM (AAD is endpoint id plus key id, keys from
   `SWIFTDROP_WEBHOOK_KEYS`). A new migration adds `secret_key_id`; legacy Base64 rows stay readable
   until re-saved.
6. Merchant-visible delivery history and manual redelivery (cap 3) can follow last.

### Phase 5. Verifying the request comes from the store

- Migration adds a per-key `signing_secret_encrypted` (HMAC needs a recoverable secret, so it is
  encrypted, not hashed). Key creation returns `sd_live_...` and a one-time signing secret; both are
  shown once on `merchant/api-keys`. Existing keys keep working while
  `SWIFTDROP_MERCHANT_API_REQUIRE_SIGNATURE=false`.
- `MerchantApiKeyFilter` requires `X-SwiftDrop-Timestamp` and
  `X-SwiftDrop-Signature: v1=hex(HMAC_SHA256(signingSecret, "<ts>.<METHOD>.<path>.<sha256(body)>"))`.
  Reject timestamps outside plus or minus 5 minutes and any mismatch (constant-time compare) with a
  generic 401. Use a body-caching wrapper so the controller can still read the body.
- Extend `config/RateLimitingFilter` with a separate limit group for `/api/v1/merchant-api/**` (per
  key prefix, falling back to IP). Do **not** change how the filter treats `X-Forwarded-For` or the
  existing auth and registration groups; that filter also protects login (section 3A, row 6).
- Same `merchantOrderReference` with a different payload returns 409 instead of silently returning
  the first parcel (`MerchantParcelCreation`).

### Phase 6. Ecommerce site changes (`D:\ecommerce site\ecommerce`)

- Prisma migration: `DeliveryStatus` gains `PAUSED` and `CANCELLED`; `Shipment.state` gains
  `CANCELLED`; `Shipment.lastSequence Int` is added.
- `src/lib/swiftdrop/{types,status,apply-status,webhook}.ts`: accept the new statuses and apply an
  event only when `sequence > lastSequence` (resume goes `PAUSED -> CREATED`, so "forward only" no
  longer holds). The poller uses the number of public history entries as the sequence. `CANCELLED`
  is final and restores stock in the same transaction (the reverse of the decrement in
  `actions/checkout.ts`).
- `live.ts createOrder` adds the signing headers from a new `SWIFTDROP_SIGNING_SECRET`
  (`src/lib/env.ts`, `.env.example`), with vitest cases for signing and the new transitions.
- UI (`StatusBadge`, `lib/tracking.ts`, `DeliveryTimeline`, `LiveOrderStatus`, `account/orders`):
  show "On hold" and "Cancelled", show the tracking number on `/account/orders`, and extend the admin
  simulate buttons.
- `scripts/sync-zones.ts`: the SwiftDrop seed migration uses zone codes
  `LK-WP, LK-CP, LK-SP, LK-NP, LK-EP, LK-NWP, LK-NCP, LK-UP, LK-SGP`, while the store's `DISTRICTS`
  uses `WP, CP, SP, NP, EP, NWP, NCP, UVA, SAB`. Map `UVA -> LK-UP`, `SAB -> LK-SGP` and prefix the
  rest with `LK-`; otherwise every district stays unmapped and checkout refuses all orders.
- Schedule `/api/cron/swiftdrop` (Task Scheduler or `npm run cron:local`). Nothing triggers retries
  or polling today.

## 5. Connection runbook

Do this after the phases above are built. Steps 1 to 3 work today for plain order creation.

1. **Infrastructure.** In SwiftDrop run `docker compose up -d` and confirm both services are healthy.
   Start the store database with its own `compose.yaml` (port 5434).
2. **Backend.** Set the environment in the shell that starts the backend (Spring does not read
   `.env`): `SWIFTDROP_BOOTSTRAP_ADMIN_EMAIL`, `SWIFTDROP_BOOTSTRAP_ADMIN_PASSWORD`,
   `SWIFTDROP_WEBHOOK_KEYS`, `SWIFTDROP_WEBHOOK_ACTIVE_KEY_ID`, and `SWIFTDROP_WEBHOOK_ALLOW_LOCAL=true`
   for development. Then run `.\mvnw.cmd spring-boot:run` from `backend/`. Flyway seeds nine
   provincial zones and a pricing rule for every zone pair.
3. **Merchant account.** Run the frontend (`npm run dev`, port 3000). Sign up as a merchant at
   `/signup`, then log in as admin and approve it
   (`PATCH /api/v1/admin/merchants/{id}/status` with `{"action":"APPROVE"}`).
4. **Credentials.** In the merchant portal create an API key (copy the `sd_live_...` key and the
   signing secret; both are shown once) and register the webhook URL
   (`http://localhost:3100/api/webhooks/swiftdrop` in development, or a public HTTPS URL) with a secret
   of at least 16 characters.
5. **Zone.** Call `GET /api/v1/zones` while logged in and note the UUID of the store's pickup zone.
6. **Store `.env`.** Set `SWIFTDROP_MODE=live`, `SWIFTDROP_API_URL=http://localhost:8080`,
   `SWIFTDROP_API_KEY`, `SWIFTDROP_SIGNING_SECRET`, `SWIFTDROP_WEBHOOK_SECRET` (the secret from step 4),
   `STORE_PICKUP_*`, `STORE_PICKUP_ZONE_ID`, `CRON_SECRET`, and `SWIFTDROP_MERCHANT_EMAIL` and
   `SWIFTDROP_MERCHANT_PASSWORD` for the zone sync only. Then run `npm run zones:sync`, `npm run dev`
   (port 3100) and `npm run cron:local`.
7. **Operations.** An admin creates a dispatcher and a courier account. The dispatcher assigns the
   courier to the new parcel; the courier advances statuses at `/courier`.
8. **Test** with the end-to-end checks in section 8.

Never put the API key, signing secret or webhook secret in logs, commits or chat.

## 6. Rollout order

1. Store and frontend first. This is a **hard gate**: the store (types, sequence ordering, signatures)
   and the frontend `/track` page must accept `PAUSED` and `CANCELLED` before the backend can produce
   them (SwiftDrop still accepts unsigned requests with `REQUIRE_SIGNATURE=false`).
2. SwiftDrop lifecycle, merchant actions and dispatcher.
3. Webhook pipeline.
4. Set `SWIFTDROP_MERCHANT_API_REQUIRE_SIGNATURE=true` and rotate keys.

## 7. Documents to update in the same changes

`adr/0006-delivery-lifecycle-and-transition-rules.md`, `implementation-contract.md` (D-06, D-17, and
the path and key-format drift in sections 4.2 and 8.3), `merchant-module.md` (new endpoints; webhooks
built), `messaging-stage-2.md`, `architecture.md`, `PROGRESS.md` (rows 5.4 to 5.7, 7.2 to 7.5, 9.2),
`development-setup.md` (new variables) and `.env.example`.

## 8. Verification

- **Unit (`.\mvnw.cmd clean verify`, no Docker):** lifecycle transitions and terminal states;
  `Parcel` pause, resume and cancel with ownership; `MerchantParcelControlService`; signature verifier
  (valid, tampered, stale, replayed); webhook signer checked against the store's `verifyV1`; SSRF
  validator (private IPs, redirects, userinfo); backoff calculator; dashboard API and authorization
  matrix rows for holds and cancellations (customer, courier and another merchant get 403 or 404);
  `MigrationConventionTest` and `DatabaseMigrationIT` counts. Also: a courier request to `PAUSED` or
  `CANCELLED` is rejected; a cancelled parcel leaves the courier list and workload count and closes its
  SSE stream; pause racing with assignment returns 409; the full `clean verify` is run as soon as the
  first scheduled bean is added; and enabling the relay fires no webhook for pre-existing events.
- **Integration (`-Pintegration`, Testcontainers PostgreSQL and RabbitMQ):** outbox, RabbitMQ,
  consumer, then a signed POST to an embedded stub server; retry and dead-letter behaviour; fresh-database
  Flyway and Hibernate `validate`.
- **Store:** `npm test` (signing, out-of-order and duplicate events, cancel restocks) and `npm run build`.
- **End to end, both apps running:** place an order on port 3100; it appears in the merchant portal and
  the dispatcher screen; assign a courier; Pause in the merchant portal and the store dashboard shows
  "On hold" within seconds; Resume; the courier picks up and each status reaches the store. A second
  order: Cancel and the store shows "Cancelled" with stock restored. A wrong signature, stale timestamp
  or revoked key returns 401. Stop the webhook receiver and deliveries retry, then succeed after restart.
  With webhooks off, polling alone converges.

## 9. Risks

- Three backend integration tests fail today and are unrelated to this work: `DeliveryQueriesIT`,
  `DispatchPersistenceIT` and `RabbitMessagingIT`. Fix or exclude them before relying on the
  integration profile.
- The outbox relay and webhook sender are the largest new pieces; put them behind a feature flag so
  the rest can ship independently.
- `MerchantOrderService` builds `NewMerchantParcel` through reflection; clean it up when touching it.
- Cancelling after the store has taken payment is a refund matter that neither system models.
