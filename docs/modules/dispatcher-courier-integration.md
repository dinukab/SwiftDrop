# Dispatcher and courier integration

> Verified against branch `feature/dispatcher` at commit `9aad858` (code identical to `DEV3`), 2026-11-17. Sections marked **Planned** describe the
> target hub workflow and do not exist in code yet.

## 1. How the modules interact today

```
Dispatcher UI ──HTTP──> DispatchController ──> DispatchService
                                                 │  CourierEligibility ──> identity.api.UserDirectory
                                                 │                    └─> CourierProfileRepository (own table)
                                                 ├─> delivery.api.ParcelQueries.findUnassignedParcels
                                                 ├─> delivery.api.ParcelQueries.countActiveParcelsByCourier
                                                 └─> delivery.api.ParcelAssignment.assignCourier ──> parcels.assigned_courier_id
Courier UI ──HTTP──> delivery CourierController ──> parcels where assigned_courier_id = caller
```

The link between the two is one column, `parcels.assigned_courier_id`. Dispatch writes it (through
`delivery.api`); the courier endpoints read it. Dispatch never touches delivery tables.

## 2. Assignment lifecycle (current)

`CREATED, unassigned` → dispatcher assigns → `CREATED, assigned` (reassignment allowed) → courier moves to
`PICKED_UP` → assignment is locked → `IN_TRANSIT` → `OUT_FOR_DELIVERY` → `DELIVERED` or `FAILED`.

## 3. Workload calculation

`countActiveParcelsByCourier()` counts parcels per assigned courier whose status is not final (DELIVERED,
FAILED, CANCELLED); couriers with none are absent and treated as 0. `workloads()` joins this with active
courier profiles and sorts by count, then name.

## 4. Status transitions and who may make them

| Transition | Actor |
|---|---|
| Assign or reassign courier (no status change) | Dispatcher, only while `CREATED` |
| CREATED → PICKED_UP → IN_TRANSIT → OUT_FOR_DELIVERY | Assigned courier |
| → DELIVERED (proof required), → FAILED (reason required) | Assigned courier |
| CREATED ↔ PAUSED, → CANCELLED | Merchant, before pickup only |

## 5. Authorization responsibilities

- Dispatch: `DISPATCHER` role on queue, assignment and workloads; `ADMIN` on profile management.
- Courier: `COURIER` role, and the parcel must be assigned to the caller.
- **Planned:** dispatcher hub scope checked in the backend from a stored dispatcher-hub binding, never from a
  hub ID sent by the browser.

## 6. Error handling

| Case | Result |
|---|---|
| Courier disabled, not a courier, no profile or inactive profile | 409, message names the rule |
| Parcel not found | 404 |
| Parcel past `CREATED` | 409 "A courier can only be assigned before pickup" |
| Wrong courier changes a status | 404 |

## 7. Planned hub workflow (not implemented)

Hubs are reference data: the nine provinces (Western, Central, Southern, Northern, Eastern, North Western,
North Central, Uva, Sabaragamuwa), with Colombo (Western) as Main Hub. They already exist as pricing zones
(`LK-WP` and so on); no hub concept exists in code.

- **Same province (Uva to Uva):** pickup assignment → pickup → arrival at Uva hub → final-mile assignment →
  delivery.
- **Cross province (Uva to Southern):** pickup (Uva dispatcher) → Uva hub → transport to Colombo → Colombo
  receives and sorts → transport to Southern hub → Southern hub receives → final-mile assignment (Southern
  dispatcher) → delivery.
- Each step is a **shipment leg** with its own courier and at most one active assignment.

### Gaps against the current code

| Needed | Today |
|---|---|
| Several assignments per parcel | One `assigned_courier_id`; assignment only while `CREATED` |
| Legs and assignment history | `ShipmentLeg` entity and `shipment_legs` table exist (courier PR #24) but nothing creates or reads them; no assignment history |
| Hub arrival / handover statuses | `HUB_ARRIVED`, `HUB_SORTED` added to the lifecycle, but the database CHECK constraints do not allow them yet (see `courier.md` known issue 1) |
| Dispatcher belongs to a hub | Done in dispatch Phase 1 (`dispatcher_hubs`, `GET /dispatchers/me`); not yet enforced |
| Parcel hub | `ParcelSummary` has no province; pickup/dropoff zone IDs are in `delivery` |
| Courier home hub, current hub | Columns, entity and service methods exist (PR #24) but no endpoint, no tests, and `moveToHub` needs no arrival confirmation |
| Courier availability | Not implemented (only `active`) |
| Different courier per leg | Impossible today: `FinalMileService` and `changeStatus` require `courierId == parcel.assignedCourierId`, set only while `CREATED` |
| Module boundary | Dispatch may not read pricing zones (`dispatch` depends only on identity and delivery) |

## 8. Integration dependencies and approvals needed

1. `delivery.api` must expose the pickup/dropoff province (or hub) of a parcel: change owned by the delivery
   owner.
2. A legs/assignment-history model and new statuses change ADR 0006 and the `delivery` schema: needs team
   approval.
3. Hub-to-zone mapping must use stable province **codes**, not the generated zone UUIDs.
4. Courier availability and hub fields change the courier owner's profile: needs their approval.

## 9. End-to-end test scenarios

Current (can be run today): customer books, dispatcher lists and assigns, courier sees the parcel, courier
picks up, re-assignment returns 409.

Planned: same-province journey with pickup and final-mile couriers; cross-province journey with three
couriers; dispatcher from another hub gets 404/403; two dispatchers assign one leg at once, one wins; courier
current hub changes only after the hub confirms arrival.

## 10. What the courier merge (PR #24) changed for this integration

Analysed 2026-11-18 at `fecb6e7`. Added by the courier owner: hub statuses and lifecycle moves, `ShipmentLeg`,
`CourierHubService`, `FinalMileService`, three courier endpoints, and `home_hub_id`/`current_hub_id` on couriers.
Issues that block the dispatcher's leg phases are listed in `courier.md` section 8; the main ones are the
database constraint gap, the single `assignedCourierId`, and the `shipment_legs` foreign keys to dispatch tables.
Decisions needed from the courier and delivery owners before dispatch Phase 5 or 6:

1. Who confirms a hub arrival: the courier (as built) or the receiving hub's dispatcher (as in the brief).
2. Whether `shipment_legs` stays in `delivery` (as built) or moves to `dispatch` (as agreed on 2026-11-17), and
   whether `assigned_courier_id` holds the courier user ID or the profile ID.
3. A migration adding the two statuses to the `parcels` and `parcel_status_history` CHECK constraints.
