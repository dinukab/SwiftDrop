# Stage 1: Parcel status event contract

Status: Draft for delivery, merchant and notification owner review.
Prepared: 2026-11-13. No teammate approval is recorded by this document.

This is the handoff for the messaging and webhook workstream. It does not implement messaging,
change module APIs or supersede an ADR. Accepted decisions below come from
[ADR 0003](adr/0003-backend-modules-and-boundaries.md) and
[ADR 0004](adr/0004-messaging-and-integration-reliability.md).
The concrete field layout follows the proposed example in
[implementation-contract.md](implementation-contract.md#43-request-and-response-examples-proposed);
field names, null conventions and Java signatures still need owner review.

## 1. Responsibilities

Module ownership is already decided; assignments to individual people are not confirmed.
The member numbers in [work-plan.md](work-plan.md#1-team-allocation-members-110-are-placeholders)
are proposed placeholders.

| Deliverable | Owning module | Handoff |
|---|---|---|
| Event record and status/source types | delivery.api | Delivery owner reviews the contract used by consumers |
| Status updates, sequence, history and atomic outbox write | delivery | Delivery teammate creates the event in the business transaction |
| Outbox relay and event exchange | delivery infrastructure | Messaging contributor coordinates changes with the delivery owner |
| Notification queue, processing state and mock SMS | notification | Notification owner consumes customer events |
| Webhook queue, processing state, jobs, sender and attempts | merchant | Messaging/webhook contributor coordinates with the merchant owner |
| Endpoint registration, active endpoint lookup and encrypted secrets | merchant | Merchant teammate and webhook contributor agree internal interfaces |
| Current tracking snapshot and live updates | tracking | Separate SSE workstream, using delivery.api |

Delivery never calls merchant or notification. Those modules may import delivery.api only.
Webhook components within merchant use internal application/domain boundaries, not a new
cross-module dependency. Do not add business event types to shared.

The user's assigned workstream covers messaging and webhooks. Confirm with the team whether
customer notification implementation is included in that personal assignment.

## 2. Accepted transport and delivery rules

| Item | Accepted value |
|---|---|
| Event type and routing key | parcel.status-changed |
| Producer | delivery |
| Exchange | swiftdrop.events, durable topic, owned by delivery |
| Notification queue | notification.parcel-status-changed, owned by notification |
| Webhook queue | merchant.parcel-status-changed, owned by merchant |
| Serialization | JSON using Jackson 3; event record in delivery.api |
| Delivery guarantee | At least once; duplicate and out-of-order arrivals are possible |
| Persistence boundary | Status, history and outbox event commit in one PostgreSQL transaction |

AMQP properties: message-id equals eventId, type equals eventType, content-type is
application/json, timestamp equals occurredAt, delivery mode is persistent.
Both consumer queues bind using parcel.status-changed. Queue durability, dead-letter settings,
publisher confirms and mandatory publishing follow ADR 0004 sections 3 and 5.

Courier assignment is not a status change and emits no parcel.status-changed event.
CREATED is sequence 1; every later committed status change increments the parcel sequence.
Retries, reconciliation and replay preserve the original event ID, occurrence time and data.

## 3. Proposed version 1 JSON layout

The envelope fields and business data are required by ADR 0004 section 2.
The exact JSON keys, types for data fields and nullable-field conventions here are a
review proposal consistent with the existing implementation-contract example.

| Field | JSON type | Rule |
|---|---|---|
| eventId | string, UUID | Required; generated once for this committed event |
| eventType | string | Required; parcel.status-changed |
| schemaVersion | integer | Required; 1 |
| occurredAt | string, UTC ISO 8601 | Required; occurrence time, not publish or retry time |
| producer | string | Required; delivery |
| data | object | Required; fields below |

| data field | JSON type | Proposed rule |
|---|---|---|
| parcelId | string, UUID | Required; owning delivery parcel |
| trackingNumber | string | Required; treat as opaque, format remains delivery-owned |
| previousStatus | string or null | Null only for initial CREATED event |
| newStatus | string | Required; one of the accepted parcel statuses |
| sequence | integer | Required; positive, per parcel; CREATED is 1 |
| source | string | CUSTOMER or MERCHANT |
| ownerCustomerId | string, UUID or null | Required value for CUSTOMER; null for MERCHANT |
| ownerMerchantId | string, UUID or null | Required value for MERCHANT; null for CUSTOMER |
| merchantOrderReference | string or null | Merchant reference when present; null for customer parcels |
| failureReasonCode | string or null | Required value for FAILED; null otherwise |

Proposed v1 producer convention: include all listed keys, using explicit nulls where permitted.
Consumers must tolerate unknown optional fields. Missing mandatory fields, unsupported versions
and malformed payloads must not be interpreted as successful events; follow ADR 0004 section 6
for rejection and recovery. Missing nullable fields and their deserialization defaults need
confirmation during contract implementation.

Accepted statuses: CREATED, PICKED_UP, IN_TRANSIT, OUT_FOR_DELIVERY, DELIVERED, FAILED.
Consumers react to delivery's facts; they do not independently authorize lifecycle transitions.

No names, addresses, phone numbers, free-text notes, proof, credentials or secrets are permitted
in the event or webhook body. Merchant references must be opaque references without personal data.
Consumers fetch any necessary private information through the owning module's api.

### Example: merchant parcel picked up

All identifiers and references below are synthetic examples.

```json
{
  "eventId": "d2be4a96-939a-4e88-8f08-158830827502",
  "eventType": "parcel.status-changed",
  "schemaVersion": 1,
  "occurredAt": "2026-11-13T10:00:00Z",
  "producer": "delivery",
  "data": {
    "parcelId": "f41ace1b-9e9a-413e-9bc1-5d7c712aa931",
    "trackingNumber": "SD7K3M9Q2XWB",
    "previousStatus": "CREATED",
    "newStatus": "PICKED_UP",
    "sequence": 2,
    "source": "MERCHANT",
    "ownerCustomerId": null,
    "ownerMerchantId": "6a43d4a8-3e7b-4291-8c0d-31581ef23796",
    "merchantOrderReference": "ORD-DEMO-001",
    "failureReasonCode": null
  }
}
```

Additional review cases:

- Creation: previousStatus null, newStatus CREATED, sequence 1.
- Customer parcel: source CUSTOMER, ownerCustomerId populated, ownerMerchantId and
  merchantOrderReference null.
- Failure: newStatus FAILED, failureReasonCode populated with a delivery-approved code.
  The allowed failure stages and code vocabulary remain open:
  [ADR 0006](adr/0006-delivery-lifecycle-and-transition-rules.md) is still Proposed.

## 4. Consumer and webhook handoff

Accepted behavior from ADR 0004:

- Notification processes customer-created parcels. Mock SMS is triggered only for PICKED_UP,
  OUT_FOR_DELIVERY, DELIVERED and FAILED. Other statuses still advance ordering state.
- Merchant processes merchant-created parcels and creates one webhook job per event and active
  endpoint of that merchant. Customer-created parcels create no webhook jobs.
- Each relevant consumer tracks processed event IDs, last applied sequence and pending events.
  Acknowledge after the database commit; hold gaps and reconcile from delivery history.
- Creating webhook jobs is the consumer effect. HTTP sending happens separately.
- Webhook body is the event envelope and data. Stored body and event ID stay unchanged on retries;
  each attempt has a fresh signing timestamp. Receivers deduplicate and apply the highest
  sequence per parcel.

Webhook request headers are X-SwiftDrop-Event-Id, X-SwiftDrop-Event-Type,
X-SwiftDrop-Timestamp (Unix seconds of the attempt) and X-SwiftDrop-Signature.
The signature format is `v1=<hex HMAC-SHA256 of "<timestamp>.<raw body>">`.
Signing uses the endpoint secret; it is never included in the payload.

Retries are database-driven with bounded exponential backoff, per ADR 0004 section 7.
The proposal PDF's TTL retry queues are not the accepted webhook retry design.

### Interfaces to review before implementation

| Provider | Required capability | Current status |
|---|---|---|
| delivery.api | ParcelStatusChanged record and event data types | Existing ParcelStatusChanged and ParcelStatusChangedData records observed during Stage 2; owner agreement still pending |
| delivery.api | Read events for an inclusive parcel sequence range, retaining original IDs/data | Proposed ParcelEvents.eventsBetween(UUID parcelId, int fromSeq, int toSeq); review signature, range validation and missing-history behavior |
| merchant internal code | Find active endpoints belonging to ownerMerchantId | Exact internal interface and endpoint selection timing to agree |
| merchant internal code | Access endpoint signing secret through controlled decryption | Never expose decrypted secrets through public APIs, events or logs |
| identity.api / delivery.api | Minimal authorized lookup needed by notification | Exact read interfaces to agree with notification and owning module |

These are documentation proposals, not authorization to change another module's api package.
Endpoint count, endpoint/secret changes affecting queued jobs, encryption configuration names
and rotation remain implementation questions in ADR 0004. Do not invent a public merchant API
solely for communication between components inside merchant.

## 5. Team review and completion checklist

| Reviewer | Confirm | Status |
|---|---|---|
| Delivery owner | JSON keys, creation nulls, UUID IDs, sequence, immutable history reconstruction and event record ownership | Pending |
| Delivery owner | Failure code vocabulary and valid failure transitions, separately from this event layout | Pending |
| Merchant owner | Merchant ID/reference mapping, endpoint lookup, encrypted-secret access and endpoint selection timing | Pending |
| Notification owner | Customer-only policy, ordering for non-notifying statuses and private-data lookup | Pending |
| Messaging/webhook contributor | Queue bindings, deduplication, sequence handling and webhook payload compatibility | Pending |
| Team lead / relevant owners | Named contributors and whether customer notifications are in this personal assignment | Pending |

Stage 1 is ready for review when this document is available.
Stage 1 agreement is complete only when the relevant owners confirm the layout and handoffs;
record reviewer names, date and review reference here after that review actually occurs.

Implementation acceptance cases for the next stages:

1. Version 1 fixtures round-trip through Jackson 3, including creation and customer/merchant variants.
2. Failed events require a code and payloads exclude personal data.
3. Optional added fields do not break consumers.
4. Replayed events retain identity; duplicates do not create a second consumer effect.
5. Sequence gaps are held and recovered with original events.
6. Merchant parcels route only to that merchant's webhook jobs; customer parcels create none.

No Java records, migrations, queues, producers, consumers, dependencies or remote Git changes
are introduced by this Stage 1 document.
