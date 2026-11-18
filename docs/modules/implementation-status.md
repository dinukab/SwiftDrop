# Dispatcher and courier implementation status

> Re-verified 2026-11-18 on branch `feature/dispatcher` (Phases 0 to 9) by reading the code and running `./mvnw clean verify` (passes, 989 tests) and the PostgreSQL integration tests listed in the close-out section below.

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
| Dispatcher UI: hub header, no-hub state, route column, assignment history, waiting-legs queue and leg assignment | PARTIALLY IMPLEMENTED | Phase 8: `HubBanner`, `AssignmentHistoryDialog`, `WaitingLegsCard`, shared `AssignCourierDialog`, province route per row; lint, `tsc` and `next build` pass; not yet checked in a browser (desktop or 360 px) |
| Admin UI: bind a dispatcher to a hub | PARTIALLY IMPLEMENTED | `/admin/dispatchers` (nav: "Dispatcher Hubs") lists dispatcher accounts, shows each hub, assigns or moves it; new `GET /api/v1/dispatcher-hubs` (ADMIN); backend tests pass; lint, `tsc`, `next build` pass; not yet checked in a browser |
| Concurrency test (same parcel, two dispatchers) | COMPLETED | `DispatchHubScopeIT` on PostgreSQL (repeated runs stable); it found and fixed a spurious optimistic-lock failure |
| Assignment history | COMPLETED (every leg) | Phase 5: table `parcel_assignments`, `GET /api/v1/parcels/{id}/assignment-history`; unit, API and PostgreSQL tests. Parcels assigned before the migration have no entries. Unblocks `PROGRESS.md` 7.4 |
| Hub reference data (9 hubs, Colombo main) | COMPLETED | Phase 1: migration `V202611171000`, `Hub`, `HubService`, `GET /api/v1/hubs`; unit and API tests pass. |
| Dispatcher-to-hub binding | COMPLETED | Phase 1: `dispatcher_hubs`, `PUT /dispatchers/{id}/hub` (ADMIN), `GET /dispatchers/me`; enforced on the queue, assignment and legs (Phases 4 and 6) |
| Hub-based parcel filtering and backend enforcement | COMPLETED (pickup queue and assignment) | Phase 4: queue and assignment are scoped to the dispatcher's hub from the server; another hub's parcel gives 404; no hub gives 409. `DispatchServiceTest`, `DispatchApiTest`, and `DispatchHubScopeIT` on PostgreSQL. Phase 7 adds the courier hub check (a courier with a hub is only given work by that hub); workloads are not hub-scoped |
| Pickup / inter-hub / final-mile assignments (legs) | COMPLETED | Phase 6 (ADR 0007): legs planned at pickup assignment, `GET /waiting-legs`, `POST /leg-assignments`, hub-scoped by the leg's source hub; `MultiLegJourneyIT` runs Uva to Southern with four couriers on PostgreSQL |
| Duplicate active assignment per leg prevention | COMPLETED | Partial unique index `uq_parcel_assignments_active` plus ending the previous row on reassignment; tested on PostgreSQL |
| Hub arrival and handover confirmation | PARTIALLY IMPLEMENTED | Courier confirms arrival naming the hub (as decided); it must be the leg's destination, ends the leg, and unlocks the next one. Phase 7: the courier's current hub is updated after the arrival and a courier based at another hub is refused. Availability and a hub-side confirmation are not implemented |

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
| Hub arrival and final-mile endpoints | PARTIALLY IMPLEMENTED | Work and are covered by `CourierFlowIT`; now record history and outbox events and require proof. Arrival is courier-confirmed and now validates `hubId` against the leg (Phase 6); no MockMvc tests |
| Shipment legs (`ShipmentLeg`, `shipment_legs`) | COMPLETED | Created, assigned, completed and covered by tests (ADR 0007) |
| Availability / operational status | NOT IMPLEMENTED | Only `active` exists |
| Home hub / current hub | PARTIALLY IMPLEMENTED | Columns, entity, service methods; current hub is set after a confirmed arrival (Phase 7); no endpoint to show or set them |

## Open decisions before hub work

- [x] Hub model adopted (2026-11-17, replaces the earlier simple-model decision; record in `PROGRESS.md`).
- [x] Base branch: `feature/dispatcher` at `9aad858`, whose backend and frontend code equals `DEV3`.
- [x] Hub-to-zone link by province code through `delivery.api` (needs the delivery owner's approval before Phase 3).
- [x] Legs and assignment history in dispatch tables (needs an ADR amending ADR 0006 before the leg phase).
- [x] Migration for the two new statuses (done 2026-11-18).
- [x] Decided 2026-11-18: the courier confirms hub arrival; legs stay in `delivery`; a leg's courier is a user ID (ADR 0007, Proposed).
- [x] ADR for multi-leg assignment: `docs/adr/0007-multi-leg-shipments-and-hub-handover.md` (status Proposed, needs team review).

## Recommended order of small tasks

1. ~~Hub reference data and dispatcher-to-hub binding~~ (done in code, Phase 1; PostgreSQL verification pending).
2. ~~Fix workloads to list only eligible couriers~~ (done, Phase 2).
3. ~~`delivery.api`: expose parcel pickup/dropoff province~~ (done, Phase 3).
4. ~~Hub-filtered unassigned queue with backend hub check~~ (done, Phase 4).
5. ~~Hub-aware assignment validation and tests for another hub's parcels~~ (done, Phase 4).
6. ~~Assignment history and duplicate-active guard~~ (done, Phase 5); ~~ADR and schema for shipment legs~~ (done, Phase 6: ADR 0007).
7. ~~Pickup, inter-hub and final-mile assignment on legs~~ (done, Phase 6).
8. ~~Hub arrival/handover confirmation and courier current hub~~ (done, Phases 6 and 7; availability still open, needs the courier owner).
9. ~~Dispatcher UI: hub header, route column, history, admin hub screen, leg-aware assignment~~ (done, Phase 8). Still open: browser check at desktop and 360 px.

## Close-out (Phase 9, 2026-11-18)

- **Verified:** `./mvnw clean verify` passes (989 tests, 0 failures). On PostgreSQL: `MultiLegJourneyIT` (Uva to Southern with four couriers and three dispatchers, same-province, leg reassignment, no-legs parcel), `DispatchHubScopeIT`, `DispatchPersistenceIT`, `DatabaseMigrationIT`, `CourierFlowIT`. Frontend: `tsc`, eslint on the dispatch files and `next build` pass.
- **Known failing, not caused by this work:** `DeliveryQueriesIT` (timestamp precision) and `RabbitMessagingIT` (dead-letter timeout); `npm run lint` reports 3 errors in unrelated files; Maven failsafe cannot be resolved here (certificate error), so ITs run through surefire.
- **Open:** browser check of the dispatcher and admin pages; ADR 0007 is still Proposed and needs team review; courier availability (needs courier owner); endpoints or UI to show and set a courier's home or current hub; parcels created before multi-leg support keep the single-courier behaviour; retries after `FAILED` and return legs; frontend has no types for `HUB_ARRIVED`/`HUB_SORTED` (courier and customer UIs unaware).
- **Delivery-owned code changed (mention in the PR):** `ParcelStatusService`, `ParcelAssignmentService`, `CourierHubService`, `Parcel`, `ShipmentLeg`, new `ParcelLegs` API and two migrations.
