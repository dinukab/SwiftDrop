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
| Legs and assignment history | None |
| Hub arrival / handover statuses | Lifecycle has no hub states (ADR 0006) |
| Dispatcher belongs to a hub | No link between a dispatcher user and a province |
| Parcel hub | `ParcelSummary` has no zone; pickup/dropoff zone IDs are in `delivery` |
| Courier availability, home hub, current hub | `CourierProfile` has only `active` |
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
