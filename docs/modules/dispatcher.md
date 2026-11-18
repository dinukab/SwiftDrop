# Dispatcher module

> Verified against branch `feature/dispatcher` at commit `9aad858` (code identical to `DEV3`), 2026-11-17. Backend `./mvnw clean verify` passes.
> Statuses: see [implementation-status.md](implementation-status.md).

## 1. Responsibilities

The dispatcher decides **which courier takes which parcel**. In the target workflow (SwiftDrop has nine
provincial hubs, Colombo being the Main Hub) a dispatcher belongs to one hub and manages pickup,
inter-hub and final-mile assignments for that hub only.

**What exists today is the single-assignment model**: one courier per parcel, assigned while the parcel is
`CREATED`. The hub-based model is **planned, not implemented** (section 9).

## 2. Backend (module `dispatch`)

| Layer | Class | Purpose |
|---|---|---|
| web | `DispatchController` | Unassigned queue, assignment, workloads (`DISPATCHER` only) |
| web | `CourierProfileController` | Courier profile CRUD (`ADMIN` only, see section 6) |
| web | `DispatchExceptionHandler` | Maps dispatch and `delivery.api` exceptions to RFC 9457 problems |
| application | `DispatchService` | `unassigned`, `assign`, `workloads` |
| application | `CourierEligibility` | Enabled account + `COURIER` role + active profile |
| application | `CourierProfileService` | Create, list, get, update, activate, deactivate profiles |
| domain | `CourierProfile`, `CourierProfileRepository`, `VehicleType` | Table `couriers` |
| domain | `Hub`, `HubRepository`, `DispatcherHub`, `DispatcherHubRepository` | Tables `hubs` (9 seeded rows, one Main Hub) and `dispatcher_hubs` |
| application | `HubService` | List hubs, find a dispatcher's hub, bind or move a dispatcher (enabled `DISPATCHER` only) |
| web | `HubController` | Hub list, own hub, admin binding |

Dispatch may depend only on `identity.api`, `delivery.api` and `shared` (enforced by `ModuleBoundaryTest`).
It reaches parcels through `delivery.api.ParcelQueries` and `ParcelAssignment`.

## 3. Frontend

`frontend/app/(dispatch)/dispatcher/page.tsx` (one page, guarded by the `DISPATCHER` role in the layout) and
`frontend/lib/dispatch.ts`:

- Queue of unassigned parcels (paged, oldest first) with a refresh button.
- Assign dialog that lists couriers with their active parcel count.
- Success notice after assignment.
- **No courier-management tab**, no hub filter, no assignment history.

## 4. API endpoints

| Method and path | Role | Result |
|---|---|---|
| `GET /api/v1/unassigned-parcels?page=&size=` | DISPATCHER | Page of `{id, trackingNumber, status, source, size, weightGrams, merchantOrderReference, createdAt, pickupProvinceCode, dropoffProvinceCode}`. No addresses or phones. |
| `POST /api/v1/parcel-assignments` `{parcelId, courierUserId}` | DISPATCHER | 200 `{parcelId, courierUserId}`. Dispatcher is the token subject. |
| `GET /api/v1/courier-workloads` | DISPATCHER | `[{courierUserId, name, vehicleType, activeParcels}]`, least busy first |
| `GET /api/v1/couriers?active=&page=&size=` | ADMIN | Page of profiles with name, email, phone |
| `GET /api/v1/hubs` | DISPATCHER, ADMIN | Active hubs, Main Hub first: `[{code, name, main}]` |
| `GET /api/v1/dispatchers/me` | DISPATCHER | `{userId, hub}`; hub is `null` until an admin binds one. Taken from the token |
| `PUT /api/v1/dispatchers/{userId}/hub` `{hubCode}` | ADMIN | Binds or moves a dispatcher; 404 unknown user or hub, 409 not an enabled dispatcher |
| `POST /api/v1/couriers` | ADMIN | 201 |
| `PATCH /api/v1/couriers/{id}` | ADMIN | Partial update of phone, vehicle type, active |

Errors: 404 unknown parcel/profile/user; 409 ineligible courier, duplicate profile, parcel past `CREATED`;
400 invalid input.

## 5. Courier assignment workflow (current)

1. Customer or merchant creates a parcel (`CREATED`, no courier).
2. `GET /unassigned-parcels` lists it.
3. Dispatcher picks a courier from the workload list.
4. `DispatchService.assign` calls `CourierEligibility.requireEligible` (409 if not eligible), then
   `ParcelAssignment.assignCourier`, which locks the parcel row and sets `assignedCourierId`.
5. A second assignment after pickup returns 409 "A courier can only be assigned before pickup".
   Reassigning while still `CREATED` is allowed.

Merchant-paused or cancelled parcels are not `CREATED`, so they never appear in the queue.

## 6. Authentication and authorization

- JWT bearer token; roles `CUSTOMER, MERCHANT, COURIER, DISPATCHER, ADMIN` (`docs/rbac-authorization.md`).
- Assignment, queue and workloads use `@PreAuthorize("hasRole('DISPATCHER')")`; an `ADMIN` gets 403.
- Courier profile endpoints are `ADMIN` only, so a dispatcher cannot list or edit profiles.
- **No hub authorization exists.** Any dispatcher sees every unassigned parcel and may assign any courier.

## 7. Integration

- **Delivery:** `ParcelQueries.findUnassignedParcels`, `countActiveParcelsByCourier`,
  `ParcelAssignment.assignCourier`. `ParcelSummary` exposes no zone/province, so it cannot be filtered by hub.
- **Identity:** `UserDirectory.findUser` for name, enabled flag and roles.
- **Courier:** see [dispatcher-courier-integration.md](dispatcher-courier-integration.md).

## 8. Testing coverage (about 50 tests)

| Test class | Tests | Covers |
|---|---|---|
| `DispatchServiceTest` | 6 | Assign, ineligible courier, workload sorting |
| `CourierEligibilityTest` | 6 | Each eligibility rule |
| `CourierProfileServiceTest` | 13 | Create, list, update, activate, duplicates |
| `CourierProfileTest` | 4 | Domain rules (phone) |
| `DispatchApiTest` | 8 | Roles, 400/404/409 mapping, dispatcher from token |
| `CourierProfileApiTest` | 9 | Profile API roles and errors |
| `DispatchPersistenceIT` | 6 | Real PostgreSQL via Testcontainers: courier constraints, hub seed (9 hubs, one main), second-main-hub rejection, dispatcher binding. Passes (2026-11-18) |

Not covered: concurrent assignment of one parcel, a dispatcher from another hub, hub handover, multi-leg
assignment, frontend tests (the project has none).

## 9. Missing functionality (hub model)

None of the following exists in code (no "hub" appears in `backend/src/main` or `frontend/app`):

- ~~Hub reference data and dispatcher-to-hub binding~~: added in Phase 1 (section 12); still not enforced anywhere.
- Hub-based filtering and backend enforcement of the dispatcher's hub.
- Pickup, inter-hub and final-mile assignments (shipment legs); assignment history.
- Hub arrival and handover confirmation.
- Courier availability and home/current hub (courier-owned data).
- Dispatcher UI: courier management, hub filter, history.

## 10. Known issues

1. ~~Workloads were not filtered by eligibility~~: fixed in Phase 2 (`CourierEligibility.isEligible`).
2. A dispatcher cannot read courier profiles (ADMIN only), so the UI shows names from the workload list only.
3. The 400 handler echoes `IllegalArgumentException` messages (for example the phone format text).
4. No test for two dispatchers assigning the same parcel at once.
5. The earlier parallel dispatch implementation on `feature/dispatcher` was merged away (PR #18 and #22): the
   code on this branch is the `DEV3` version. Its small improvements (dispatcher-or-admin on `/couriers`, atomic
   PATCH, fixed 400 text) are not in the code.

## 11. Remaining tasks

See [implementation-status.md](implementation-status.md) for the ordered list.

## 12. Phase 1 notes (hub reference data and dispatcher binding)

- Migration `V202611171000__dispatch_create_hubs.sql` creates `hubs` (code = province code such as `LK-UP`, a unique
  partial index allows a single Main Hub) and `dispatcher_hubs` (one row per dispatcher user), and seeds the nine
  hubs. Codes, not zone UUIDs, so dispatch needs no access to pricing tables.
- `HubService` and `HubController` are `@Lazy`, the same pattern as `DispatchController`: about 25 existing
  `@SpringBootTest` classes exclude JPA and mock repositories, so an eager bean needing the new repositories would
  break all of them. If a later phase makes `DispatchService` depend on `HubService`, keep that dependency lazy or
  add the repository mocks to those tests.
- Tests: `HubServiceTest` (10), `HubApiTest` (8), seed and constraint tests in `DispatchPersistenceIT` (3, need
  Docker, not yet run), updated `MigrationConventionTest` and `DatabaseMigrationIT` (23 migrations).
- Known gap: nothing enforces the binding yet; the queue and assignment still ignore the hub (Phase 4).

## 13. Phase 2 notes (eligible-only workloads)

`DispatchService.workloads()` now drops couriers whose account is disabled, who lack the `COURIER` role or whose
user no longer exists, using the shared rule `CourierEligibility.isEligible(user, profile)`; `requireEligible`
(used by assignment) has the same conditions. Tests: `DispatchServiceTest` (7), `CourierEligibilityTest` (7).
No API or schema change. The courier PR #24 (hub statuses, `ShipmentLeg`, courier hub fields) was analysed at
the same time; see [courier.md](courier.md) section 8 for the issues found.

## 14. Phase 3 notes (parcel province in `delivery.api`)

- **New contract `pricing.api.ZoneDirectory`** (`findZoneIdByCode`, `findCodes`), implemented by
  `pricing.application.ZoneDirectoryService`. Province codes (`LK-UP`, ...) are stable; zone UUIDs are generated per
  environment. Delivery may depend on `pricing.api` (ADR 0003 graph), so `dispatch` still needs nothing from pricing.
- **`delivery.api.ParcelSummary`** gains `pickupProvinceCode` and `dropoffProvinceCode` (null if the zone is gone).
  The old 11-argument constructor is kept, so existing callers compile unchanged.
- **`ParcelQueries.findUnassignedParcels(page, pickupProvinceCode)`**: only CREATED parcels without a courier whose
  pickup zone has that code; null or blank means no filter; an unknown code gives an empty page. The single-argument
  method is unchanged. Province codes for a page are looked up in one query.
- **Dispatch:** `UnassignedParcelResponse` shows both codes (a province is not personal data). `DispatchService` does
  not use the filter yet; Phase 4 will pass the dispatcher's hub code.
- Tests: `ParcelQueryServiceTest` (+5), `ZoneDirectoryServiceTest` (4), `DispatchApiTest` (+1), and
  `DeliveryQueriesIT.unassignedQueryCanBeFilteredByPickupProvinceAndShowsBothProvinces` on PostgreSQL.
- Approval: this changes `delivery.api` and `ParcelQueryService`, which belong to the delivery owner; it was made on
  the instruction to continue with Phase 3 and should be mentioned in the PR.
