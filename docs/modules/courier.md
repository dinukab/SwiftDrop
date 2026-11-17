# Courier module

> Verified against branch `DEV3` at commit `7636dbe`, 2026-11-17. The courier module is **owned by another
> team member**; this document only describes it. Do not change courier code without agreement.
> Statuses: see [implementation-status.md](implementation-status.md).

## 1. Responsibilities

A courier sees the parcels assigned to them, moves them through the status lifecycle and records proof of
delivery. Courier **profile data** (phone, vehicle, active flag) lives in the `dispatch` module; courier
**parcel operations** live in the `delivery` module.

## 2. Backend

| Area | Class | Notes |
|---|---|---|
| Profile (dispatch) | `CourierProfile`, `CourierProfileService`, `CourierProfileController` | Table `couriers`: `user_id`, `phone` (E.164), `vehicle_type` (BIKE, CAR, VAN), `active` |
| Parcel ops (delivery) | `CourierController` (`@PreAuthorize("hasRole('COURIER')")`) | Day list, status change, proof |
| | `CourierParcelService` | `today(courierId)`: non-final parcels plus today's finished ones, with `proofCaptured` and next statuses |
| | `ParcelStatusService`, `DeliveryProofService` | Status moves and proof storage |
| Lifecycle | `ParcelLifecycle` | Courier moves: CREATED → PICKED_UP → IN_TRANSIT → OUT_FOR_DELIVERY → DELIVERED; FAILED from PICKED_UP, IN_TRANSIT or OUT_FOR_DELIVERY |

A courier account is created by an admin (`POST /api/v1/staff-users`, role `COURIER`); courier self
registration and approval were removed (migration `V202611131200`).

## 3. Frontend

`frontend/app/(courier)/courier/`: `page.tsx` (today's parcels, status updates), `tasks/page.tsx`,
`proof/page.tsx` (recipient name and photo). These are full pages, not placeholders.

## 4. API endpoints

| Method and path | Role | Purpose |
|---|---|---|
| `GET /api/v1/courier/parcels` | COURIER | Own parcels for the day, with pickup and dropoff contacts and `nextStatuses` |
| `POST /api/v1/parcels/{id}/status-changes` `{toStatus, failureReason?, note?}` | COURIER | Move a parcel one step; 404 if not the assigned courier |
| `PUT /api/v1/parcels/{id}/proof` (multipart `recipientName`, `photo`) | COURIER | Record proof of delivery |
| `GET /api/v1/parcels/{id}/proof/photo` | per delivery rules | Fetch the proof photo |

Profile endpoints (`/api/v1/couriers`) are listed in [dispatcher.md](dispatcher.md).

## 5. Rules enforced

- Only the **assigned** courier can change a parcel; anyone else gets 404 (no existence leak).
- `FAILED` needs a failure reason; `DELIVERED` needs proof (409 otherwise).
- Final states (DELIVERED, FAILED, CANCELLED) have no exits; PAUSED and CANCELLED are merchant-only.
- Every status change takes the next sequence number and writes history plus an outbox event.

## 6. Availability, home hub, current hub

**Not implemented.** `CourierProfile` has only `active` (an admin switch for assignment eligibility). There is no
availability or operational status, no home hub and no current hub.

## 7. Testing coverage

| Test | Tests |
|---|---|
| `delivery/web/CourierApiTest` | 10 (roles, validation, multipart, 404/409) |
| `delivery/application/CourierParcelServiceTest` | 4 |
| `delivery/application/CourierFlowIT` | 5 (Docker; not run in this analysis) |
| Profile tests | see [dispatcher.md](dispatcher.md) |

## 8. Missing functionality and known issues

- Availability/operational status, home hub, current hub (needed by the dispatcher, section 9).
- The courier tasks page mentions a history endpoint (`GET /api/v1/courier/parcels/history`) that does **not**
  exist in the backend.
- A courier holds one parcel assignment only; there is no notion of shipment legs.

## 9. Integration requirements from the dispatcher

Documented in [dispatcher-courier-integration.md](dispatcher-courier-integration.md): availability and hub fields
on the profile, a way for a courier to see legs assigned in different statuses, and hub-arrival confirmation.
Each needs the courier owner's approval before any change.
