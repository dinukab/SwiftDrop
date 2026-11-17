# Dispatcher and courier implementation status

> Verified against branch `DEV3` at commit `7636dbe`, 2026-11-17 by reading the code and running
> `./mvnw clean verify` (passes) and `tsc --noEmit` (0 errors). Docker integration tests (`*IT`) were not run.

Legend: COMPLETED, PARTIALLY IMPLEMENTED, NOT IMPLEMENTED, PLANNED, BLOCKED.

## Dispatcher module (owner: M7)

| Feature | Status | Evidence / note |
|---|---|---|
| Role-based access (DISPATCHER) | COMPLETED | `@PreAuthorize`, `DispatchApiTest` |
| Unassigned parcel queue | COMPLETED | `GET /unassigned-parcels`, no personal data |
| Courier eligibility | COMPLETED | `CourierEligibility`, 6 tests |
| Courier assignment | COMPLETED | `POST /parcel-assignments`, 409/404 mapping |
| Courier workload | PARTIALLY IMPLEMENTED | Lists inactive-account couriers too (known issue 1) |
| Courier profile API | PARTIALLY IMPLEMENTED | ADMIN only; dispatcher cannot manage profiles |
| Dispatcher UI: queue and assign | COMPLETED | `/dispatcher` |
| Dispatcher UI: courier management | NOT IMPLEMENTED | Planned in `PROGRESS.md` 7.5 |
| Concurrency test (same parcel, two dispatchers) | NOT IMPLEMENTED | Needs Docker integration profile |
| Assignment history | BLOCKED | Waits for a `delivery.api` history contract (`PROGRESS.md` 3.7) |
| Hub reference data (9 hubs, Colombo main) | PLANNED | Needs design decision (hub vs pricing zone) |
| Dispatcher-to-hub binding | PLANNED | |
| Hub-based parcel filtering and backend enforcement | BLOCKED | Needs parcel province in `delivery.api` |
| Pickup / inter-hub / final-mile assignments (legs) | BLOCKED | Needs multi-assignment model in `delivery` |
| Duplicate active assignment per leg prevention | PLANNED | |
| Hub arrival and handover confirmation | BLOCKED | Needs hub statuses in the lifecycle |

## Courier module (owner: another member; documentation only)

| Feature | Status | Evidence / note |
|---|---|---|
| Courier profile (phone, vehicle, active) | COMPLETED | `couriers` table, tests |
| Assigned parcel list | COMPLETED | `GET /courier/parcels`, `CourierApiTest` |
| Pickup and delivery status updates | COMPLETED | `ParcelLifecycle`, `ParcelStatusService` |
| Proof of delivery | COMPLETED | `PUT /parcels/{id}/proof` |
| Courier UI (day list, tasks, proof) | COMPLETED | `frontend/app/(courier)` |
| Courier history endpoint | NOT IMPLEMENTED | UI mentions `GET /courier/parcels/history`; backend has none |
| Availability / operational status | NOT IMPLEMENTED | Only `active` exists |
| Home hub / current hub | NOT IMPLEMENTED | |

## Open decisions before hub work

- [ ] Team decision on the hub model (the earlier decision was the simple one-courier model, see `PROGRESS.md`).
- [ ] Two dispatch implementations exist: `DEV3` (teammates) and `feature/dispatcher` (this developer). Choose
      one base before adding features.
- [ ] Courier owner approval for availability and hub fields.
- [ ] ADR for multi-leg assignment (amends ADR 0006).

## Recommended order of small tasks

1. Hub reference data and dispatcher-to-hub binding inside `dispatch` (province code based, no cross-module change).
2. Fix workloads to list only eligible couriers (small, independent).
3. `delivery.api`: expose parcel pickup/dropoff province (needs delivery owner approval).
4. Hub-filtered unassigned queue with backend hub check.
5. Hub-aware assignment validation and tests for another hub's parcels.
6. ADR and schema for shipment legs and assignment history.
7. Pickup, inter-hub and final-mile assignment on legs; duplicate-active-assignment guard.
8. Hub arrival/handover confirmation and courier current hub (with courier owner).
9. Dispatcher UI: hub filter, courier management, history.
