# Stage 3: outbox relay and ordered event processing

Stage 3 connects the existing delivery outbox to RabbitMQ and adds durable consumer processing.
It follows [ADR 0004](adr/0004-messaging-and-integration-reliability.md) and builds on
[Stage 2](messaging-stage-2.md).

Stage 4 now supplies this adapter when webhooks are enabled; see
[secure merchant webhooks](messaging-stage-4.md). The behavior below describes Stage 3 in isolation.

## Implemented behavior

- Outbox polling takes a PostgreSQL transaction-scoped advisory lock. One relay publishes at a time.
- Unpublished rows are read in ID order using keyset pages. A delayed or failing parcel blocks its
  own later rows while other parcels can progress. A broker failure ends the poll.
- Publication uses the stored JSON, original event ID and occurrence time, persistent messages,
  correlated confirms and mandatory returns. Only an ack without a return permits marking published.
- Failed attempts record a safe category and exponential backoff (1 second, doubling to 60 seconds).
  Failure to save publication results rolls back the poll; the same event can be published again.
- Each consumer owns separate state, processed-event and pending-event tables. Row locks serialize
  processing per parcel. Business effects and processing state commit together; AUTO acknowledgement
  happens after the listener returns from that transaction.
- Sequence gaps are held pending. After a 60-second grace period, reconciliation reads original events
  from delivery.api.ParcelEvents. Five failed recovery attempts, with backoff, mark the parcel stuck.
  Pending events are retained. A missing effect adapter is deferred without consuming failure attempts.
- Malformed/unsupported events are rejected immediately. Transient database failures get three total
  attempts with 1-second and 2-second delays, then rejection without requeue. Original messages reach
  the Stage 2 dead-letter queues. Application error logging excludes message bodies and exception details.
- Customer events create mock notification records on PICKED_UP, OUT_FOR_DELIVERY, DELIVERED and FAILED.
  CREATED and IN_TRANSIT advance ordering without a notification.
- Merchant events are durably pending until a MerchantWebhookEffect bean is supplied by Stage 4.
  That adapter must create per-endpoint jobs in the caller's transaction with a unique event/endpoint
  constraint. It must not send HTTP from the consumer transaction.

The merchant integration deliberately does not mark an event processed when its webhook effect
is unavailable. Stage 4 still owns endpoint registration, encrypted secrets, webhook delivery
records, signing and HTTP sending. No webhook job or HTTP request is produced in this stage.

## Enable the workers

Workers are opt-in. Flyway discovers the three new migrations on normal backend startup even when
workers are disabled. Configure the existing PostgreSQL and RabbitMQ settings first.

From backend/, with Java 21 and the infrastructure available:

```powershell
$env:SWIFTDROP_MESSAGING_PROCESSING_ENABLED = "true"
.\mvnw.cmd spring-boot:run
```

On this workstation Java 21 is available at
`C:\Program Files\JetBrains\IntelliJ IDEA 2025.3.2\jbr`; set JAVA_HOME for the terminal if its
default java is still version 11. Do not enable workers in unit/slice test runs.

| Environment setting | Default | Meaning |
|---|---|---|
| SWIFTDROP_MESSAGING_PROCESSING_ENABLED | false | Enable relay, consumers and scheduled reconciliation |
| SWIFTDROP_MESSAGING_RELAY_POLL_MS | 1000 | Poll interval, validated between 500 and 10000 ms |
| SWIFTDROP_MESSAGING_RELAY_BATCH_SIZE | 100 | Maximum publish attempts per poll, 1 to 500 |
| SWIFTDROP_MESSAGING_CONFIRM_TIMEOUT_SECONDS | 5 | Confirm wait, 1 to 30 seconds |
| SWIFTDROP_MESSAGING_RECONCILIATION_POLL_MS | 5000 | Reconciliation scheduler interval |

The relay never deletes unpublished rows. Automatic retention cleanup is not included.
A failed database transaction leaves rows retryable after recovery.

## Observe and recover

Micrometer records outbox publication outcomes and poll failures, backlog, oldest event age and
last completed poll time. Inboxes record duplicates, anomalies, gaps, applied effects, deferred
effects, recovery failures and stuck parcels. Metrics follow existing Actuator exposure settings.

Inspect the consumer-owned *_event_pending and *_event_state tables for deferred/stuck events.
Merchant pending events are expected while the Stage 4 adapter is absent. Installing that adapter
allows reconciliation to drain them, preserving their original IDs and order.

For a stuck parcel, fix the underlying cause, then use a reviewed migration or an audited
administrative operation to reset its attempts/stuck/next-attempt fields. Do not delete its pending
events or processed IDs. No new administrative replay endpoint is introduced.

## Verification

From backend/:

```powershell
.\mvnw.cmd clean verify
.\mvnw.cmd verify -Pintegration "-Dit.test=OrderedInboxIT,RabbitMessagingIT,DatabaseMigrationIT"
```

The unit suite covers publication identity/persistence, confirms, returns, nacks, timeout,
broker failure, advisory-lock exclusion, per-parcel blocking and republishing after a failed mark.
It also checks malformed-event rejection and discovers all 11 Flyway migrations.

OrderedInboxIT uses real PostgreSQL and the actual migrations to check ordering, duplicates,
missing-effect retention, reconciliation, transactional rollback and stuck-state retention.
RabbitMessagingIT remains the real-broker topology test from Stage 2.

Docker is unavailable on this workstation, so database/broker integration tests are compiled
but have not been executed here. Full end-to-end webhook-job verification requires Stage 4.

Validation on 2026-11-14: Java 21 clean verify passed with 451 tests, zero failures, errors or
skips (12 additional unit cases). Integration test sources compiled. git diff --check passed.
The test run overrode spring.config.import to avoid reading local .env settings.
