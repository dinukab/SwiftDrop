# 0007. Multi-leg shipments and hub handover

- Status: Proposed
- Date: 2026-11-18
- Amends: [0006](0006-delivery-lifecycle-and-transition-rules.md) (assignment rules, section 3)

## Context

SwiftDrop has nine provincial hubs; Colombo (Western Province) is the Main Hub. A parcel can pass through
several couriers: one picks it up and brings it to the origin hub, others move it between hubs, and a last one
delivers it. [ADR 0006](0006-delivery-lifecycle-and-transition-rules.md) assumed one courier per parcel,
assigned while the parcel is `CREATED` and fixed after pickup. The courier work already added hub statuses
(`HUB_ARRIVED`, `HUB_SORTED`) and a `shipment_legs` table in `delivery`, but nothing creates or uses legs, and a
second courier could never act on a parcel.

Decisions taken with the team (2026-11-18): the **courier** confirms hub arrival; legs **stay in `delivery`**;
a leg's courier is identified by **user ID**, like everywhere else.

## Decision

1. **A parcel has an ordered list of legs** (`shipment_legs`, owned by `delivery`). Leg types:
   `PICKUP` (sender to the origin hub), `INTER_HUB` (hub to hub) and `FINAL_MILE` (destination hub to the
   recipient). Each leg has a source hub, a destination hub, at most one courier, and a completion time.
   For `PICKUP` and `FINAL_MILE` the source and destination hub are the same hub.
2. **Routes** (planned by `dispatch`, which knows the hubs, and stored through `delivery.api`):
   - same province: `PICKUP`, `FINAL_MILE`;
   - different provinces: `PICKUP`, then `INTER_HUB` origin to Main Hub (unless the origin is the Main Hub),
     then `INTER_HUB` Main Hub to destination (unless the destination is the Main Hub), then `FINAL_MILE`.
   Legs are planned when the first (pickup) courier is assigned. A parcel whose pickup or dropoff province has no
   hub gets no legs and keeps the single-courier behaviour of ADR 0006.
3. **The courier who holds the parcel is `parcels.assigned_courier_id`.** Every courier check of ADR 0006 keeps
   working unchanged. Assigning a later leg hands the parcel over: it is allowed only while the parcel waits at a
   hub (`HUB_ARRIVED` or `HUB_SORTED`), only for the next leg, and only after the previous leg is complete. This
   replaces "assignment only while `CREATED`" for parcels with legs; `CREATED`-time (re)assignment of the pickup
   courier is unchanged.
4. **A leg is complete when its courier confirms arrival at the leg's destination hub**
   (`POST /api/v1/parcels/{id}/hub-arrival`, which must name that hub), or, for the final leg, when the parcel is
   `DELIVERED` or `FAILED`. For a parcel with legs, `HUB_ARRIVED` cannot be set through the generic status endpoint,
   so the hub is always recorded.
5. **Who assigns which leg:** the dispatcher of the leg's **source** hub (the origin hub for pickup and the first
   inter-hub leg, Colombo for the second, the destination hub for the final mile). `dispatch` checks hub scope and
   courier eligibility; `delivery` enforces the leg order and the parcel state under the parcel lock.
6. **History:** every leg assignment is recorded in `dispatch`'s assignment history with its leg sequence; there is
   at most one active assignment per parcel and leg.
7. `shipment_legs.assigned_courier_id` references `users`, not `couriers`.

## Alternatives considered

- *Legs in `dispatch`*: keeps hubs out of `delivery`, but the courier already built the table in `delivery` and
  lifecycle checks need the legs next to the parcel row lock. Rejected by the team.
- *A hub-side confirmation of arrival* (the receiving dispatcher confirms): closer to a real hub scan, but needs a
  new actor and endpoint; kept as a later option.
- *A separate "current courier" per leg checked everywhere*: every courier endpoint would change. Reusing
  `assigned_courier_id` as the current holder keeps the change small.

## Consequences

- Different couriers can handle pickup, hub transport and delivery; dispatchers of different hubs assign their own
  legs.
- `delivery` stores hub IDs it does not own (foreign keys to `hubs`), as built; `dispatch` plans the route.
- Between two legs the previous courier still holds the parcel until the next leg is assigned.
- Not covered: courier availability, courier current hub and home-hub checks, retries after `FAILED`, return
  legs.

## Amendment 2026-11-19: direct hub-to-hub transfers (no courier between hubs)

Owner decision. A courier is no longer assigned to an `INTER_HUB` leg. Only the pickup and the final mile have
couriers; between hubs the parcel is transferred by dispatchers.

- **Transfer and receipt are two facts.** The dispatcher of the leg's *source* hub transfers the parcel
  (`transferred_at/by` on `shipment_legs`); the dispatcher of the *destination* hub confirms physical receipt
  (`received_at/by`). Only the receipt sets `completed_at`, so the existing rule "the next leg needs the previous leg
  complete" keeps final-mile assignment closed until the receiving hub confirmed.
- **Statuses (no new status).** Transfer: `HUB_ARRIVED`/`HUB_SORTED` to `IN_TRANSIT`, no courier on the parcel
  (`assigned_courier_id` cleared, so the previous courier loses access). Receipt: `IN_TRANSIT` to `HUB_ARRIVED`. Both
  are recorded in the status history and the outbox with the **dispatcher** as actor.
- **No courier records for a transfer:** no `parcel_assignments` row, no eligibility or hub check, no workload effect,
  no courier current-hub change.
- **Destination.** Never supplied by the caller: it is the leg's planned next hub. Other hubs' dispatchers get 404.
- **Migration** `V202611191000`: the four leg columns and their checks; `ck_parcels_courier_after_created` also allows
  a missing courier in `HUB_ARRIVED`, `HUB_SORTED` and `IN_TRANSIT`; `DISPATCHER` is allowed as history actor.
- **Pickup arrival stays courier-confirmed** (owner decision), as does everything else about the pickup and the final
  mile.
- **Compatibility.** Same-province parcels and parcels without legs are unchanged. A hub-to-hub leg that already has a
  courier (created before this amendment) is not offered as a transfer and can still finish through the courier's
  hub-arrival confirmation; new courier assignments to hub-to-hub legs are refused (409).
