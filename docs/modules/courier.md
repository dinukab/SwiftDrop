# Courier module

> Verified against branch `feature/dispatcher` at commit `fecb6e7` (includes PR #24 `feature/courier-new`),
> 2026-11-18. Backend `./mvnw clean verify` passes. With Docker: `DatabaseMigrationIT` and `DispatchPersistenceIT` pass, `CourierFlowIT` fails.
> The courier module is **owned by another team member**; this document only describes it. Do not change
> courier code without agreement. Statuses: see [implementation-status.md](implementation-status.md).

## 1. Responsibilities

A courier sees the parcels assigned to them, moves them through the status lifecycle and records proof of
delivery. Courier **profile data** (phone, vehicle, active flag, hubs) lives in the `dispatch` module; courier
**parcel operations** live in the `delivery` module.

## 2. Backend

| Area | Class | Notes |
|---|---|---|
| Profile (dispatch) | `CourierProfile`, `CourierProfileService`, `CourierProfileController` | Table `couriers`: `user_id`, `phone` (E.164), `vehicle_type` (BIKE, CAR, VAN), `active`, **`home_hub_id`, `current_hub_id`** (PR #24) |
| Parcel ops (delivery) | `CourierController` (`@PreAuthorize("hasRole('COURIER')")`) | Day list, status change, proof, hub arrival, final mile |
| | `CourierParcelService` | `today(courierId)` |
| | `CourierHubService` (PR #24, `@Lazy`) | `confirmHubArrival` |
| | `FinalMileService` (PR #24, `@Lazy`) | `pickupFromHub`, `completeDelivery` |
| Legs (delivery) | `ShipmentLeg`, `ShipmentLegRepository` (PR #24) | Table `shipment_legs`; **not used by any service yet** |
| Lifecycle | `ParcelLifecycle` | See section 5 |

A courier account is created by an admin (`POST /api/v1/staff-users`, role `COURIER`).

## 3. Frontend

`frontend/app/(courier)/courier/`: `page.tsx` (today's parcels, status updates), `tasks/page.tsx`,
`proof/page.tsx`. PR #24 changed **no frontend code**: the UI knows nothing of the hub statuses or the new
endpoints (its `ParcelStatus` type lacks `HUB_ARRIVED` and `HUB_SORTED`).

## 4. API endpoints

| Method and path | Role | Purpose |
|---|---|---|
| `GET /api/v1/courier/parcels` | COURIER | Own parcels for the day with `nextStatuses` |
| `POST /api/v1/parcels/{id}/status-changes` | COURIER | Move one step (404 if not the assigned courier) |
| `PUT /api/v1/parcels/{id}/proof` (multipart) | COURIER | Proof of delivery |
| `POST /api/v1/parcels/{id}/hub-arrival` `{hubId}` | COURIER | Courier confirms arrival at a hub; sets `HUB_ARRIVED` (PR #24) |
| `POST /api/v1/parcels/{id}/pickup-for-delivery` | COURIER | `OUT_FOR_DELIVERY` from the hub (PR #24) |
| `POST /api/v1/parcels/{id}/complete-delivery` | COURIER | `DELIVERED`; the service passes `proofCaptured=true` (PR #24) |

Profile endpoints (`/api/v1/couriers`) are in [dispatcher.md](dispatcher.md); they do **not** expose the hub fields.

## 5. Parcel lifecycle after PR #24

| From | Courier may move to |
|---|---|
| CREATED | PICKED_UP |
| PICKED_UP | HUB_ARRIVED, FAILED (no longer IN_TRANSIT) |
| HUB_ARRIVED | HUB_SORTED, OUT_FOR_DELIVERY, FAILED |
| HUB_SORTED | IN_TRANSIT, OUT_FOR_DELIVERY, FAILED |
| IN_TRANSIT | HUB_ARRIVED, FAILED (no longer OUT_FOR_DELIVERY) |
| OUT_FOR_DELIVERY | DELIVERED, FAILED |

Same-province path: CREATED, PICKED_UP, HUB_ARRIVED, OUT_FOR_DELIVERY, DELIVERED. Cross-province path:
CREATED, PICKED_UP, HUB_ARRIVED, HUB_SORTED, IN_TRANSIT, HUB_ARRIVED, HUB_SORTED, OUT_FOR_DELIVERY, DELIVERED.
Final states have no exits; PAUSED and CANCELLED are merchant-only.

## 6. Hubs and availability

- `home_hub_id` and `current_hub_id` exist on the table and entity, with service methods
  `createProfile(..., homeHubId)`, `assignHomeHub` and `moveToHub` (which validate the hub through
  `HubService.requireHub`). **Nothing calls them**: no endpoint, no response field, no test.
- `moveToHub` changes the courier's current hub **without any arrival confirmation**, which the brief forbids.
- Availability / operational status: not implemented (only `active`).

## 7. Testing coverage

| Test | Tests |
|---|---|
| `delivery/web/CourierApiTest` | 10 (unchanged by PR #24: no test for the new endpoints) |
| `delivery/application/CourierParcelServiceTest` | 4 |
| `delivery/application/CourierFlowIT` | 4, **2 fail on PostgreSQL**: they still expect PICKED_UP to IN_TRANSIT to OUT_FOR_DELIVERY, which PR #24 removed (409 instead of 200); the test was not updated |
| `delivery/domain/ParcelLifecycleTest`, `ParcelTest` | updated for the new statuses |
| `CourierHubService`, `FinalMileService`, `ShipmentLeg` | **no tests** |
| Profile tests | see [dispatcher.md](dispatcher.md) |

## 8. Known issues (to raise with the courier owner)

0. **`CourierFlowIT` fails** (2 of 4 tests): the courier lifecycle change broke the courier owner's own end-to-end test. Run: `./mvnw test -Dtest=CourierFlowIT` with Docker.
1. **Database rejects the new statuses.** `ck_parcels_status` and the two `parcel_status_history` CHECK
   constraints (last changed in `V202611151000`) list only the eight old statuses. No migration adds
   `HUB_ARRIVED` or `HUB_SORTED`, so the first hub arrival fails on PostgreSQL with a constraint violation (a
   500). Unit tests do not see it because they never touch PostgreSQL. Confirmed by reading the SQL; not
   reproduced (Docker was off).
2. **A parcel still has one courier.** `Parcel.assignedCourierId` is set only while `CREATED` and every
   courier action requires `courierId == assignedCourierId`. `FinalMileService` therefore only works if the
   pickup courier is also the final-mile courier, so different couriers per leg are impossible.
3. **`ShipmentLeg` is dead code.** Nothing creates or reads legs. Its `assigned_courier_id` has a foreign key
   to `couriers(id)` (the profile ID) while everywhere else a courier is identified by **user ID**.
4. **Delivery depends on dispatch tables.** The `shipment_legs` migration has foreign keys to `hubs` and
   `couriers` (dispatch tables), against ADR 0003.
5. **Courier-driven hub arrival.** `confirmHubArrival` is done by the courier, ignores `hubId` (never validated or
   stored) and does not update the courier's current hub. The brief says the hub confirms arrival.
6. **No existence/ownership checks on hub arrival** beyond the assigned courier; `HUB_ARRIVED` is also reachable
   from `PICKED_UP` for a same-province drop, so the status does not say which hub.
7. `GET /api/v1/courier/parcels/history` is referenced by the tasks page but does not exist.
8. Frontend unaware of `HUB_ARRIVED` / `HUB_SORTED`; `Record<ParcelStatus, …>` style maps will miss them.

## 9. Integration requirements from the dispatcher

See [dispatcher-courier-integration.md](dispatcher-courier-integration.md).
