# Dispatcher module

> Verified against branch `DEV3` at commit `7636dbe`, 2026-11-17. Backend `./mvnw clean verify` passes.
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
| `GET /api/v1/unassigned-parcels?page=&size=` | DISPATCHER | Page of `{id, trackingNumber, status, source, size, weightGrams, merchantOrderReference, createdAt}`. No addresses or phones. |
| `POST /api/v1/parcel-assignments` `{parcelId, courierUserId}` | DISPATCHER | 200 `{parcelId, courierUserId}`. Dispatcher is the token subject. |
| `GET /api/v1/courier-workloads` | DISPATCHER | `[{courierUserId, name, vehicleType, activeParcels}]`, least busy first |
| `GET /api/v1/couriers?active=&page=&size=` | ADMIN | Page of profiles with name, email, phone |
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
- Courier profile endpoints are `ADMIN` only on `DEV3`, so a dispatcher cannot list or edit profiles.
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
| `DispatchPersistenceIT` | 4 | Needs Docker (`-Pintegration`); not run in this analysis |

Not covered: concurrent assignment of one parcel, a dispatcher from another hub, hub handover, multi-leg
assignment, frontend tests (the project has none).

## 9. Missing functionality (hub model)

None of the following exists in code (no "hub" appears in `backend/src/main` or `frontend/app`):

- Hub reference data (nine provincial hubs, Colombo = Main Hub) and dispatcher-to-hub binding.
- Hub-based filtering and backend enforcement of the dispatcher's hub.
- Pickup, inter-hub and final-mile assignments (shipment legs); assignment history.
- Hub arrival and handover confirmation.
- Courier availability and home/current hub (courier-owned data).
- Dispatcher UI: courier management, hub filter, history.

## 10. Known issues

1. **Workloads are not filtered by eligibility.** `DispatchService.workloads()` lists every *active profile*,
   including a courier whose account is disabled or lost the `COURIER` role; assigning them then returns 409.
2. A dispatcher cannot read courier profiles (ADMIN only), so the UI shows names from the workload list only.
3. The 400 handler echoes `IllegalArgumentException` messages (for example the phone format text).
4. No test for two dispatchers assigning the same parcel at once.
5. Another dispatch implementation exists on branch `feature/dispatcher` (same endpoints, `DISPATCHER`-or-`ADMIN`
   on `/couriers`, eligibility-filtered workloads, atomic PATCH). It has not been merged; see section 11.

## 11. Remaining tasks

See [implementation-status.md](implementation-status.md) for the ordered list.
