# Stage 2: RabbitMQ topology

The backend now declares the publisher-owned durable topic exchange `swiftdrop.events`.
Each consumer declares its own durable quorum queue, binding and dead-letter queue:

| Consumer | Queue | Binding key on swiftdrop.events | Dead-letter queue |
|---|---|---|---|
| merchant | merchant.parcel-status-changed | parcel.status-changed | merchant.parcel-status-changed.dlq |
| notification | notification.parcel-status-changed | parcel.status-changed | notification.parcel-status-changed.dlq |

The shared durable direct exchange `swiftdrop.dead-letter` routes using the source queue name.
Both consumer modules declare that exchange identically. Spring's RabbitAdmin declares exchanges
before queues and bindings when a connection is established; there is no business consumer yet.

This implements the topology portion of
[ADR 0004](adr/0004-messaging-and-integration-reliability.md), sections 3, 5 and 9.
The outbox relay, processing listeners, ordering, deduplication and webhook sender belong to
later stages. Declaring queues alone does not send events from delivery.

## Settings

Publisher confirms are correlated; publisher returns and mandatory publishing are enabled.
A future relay must supply CorrelationData, wait for its confirm with a bounded timeout and
check for a returned message. An ack alone is not success when the message was unroutable.
Retries must retain the original event ID, payload and persistent delivery mode.

Source queues use at-least-once dead-lettering and reject-publish overflow with these defaults:

| Environment variable | Default | Validation |
|---|---|---|
| SWIFTDROP_MESSAGING_DELIVERY_LIMIT | 5 | Integer from 2 to 20 |
| SWIFTDROP_MESSAGING_QUEUE_MAX_LENGTH | 100000 | Positive integer |

Set these before starting the backend, or use the existing local configuration mechanism.
Both consumers use the same tuning. Simple listener failures are configured not to requeue;
bounded application retries and acknowledgement after commit must be implemented with listeners.

Dead-letter queues have no TTL, maximum length or dead-letter exchange. The quorum delivery
limit is a source-queue crash-loop backstop. Review retained dead letters operationally as
described in ADR 0004; no automatic replay or deletion is implemented.

Queue arguments are immutable for an existing queue: changing tuning can cause a declaration
failure. Do not delete existing queues or volumes to resolve that failure without a reviewed
migration/recovery plan. No existing broker data is changed by the tests.

## Run and verify

The normal backend startup declares topology when it first connects to RabbitMQ.
With the local broker running, check the required feature flag and declared resources:

```powershell
docker compose exec rabbitmq rabbitmqctl list_feature_flags
docker compose exec rabbitmq rabbitmqctl list_exchanges name type durable
docker compose exec rabbitmq rabbitmqctl list_queues name type durable arguments
docker compose exec rabbitmq rabbitmqctl list_bindings source_name destination_name routing_key
```

The `stream_queue` flag must be enabled for at-least-once dead-lettering. The isolated broker
test asserts that requirement rather than silently changing flags on a developer's broker.

From `backend/`, use Java 21:

```powershell
.\mvnw.cmd clean verify
.\mvnw.cmd verify -Pintegration -Dit.test=RabbitMessagingIT
```

The first command runs the Docker-free suite, including configuration and queue-bound tests.
The second starts a disposable RabbitMQ 4.3 container via the existing Testcontainers core
dependency. It tests a real serialized sample event reaching both queues, persistent properties,
correlated confirms, unroutable returns, isolated dead letters with death headers and recovery
when a dead-letter queue is restored. It does not read .env, use development containers or send
HTTP webhooks. No new dependency is needed.

These tests are not the complete end-to-end outbox/notification/webhook integration test; that
requires the later stages. Docker is required only for the integration profile.

## Verification in this workspace

Verified on 2026-11-13 with IntelliJ's bundled Java 21.0.9 (JBR), selected only for the build process:

- Maven Wrapper clean verify: BUILD SUCCESS; 439 tests, zero failures/errors/skips.
- Includes 9 RabbitMessagingConfigurationTest cases and the module-boundary checks.
- RabbitMessagingIT compiled, but was not executed because Docker is unavailable.
- git diff --check and separate whitespace checks of new files passed.

Actual RabbitMQ routing, confirms/returns and dead-letter recovery remain unverified in this
workspace until the broker integration command above runs successfully. No development broker
or queue was changed.
