# 0006. Delivery lifecycle and transition rules

- Status: Proposed
- Date: 2026-10-28
- Amended: 2026-11-19 (courier moves depend on the leg type, section 1B).
- Amended: 2026-11-15 (merchant hold and cancellation before pickup; still Proposed, pending team
  review). See [section 1A](#1a-merchant-hold-release-and-cancellation-amendment). The original text
  below is kept where it still applies; sections 2 and the alternatives list are updated.

## Context

[ADR 0003](0003-backend-modules-and-boundaries.md) established the backend modular monolith
boundaries and decided that the `delivery` module alone owns parcel state, status transitions, and
history. It defined the basic lifecycle sequence from the assignment brief:
`CREATED → PICKED_UP → IN_TRANSIT → OUT_FOR_DELIVERY → DELIVERED / FAILED`.

However, ADR 0003 left several operational and transition rules unresolved:
- The exact allowed transitions between statuses and whether transitions can skip steps.
- The stages from which `FAILED` is permitted and the standardized failure reason codes (D-19).
- Assignment and reassignment rules and their status boundaries.
- Delivery proof requirements for `DELIVERED` (D-12) and how captured proof is handled on failure.
- The handling of retries, cancellations, and dispatcher/admin status overrides.
- Receipt availability before and after delivery (D-13).
- HTTP error mapping conventions for lifecycle and ownership violations.

[ADR 0004](0004-messaging-and-integration-reliability.md) established the transactional outbox
pattern, monotonic sequence numbers per parcel, and the requirement that status changes commit
atomically with outbox domain events.

This ADR decides the delivery lifecycle, transition, and proof rules (D-06, D-12, D-13, D-19) to
unblock implementation of the delivery domain and endpoints.

## Decision

### 1. Status Transition Matrix

The `delivery` module strictly enforces the following state transitions:

| Source Status | Target Status | Authorized Caller | Preconditions & Validation Rules | Outbox Event Published |
|---|---|---|---|---|
| `CREATED` | `PICKED_UP` | Assigned Courier | Courier must be assigned (`assigned_courier_id != null`). | Yes (`parcel.status-changed`) |
| `PICKED_UP` | `IN_TRANSIT` | Assigned Courier | Normal sequential progression. | Yes (`parcel.status-changed`) |
| `PICKED_UP` | `FAILED` | Assigned Courier | Mandatory `failureReasonCode` from standard list. | Yes (`parcel.status-changed`) |
| `IN_TRANSIT` | `OUT_FOR_DELIVERY` | Assigned Courier | Normal sequential progression. | Yes (`parcel.status-changed`) |
| `IN_TRANSIT` | `FAILED` | Assigned Courier | Mandatory `failureReasonCode` from standard list. | Yes (`parcel.status-changed`) |
| `OUT_FOR_DELIVERY` | `DELIVERED` | Assigned Courier | Valid delivery proof (`delivery_proofs` record with non-blank `recipient_name`) must exist for the parcel. | Yes (`parcel.status-changed`) |
| `OUT_FOR_DELIVERY` | `FAILED` | Assigned Courier | Mandatory `failureReasonCode` from standard list. | Yes (`parcel.status-changed`) |
| `DELIVERED` | None | None | Terminal state. No further updates permitted. | No |
| `FAILED` | None | None | Terminal state in v1. No retries, transitions, or reopen. | No |

### 1A. Merchant hold, release and cancellation (amendment)

An external store (the merchant) needs to stop a delivery while it is still preparing the goods, or
call it off, without involving a courier. Two statuses are added: `PAUSED` (a hold that can be
released) and `CANCELLED` (final). They are reachable **only by the owning merchant and only before
pickup**:

| Source Status | Target Status | Authorized Caller | Preconditions & Validation Rules | Outbox Event Published |
|---|---|---|---|---|
| `CREATED` | `PAUSED` | Owning merchant | Parcel `source` is `MERCHANT` and `merchant_id` matches. | Yes (`parcel.status-changed`) |
| `PAUSED` | `CREATED` | Owning merchant | Resume. The assigned courier, if any, is kept. | Yes (`parcel.status-changed`) |
| `CREATED` | `CANCELLED` | Owning merchant | As for `PAUSED`. | Yes (`parcel.status-changed`) |
| `PAUSED` | `CANCELLED` | Owning merchant | As for `PAUSED`. | Yes (`parcel.status-changed`) |
| `CANCELLED` | None | None | Terminal. No reopen. | No |

Rules that keep the rest of the lifecycle intact:

- The courier transition table of section 1 is **unchanged**. A courier can neither move a parcel
  into or out of `PAUSED`, nor out of `CANCELLED`; a request for those statuses is a `409`.
- A `PAUSED` parcel cannot be picked up and cannot be assigned or reassigned (assignment stays
  `CREATED` only). A courier already assigned stays assigned through a hold.
- Once a parcel is `PICKED_UP` or later, the merchant cannot hold or cancel it (`409`).
- A merchant that does not own the parcel, and a parcel that is not a merchant parcel, get `404`, as
  for couriers (section 7).
- Each change takes the next sequence number, so a hold, a release and a second hold are three
  distinct events. The merchant's free-text reason is stored in the status history only, never in the
  event or webhook (ADR 0004).
- Dispatchers and administrators do **not** gain a hold or cancel power in this amendment.
- `CANCELLED` is final: it is excluded from courier workloads and the courier's day list, and it
  closes live tracking streams. `PAUSED` is not final and still counts toward the assigned courier's
  workload.
- The new values are an additive change to the `parcel.status-changed` event (`schemaVersion` stays
  1). Consumers must tolerate statuses they do not know yet; they are rolled out before the backend
  can produce them.
- The database CHECK constraints are widened by a new `delivery` migration. This is one-way in
  practice: once rows use the new statuses the old constraints cannot be restored without fixing data.

### 1B. Courier moves depend on the leg (amendment, 2026-11-19)

For a parcel with legs ([ADR 0007](0007-multi-leg-shipments-and-hub-handover.md)) the courier moves of section 1
are narrowed by the type of the leg the courier holds (the open leg assigned to that courier). Every move must be
allowed by both the status table and the leg type:

| Leg type | Courier moves | Who else moves the parcel |
|---|---|---|
| `PICKUP` | `CREATED` to `PICKED_UP`, `PICKED_UP` to `HUB_ARRIVED` (ends the leg at the origin hub), `PICKED_UP` to `FAILED` | |
| `INTER_HUB` | none for new parcels | Dispatchers transfer (`IN_TRANSIT`) and receive (`HUB_ARRIVED`), ADR 0007 amendment. A leg that got a courier before that amendment can still be finished by that courier. |
| `FINAL_MILE` | `HUB_ARRIVED` or `HUB_SORTED` to `OUT_FOR_DELIVERY`, `OUT_FOR_DELIVERY` to `DELIVERED` or `FAILED` (proof and failure reason as in section 5) | |

- A courier who holds no open leg (for example the pickup courier once the parcel reached the hub) cannot move the
  parcel; the request is a `409`.
- `HUB_SORTED` is not set by any courier of a parcel with legs and does not gate the final-mile courier. Who records
  sorting is open.
- A parcel without legs keeps the single-courier rules of section 1 unchanged.

### 2. Terminal States, Cancellations, and Overrides

- `DELIVERED`, `FAILED` and `CANCELLED` are strictly terminal. Once a parcel reaches any of them, no
  further status transitions, cancellations, or edits can occur. (`CANCELLED` was added by section 1A.)
- There is no retry flow after failure, no parcel cancellation by a customer, and no manual status
  override by dispatchers or administrators. A merchant may hold or cancel its own parcel before
  pickup only (section 1A).
- If a delivery attempt fails, the parcel terminates as `FAILED`. Subsequent re-delivery attempts or
  follow-up shipments require creating a new parcel delivery request.

### 3. Courier Assignment and Reassignment

- Courier assignment is a separate property on `parcels` (`assigned_courier_id`), not a status, and
  does not emit a status-change event.
- Assignment and reassignment are permitted **only while the parcel is in `CREATED` status**.
- Only dispatchers/admins (`DISPATCHER_ADMIN` role via the `dispatch` module calling
  `delivery.api.ParcelAssignment`) may assign or reassign couriers.
- Attempting to assign or reassign a courier when the parcel is in any status other than `CREATED`
  is rejected with `409 Conflict`.
- Once `PICKED_UP` is reached, the assigned courier is immutable.

### 4. Failure Reason Codes (D-19)

- When transitioning to `FAILED`, a valid `failureReasonCode` is mandatory. Permitted values are
  strictly bounded by `com.swiftdrop.delivery.api.FailureReason`:
  - `RECIPIENT_UNAVAILABLE`: Recipient was not present at the drop-off location.
  - `ADDRESS_NOT_FOUND`: Drop-off address could not be located.
  - `REFUSED`: Recipient rejected acceptance of the parcel.
  - `DAMAGED`: Parcel package was damaged in transit and cannot be delivered.
  - `OTHER`: Unforeseen delivery impediment.
- The reason code is stored in `parcel_status_history` and included in the `parcel.status-changed`
  event payload.
- Any optional free-text operational notes are stored in `parcel_status_history` only and are
  strictly excluded from events, webhooks, and public tracking to protect personal data.

### 5. Delivery Proof Workflow and Failure Handling (D-12)

- Proof of delivery requires a non-blank `recipient_name` (personal data) and allows an optional
  photographic image (JPEG or PNG, $\le 2\text{ MB}$, stored as `BYTEA` in `delivery_proofs`).
- The submission uses a **two-step workflow**:
  1. The assigned courier uploads proof via `PUT /api/v1/parcels/{id}/proof` while the parcel is
     `OUT_FOR_DELIVERY`.
  2. The assigned courier transitions status via
     `POST /api/v1/parcels/{id}/status-changes` with `toStatus="DELIVERED"`.
- **Atomic validation guarantee:** The `DELIVERED` transition must verify the existence of valid proof
  for the same parcel within the exact database transaction that updates the status, increments the
  sequence, writes history, and appends the outbox event. Attempting `toStatus="DELIVERED"` without
  valid existing proof is rejected with `409 Conflict`.
- **Handling of proof on subsequent failure:** If proof was uploaded during `OUT_FOR_DELIVERY` but the
  courier subsequently transitions the parcel to `FAILED` (e.g. dispute before handover), the proof
  record is invalidated or retained strictly as an uncompleted attempt. A parcel in `FAILED` status
  is never marked as delivered, and its proof cannot be used to justify completion.
- **Privacy and protection:** Proof data (`recipient_name`, photo) is private operational data. It is
  readable only by the parcel owner (customer/merchant), the assigned courier, and dispatchers/admins
  via authenticated endpoints. It is **never** included in `parcel.status-changed` event payloads,
  webhooks, or public tracking endpoints.

### 6. Receipt Behavior (D-13)

- A receipt (`GET /api/v1/parcels/{id}/receipt`) is accessible to the owning customer and
  dispatchers/admins immediately after parcel creation as an Order/Booking Receipt.
- The receipt reflects the current parcel status:
  - **Before delivery (`CREATED` through `OUT_FOR_DELIVERY`):** Acts as booking confirmation,
    showing tracking number, creation timestamp, addresses, size, weight, fee breakdown, and current
    status (`deliveredAt` and completion proof remain `null`).
  - **Upon `DELIVERED`:** Automatically includes completion details (`deliveredAt`, recipient name).
  - **Upon `FAILED`:** Reflects that the delivery attempt was terminated as failed.
- Calling the receipt endpoint for a non-existent or non-owned parcel returns `404 Not Found`.

### 7. HTTP Error Mapping Conventions

Endpoints in the `delivery` module follow standard RFC 9457 Problem Details error mappings:

- **`400 Bad Request`**: Malformed JSON syntax, missing structurally required fields in the request
  body, or unrecognized enum constants (e.g. invalid status name or unsupported failure reason).
- **`404 Not Found`**: Returned when the parcel ID does not exist, **or** when the authenticated
  caller is not entitled to access or update the parcel. Specifically, if a courier who is not the
  currently assigned courier attempts to change status or upload proof, the API returns `404 Not Found`
  to prevent parcel existence probing and courier assignment enumeration.
- **`409 Conflict`**: Returned when an authenticated, authorized caller submits a structurally valid
  request that violates a domain lifecycle precondition, business rule, or optimistic concurrency check:
  - Transition not permitted by the state machine (e.g. `CREATED → DELIVERED` or `CREATED → IN_TRANSIT`).
  - Attempting any transition on a terminal parcel (`DELIVERED` or `FAILED`).
  - Transitioning to `PICKED_UP` without an assigned courier.
  - Transitioning to `FAILED` without a failure reason.
  - Transitioning to `DELIVERED` without previously captured delivery proof.
  - Attempting courier assignment or reassignment after `CREATED` status.
  - Concurrent update collision detected by aggregate `@Version`.

### 8. Transactional Guarantees and Events

- Every status transition executes in a single database transaction in `delivery`:
  1. The `Parcel` row is updated and locked via optimistic locking (`@Version`).
  2. A new entry is appended to `parcel_status_history` with the incremented sequence, actor ID, and
     timestamp.
  3. A new row is appended to `parcel_outbox_events` with the `parcel.status-changed` payload.
- Upon successful transaction commit, an in-process application event is published for immediate
  dispatch to the `tracking` module's Server-Sent Events (SSE) connections.
- The asynchronous outbox relay forwards the event to RabbitMQ for `notification` and `merchant`
  webhook consumers as defined in ADR 0004.

## Alternatives Considered

- **Allowing `FAILED` from `CREATED`:** Rejected. A parcel that has not yet been picked up cannot
  experience a delivery failure; it must either be reassigned or cancelled.
- **Allowing customer or merchant cancellations in v1:** Originally deferred, because cancelling a
  parcel already in a courier's hands adds complex cascades across dispatch and couriers. The
  2026-11-15 amendment (section 1A) allows the **merchant** to hold or cancel **before pickup**, where
  no courier work has started and no cascade exists. **Customer** cancellation, and cancellation after
  pickup, remain deferred to a later ADR.
- **Single atomic multipart delivery endpoint:** Combining proof upload and status transition into a
  single request (`POST /api/v1/parcels/{id}/delivered-with-proof`). Rejected in favor of uniform status
  transition JSON bodies across all lifecycle stages.
- **Returning `403 Forbidden` for non-assigned couriers:** Rejected. In accordance with project security
  rules, returning 403 leaks whether a parcel exists and whether it is assigned. `404 Not Found` ensures
  complete resource privacy.

## Consequences

- The state machine for parcels is deterministic, strictly forward-progressing, and resilient to
  concurrency issues.
- `delivery` maintains strict encapsulation over all lifecycle operations and tables.
- Couriers have clear, validated steps for completing deliveries and recording failures.
- Delivery proof and personal data remain private and decoupled from event publishing.
- Receipt generation is available immediately without blocking on delivery completion.

