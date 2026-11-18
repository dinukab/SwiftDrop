# Dispatcher and courier implementation status

> Verified against branch `feature/dispatcher` at commit `fecb6e7` (includes courier PR #24), 2026-11-18 by reading the code and
> running `./mvnw clean verify` (passes) and, with Docker, `DatabaseMigrationIT` and `DispatchPersistenceIT` (pass, 26 migrations on PostgreSQL). `CourierFlowIT` fails (see `courier.md` section 8).

Legend: COMPLETED, PARTIALLY IMPLEMENTED, NOT IMPLEMENTED, PLANNED, BLOCKED.

## Dispatcher module (owner: M7)

| Feature | Status | Evidence / note |
|---|---|---|
| Role-based access (DISPATCHER) | COMPLETED | `@PreAuthorize`, `DispatchApiTest` |
| Unassigned parcel queue | COMPLETED | `GET /unassigned-parcels`, no personal data |
| Courier eligibility | COMPLETED | `CourierEligibility`, 6 tests |
| Courier assignment | COMPLETED | `POST /parcel-assignments`, 409/404 mapping |
| Courier workload | COMPLETED | Phase 2: only eligible couriers (enabled, COURIER role, active profile), same rule as assignment; `DispatchServiceTest`, `CourierEligibilityTest` |
| Courier profile API | PARTIALLY IMPLEMENTED | ADMIN only; dispatcher cannot manage profiles |
| Dispatcher UI: queue and assign | COMPLETED | `/dispatcher` |
| Dispatcher UI: courier management | NOT IMPLEMENTED (by design) | Courier profile endpoints are ADMIN only (`rbac-authorization.md`), so a dispatcher tab would only show 403. Courier setup belongs in the admin portal ("Onboard Staff") |
| Dispatcher UI: hub header, no-hub state, route column, assignment history | PARTIALLY IMPLEMENTED | Phase 8: `HubBanner`, `AssignmentHistoryDialog`, province route per row; lint, `tsc` and `next build` pass; not yet checked in a browser (desktop or 360 px) |
| Admin UI: bind a dispatcher to a hub | PARTIALLY IMPLEMENTED | `/admin/dispatchers` (nav: "Dispatcher Hubs") lists dispatcher accounts, shows each hub, assigns or moves it; new `GET /api/v1/dispatcher-hubs` (ADMIN); backend tests pass; lint, `tsc`, `next build` pass; not yet checked in a browser |
| Concurrency test (same parcel, two dispatchers) | COMPLETED | `DispatchHubScopeIT` on PostgreSQL (repeated runs stable); it found and fixed a spurious optimistic-lock failure |
| Assignment history | COMPLETED (pickup leg) | Phase 5: table `parcel_assignments`, `GET /api/v1/parcels/{id}/assignment-history`; unit, API and PostgreSQL tests. Parcels assigned before the migration have no entries. Unblocks `PROGRESS.md` 7.4 |
| Hub reference data (9 hubs, Colombo main) | PARTIALLY IMPLEMENTED | Phase 1: migration `V202611171000`, `Hub`, `HubService`, `GET /api/v1/hubs`; unit and API tests pass. Migration and seed not yet run on PostgreSQL (Docker was unavailable) |
| Dispatcher-to-hub binding | PARTIALLY IMPLEMENTED | Phase 1: `dispatcher_hubs`, `PUT /dispatchers/{id}/hub` (ADMIN), `GET /dispatchers/me`; not yet used to restrict the queue or assignment (Phase 4) |
| Hub-based parcel filtering and backend enforcement | COMPLETED (pickup queue and assignment) | Phase 4: queue and assignment are scoped to the dispatcher's hub from the server; another hub's parcel gives 404; no hub gives 409. `DispatchServiceTest`, `DispatchApiTest`, and `DispatchHubScopeIT` on PostgreSQL. Courier home hub is not enforced (no endpoint sets it) and workloads are not hub-scoped |
| Pickup / inter-hub / final-mile assignments (legs) | BLOCKED | Needs multi-assignment model in `delivery` |
| Duplicate active assignment per leg prevention | COMPLETED (pickup leg) | Partial unique index `uq_parcel_assignments_active` plus ending the previous row on reassignment; tested on PostgreSQL |
| Hub arrival and handover confirmation | BLOCKED | Courier-driven arrival exists (PR #24) but hub-side confirmation is undecided; needs the status CHECK-constraint migration and owner decisions (`dispatcher-courier-integration.md` section 10) |

## Courier module (owner: another member; documentation only)

| Feature | Status | Evidence / note |
|---|---|---|
| Courier profile (phone, vehicle, active) | COMPLETED | `couriers` table, tests |
| Assigned parcel list | COMPLETED | `GET /courier/parcels`, `CourierApiTest` |
| Pickup and delivery status updates | COMPLETED | `ParcelLifecycle`, `ParcelStatusService` |
| Proof of delivery | COMPLETED | `PUT /parcels/{id}/proof` |
| Courier UI (day list, tasks, proof) | COMPLETED | `frontend/app/(courier)` |
| Courier history endpoint | NOT IMPLEMENTED | UI mentions `GET /courier/parcels/history`; backend has none |
| Hub statuses `HUB_ARRIVED`, `HUB_SORTED` in the lifecycle | COMPLETED | Lifecycle, unit tests, migration `V202611171300` (CHECK constraints), `CourierFlowIT` on PostgreSQL |
| Hub arrival and final-mile endpoints | PARTIALLY IMPLEMENTED | Work and are covered by `CourierFlowIT`; now record history and outbox events and require proof. Arrival is still courier-driven and ignores `hubId`; no MockMvc tests |
| Shipment legs (`ShipmentLeg`, `shipment_legs`) | NOT IMPLEMENTED | Entity and table only; nothing creates or reads legs |
| Availability / operational status | NOT IMPLEMENTED | Only `active` exists |
| Home hub / current hub | PARTIALLY IMPLEMENTED | Columns, entity, service methods; no endpoint, no tests, no arrival confirmation |

## Open decisions before hub work

- [x] Hub model adopted (2026-11-17, replaces the earlier simple-model decision; record in `PROGRESS.md`).
- [x] Base branch: `feature/dispatcher` at `9aad858`, whose backend and frontend code equals `DEV3`.
- [x] Hub-to-zone link by province code through `delivery.api` (needs the delivery owner's approval before Phase 3).
- [x] Legs and assignment history in dispatch tables (needs an ADR amending ADR 0006 before the leg phase).
- [x] Migration for the two new statuses (done 2026-11-18).
- [ ] Courier owner: who confirms hub arrival; user ID vs profile ID in legs; whether `shipment_legs` stays in `delivery` (see `dispatcher-courier-integration.md` section 10).
- [ ] ADR for multi-leg assignment (amends ADR 0006).

## Recommended order of small tasks

1. ~~Hub reference data and dispatcher-to-hub binding~~ (done in code, Phase 1; PostgreSQL verification pending).
2. ~~Fix workloads to list only eligible couriers~~ (done, Phase 2).
3. ~~`delivery.api`: expose parcel pickup/dropoff province~~ (done, Phase 3).
4. ~~Hub-filtered unassigned queue with backend hub check~~ (done, Phase 4).
5. ~~Hub-aware assignment validation and tests for another hub's parcels~~ (done, Phase 4).
6. ~~Assignment history and duplicate-active guard~~ (done, Phase 5). Still open: ADR and schema for shipment legs.
7. Pickup, inter-hub and final-mile assignment on legs; duplicate-active-assignment guard.
8. Hub arrival/handover confirmation and courier current hub (with courier owner).
9. Dispatcher UI: hub header, route column and history done (Phase 8, first part). Still open: admin screen to bind a dispatcher to a hub, leg-aware assignment, browser check.
