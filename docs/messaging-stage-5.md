# Stage 5: retries, recovery tests and webhook demo

## Merge reconciliation

The merged repository originally contained two incompatible migrations creating the same webhook tables:
`V202611091220__merchant_create_webhooks.sql` and `V202611141600__merchant_webhooks.sql`.
The Stage 4 migration now extends the earlier tables. Legacy records lack authenticated
payloads and AES-encrypted secrets, so they are quarantined as failed and inactive. The old endpoint route returns 410 when secure
webhooks are enabled; merchants should use the secure Stage 4 route.

The correction changes the Stage 4 migration checksum. A database that already applied that version
needs its migration history reviewed before upgrade; do not run Flyway repair blindly.
The backend demo procedure below requires the corrected migrations.

## Added behavior

- Configurable automatic retries, exponential backoff with +/-20% jitter, capped Retry-After,
  and a finite number of attempts per series.
- `POST /api/v1/webhook-deliveries/{id}/redeliveries` returns 202 for an eligible failed delivery.
  The owning MERCHANT or ADMIN can request it. ADMIN follows the merged role split; DISPATCHER
  does not gain merchant-management access.
- Redelivery locks the delivery, endpoint and owner, checks approval and endpoint activity, enforces
  the cap, inserts the actor/role/time audit record, and atomically starts a new series.
  Concurrent requests cannot both succeed. Ineligible requests return 409; non-owned IDs return 404.
- Previous attempts remain intact: lifetime attempt numbers continue increasing, while the new
  series gets a fresh retry budget. The original event ID/body remain unchanged. Every send still
  validates DNS/destination and signs with a fresh timestamp.
- Attempts for each manual series begin at `merchant_webhook_redeliveries.first_attempt`; the next
  audit entry marks the following series. Audit rows are retained, never automatically deleted.

| Environment variable | Default | Bounds |
|---|---|---|
| SWIFTDROP_WEBHOOKS_MAX_ATTEMPTS | 8 | 3..15 per series |
| SWIFTDROP_WEBHOOKS_RETRY_BASE_SECONDS | 30 | 1..300; use 2 for demo |
| SWIFTDROP_WEBHOOKS_RETRY_CAP_SECONDS | 3600 | At least base, at most 3600 |
| SWIFTDROP_WEBHOOKS_MAX_REDELIVERIES | 3 | 1..10 |

Invalid settings fail startup when webhooks are enabled. The Stage 4 encryption/local-mode
configuration still applies; webhooks and messaging processing remain disabled by default.

## Receiver-only demo and tests (available now)

From the repository root, using the project's Node.js 24 runtime:

```powershell
node --test tools/webhook-demo/receiver.test.mjs
```

This runs real local HTTP requests against the demo receiver and checks two simulated 503s followed
by success, duplicate suppression, highest-sequence ordering, persistence across restart, invalid
signatures, stale timestamps, and event/header mismatches. It needs no npm packages or Docker.
It does **not** prove the backend/database integration.

The receiver verifies HMAC over the raw body, compares signatures in constant time and checks a
five-minute timestamp window. Event IDs persist for 24 hours and highest sequences persist across
restarts in ignored `.local/webhook-demo/state.json`. Only IDs, sequence numbers and outcome categories
are logged. It listens only on 127.0.0.1 and has a 64 KiB request-body limit.

## Backend demo, after migration reconciliation

1. Start PostgreSQL and RabbitMQ using the existing development setup.
2. Configure the Stage 4 encryption key set and active key ID privately. Preserve the same keys
   across backend restarts; generating a replacement key makes existing secrets unreadable.
3. Start the backend with:
   - `SWIFTDROP_MESSAGING_PROCESSING_ENABLED=true`
   - `SWIFTDROP_WEBHOOKS_ENABLED=true`
   - `SWIFTDROP_WEBHOOKS_ALLOW_LOCAL=true` (demo only)
   - `SWIFTDROP_WEBHOOKS_RETRY_BASE_SECONDS=2`
4. Obtain an approved merchant's bearer token and place it in `SWIFTDROP_DEMO_MERCHANT_TOKEN`
   in a separate PowerShell terminal. Register the endpoint without printing its one-time secret:

```powershell
$demoHeaders = @{ Authorization = "Bearer $env:SWIFTDROP_DEMO_MERCHANT_TOKEN" }
$demoEndpoint = Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/v1/merchants/me/webhook-endpoints" -Headers $demoHeaders -ContentType "application/json" -Body '{"url":"http://127.0.0.1:9090/webhooks"}'
$env:SWIFTDROP_DEMO_WEBHOOK_SECRET = $demoEndpoint.secret
$env:SWIFTDROP_DEMO_FAIL_FIRST = "2"
node tools/webhook-demo/receiver.mjs
```

Registration allows loopback even if the receiver has not started listening yet. Keep this terminal
running; use another terminal or the existing merchant UI/API to create a **new merchant parcel**.
The receiver prints two `simulated_failure` outcomes, then `accepted`.
Query `GET /api/v1/webhook-deliveries` and `GET /api/v1/webhook-deliveries/{id}/attempts` with the
same merchant token: expect HTTP statuses 503, 503, 200 and final state SUCCEEDED.
Do not paste secrets or bearer tokens into screenshots, commits or PRs.

To demonstrate exhaustion, use three maximum attempts and set `SWIFTDROP_DEMO_FAIL_FIRST=3` before
starting the receiver. A fresh event reaches FAILED after three attempts. Keep the receiver running
so its failure counter stays exhausted, then:

```powershell
$demoDeliveryId = "<failed-delivery-id>"
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/v1/webhook-deliveries/$demoDeliveryId/redeliveries" -Headers $demoHeaders
```

The next series succeeds. Attempt history retains the previous failures and the audit table records
who requested the new series. A second request while a series is active returns 409.

For notification isolation, create a customer-owned parcel, then move it to PICKED_UP with the
assigned courier's account. Verify its mock notification in `notification_messages`; it should
create no merchant webhook job. Merchant parcels should create webhook jobs and no customer SMS.

## Backend verification

From `backend`:

```powershell
.\\mvnw.cmd clean verify
.\\mvnw.cmd verify -Pintegration "-Dit.test=WebhookStoreIT,MessagingWebhookFlowIT,DatabaseMigrationIT"
```

The integration command requires Docker and corrected migrations. It uses isolated containers,
never the development database. `MessagingWebhookFlowIT` covers a committed outbox row through
RabbitMQ and the ordered merchant consumer to real signed HTTP attempts (503, 503, 204), duplicate
event processing, and customer-notification isolation. `WebhookStoreIT` also covers concurrent
manual requests, caps, actor audit and retained attempt history.

Docker is unavailable in this workspace; the integration tests are added and compiled, not executed.
A passing Docker-free build alone does not establish that merged Flyway migrations can run.

## Remaining operations

Retention cleanup, DLQ replay tooling and repair of stuck parcel inboxes remain separate operational
work. Do not delete unpublished outbox rows, pending events, attempts or audit records to clear errors.
Manual redelivery is for failed webhook records only; it does not replay broker dead letters.
