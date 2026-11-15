# Stage 4: secure merchant webhooks

Stage 4 connects the Stage 3 merchant inbox to durable delivery jobs and signed HTTP POSTs.
It supports multiple endpoints per merchant. Only approved merchants can register endpoints.
No frontend or merchant-facing secret rotation is included.

## Enable locally

Keep both flags off until PostgreSQL migrations and RabbitMQ are available. Supply configuration
through your environment or your untracked root `.env`; never commit real keys.

| Variable | Default | Meaning |
|---|---|---|
| `SWIFTDROP_MESSAGING_PROCESSING_ENABLED` | false | Stage 3 relay and consumers |
| `SWIFTDROP_WEBHOOKS_ENABLED` | false | Webhook APIs, job adapter and sender |
| `SWIFTDROP_WEBHOOKS_ACTIVE_KEY_ID` | empty | Key used for new endpoint secrets |
| `SWIFTDROP_WEBHOOKS_KEYS` | empty | Comma-separated `keyId:base64Key` entries; each key decodes to 32 bytes |
| `SWIFTDROP_WEBHOOKS_ALLOW_LOCAL` | false | Allows HTTP and arbitrary ports only for localhost / 127.0.0.1 / ::1 |
| `SWIFTDROP_WEBHOOKS_DENIED_HOSTS` | empty | Comma-separated backend/service host names to refuse |
| `SWIFTDROP_WEBHOOKS_POLL_MS` | 5000 | Sender poll interval |

Generate encryption keys with a cryptographically secure generator and save them privately.
Startup fails when webhooks are enabled with missing or malformed keys. Keep every key ID still
referenced by an endpoint when adding a new active key; automatic re-encryption is not implemented.
Never enable local destinations in production.

Register an endpoint **before** generating demo parcel events. An event creates jobs for endpoints
active when the inbox applies it; adding an endpoint does not replay old processed events.
If Stage 3 retained events while the adapter was absent, its reconciliation poll will now apply them.

## API

All requests require a bearer JWT. Creation returns the Base64-encoded signing secret **once** with
`Cache-Control: no-store`. The receiver must Base64-decode it before computing HMAC.

| Method and path | Access |
|---|---|
| POST /api/v1/merchants/me/webhook-endpoints | Approved MERCHANT; body `{"url":"https://merchant.example/webhook"}` |
| GET /api/v1/merchants/me/webhook-endpoints | MERCHANT; own endpoints, no secrets |
| DELETE /api/v1/webhook-endpoints/{id} | Owning MERCHANT or DISPATCHER_ADMIN; disables, does not delete history |
| GET /api/v1/webhook-deliveries?page=0 | MERCHANT sees own records; DISPATCHER_ADMIN sees all; 50 per page |
| GET /api/v1/webhook-deliveries/{id}/attempts | Owning MERCHANT or DISPATCHER_ADMIN; safe outcome/status/timing history |

Other roles get 403; anonymous requests get 401. Another merchant's endpoint or delivery ID returns 404.
Merchant ownership comes from the authenticated user's merchant row, never from request fields.
Disabling an endpoint prevents new jobs and sends claimed after the disable; an already in-flight
HTTP request cannot be recalled.

## Delivery and security

- Jobs are inserted in the same transaction as the ordered inbox effect, unique by event and endpoint.
- The worker claims one due job at a time using `FOR UPDATE SKIP LOCKED`, commits a two-minute lease,
  then sends outside the transaction. A lease token prevents an older worker overwriting newer results.
  Each poll handles at most 20 jobs. A crash can cause duplicate delivery.
- Each real attempt is recorded before sending. An interrupted attempt remains `IN_FLIGHT` in history.
  Eight attempts maximum, 30-second exponential backoff, +/-20% jitter, capped at one hour.
  Retry-After seconds or HTTP dates are bounded by the same cap.
- Any 2xx succeeds; network errors, timeouts, 408, 429 and 5xx retry. Redirects and other HTTP errors
  fail permanently. Disabled endpoints and unapproved owners are not sent.
- AES-256-GCM protects each 32-byte random signing secret, with a fresh 12-byte nonce and authenticated
  endpoint/key IDs. Missing keys or tampering produce `SECRET_UNAVAILABLE`, no HTTP request and no
  consumed attempt. A 60-second recheck resumes after key restoration.
- HTTPS/443 is required outside local mode. All DNS answers must pass the IPv4/IPv6 restrictions in
  ADR 0004. Validation runs at registration, before each send, and in the connection resolver.
- Apache HttpClient 5 connects only to that resolver's checked addresses, preserves certificate and
  hostname verification, uses a fresh connection manager each attempt, disables redirects and internal
  retries, and explicitly creates sockets with `Proxy.NO_PROXY` to block system SOCKS routing too.
- Connect timeout is five seconds; response/socket timeout is ten seconds. Response bodies are not
  consumed or stored. HTTP client logging is disabled to avoid exposing bodies or signatures.
- Outcome metrics: `swiftdrop.webhooks.attempts`, `swiftdrop.webhooks.secret.unavailable`,
  `swiftdrop.webhooks.poll.failures`. Do not enable HTTP wire logging.

## Receiver contract

Read the raw UTF-8 request body before parsing JSON. Headers:

- `X-SwiftDrop-Event-Id`: stable event UUID.
- `X-SwiftDrop-Event-Type`: `parcel.status-changed`.
- `X-SwiftDrop-Timestamp`: Unix seconds for this attempt.
- `X-SwiftDrop-Signature`: `v1=` followed by lowercase hexadecimal
  HMAC-SHA256 of `<timestamp>.<raw body>` using the decoded secret.

Compare signatures in constant time; reject timestamps more than five minutes from your clock.
Deduplicate event IDs for at least that window and apply only the highest sequence per parcel.
Retries preserve the stored body and event ID but receive a fresh signing timestamp.
Return a 2xx response promptly after durably accepting the event.

## Validation and next stage

Docker-free tests cover encryption/tampering/key changes, address ranges and mixed DNS responses,
real HTTP signatures, connection-time DNS rebinding, no proxy routing, no redirects or stale pooled
connections, untrusted TLS certificates, trusted matching TLS, hostname mismatch, retry classification,
and API role enforcement.

`WebhookStoreIT` uses PostgreSQL 18 to check job idempotency and transaction rollback, ownership,
approval, leases, success, secret recovery and retry exhaustion. Run from `backend` with Docker:
`.\\mvnw.cmd verify -Pintegration -Dit.test=WebhookStoreIT`.
The default `.\\mvnw.cmd clean verify` needs no Docker.

Stage 5 remains: audited bounded manual redelivery, configurable retry tuning, a receiver demo,
end-to-end RabbitMQ/PostgreSQL verification, retention/operational recovery and deployment checks.
The PostgreSQL integration tests have been added but could not run in this workspace because
Docker is unavailable. No live merchant delivery has been claimed or demonstrated.
