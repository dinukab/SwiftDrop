# SwiftDrop progress tracker and implementation guide

This file has two jobs:

1. **Tracker**: who owns which part of SwiftDrop, and the status of every sub-part.
2. **Guide**: the steps to implement each sub-part, in order.

The team has 10 members (M1–M10). Each member owns one workstream, and each workstream is split
into small **sub-parts**. **Each sub-part is built on its own branch, committed separately and
merged through its own pull request.**

Scope and structure are inspired by a reference SwiftDrop implementation (`ShenSandaru/SwiftDrop`).
We use it only to see which features, endpoints and pages a complete system has. **Do not copy
its code.** Every member writes and can explain their own code (DEM-6). Requirements come from
[project-requirements.md](project-requirements.md). Contracts and endpoint paths come from
[implementation-contract.md](implementation-contract.md). The `SD-nnn` IDs refer to
[work-plan.md](work-plan.md).

- Last full review of statuses against the code: **2026-11-14**
- Members M1–M10 are placeholders. Replace them with names in the [summary](#2-summary) when
  agreed.

---

## 1. Rules: how every member works

### 1.1 Status legend

| Status | Meaning |
|---|---|
| ⬜ To do | Not started |
| 🟡 In progress | Branch exists, work ongoing (or partly present in the code) |
| 🔵 In review | Pull request open |
| ✅ Done | Merged into `dev`, checks green |
| ⛔ Blocked | Waiting on a decision or another sub-part (write the reason) |

### 1.2 Step-by-step process for **every** sub-part

1. **Pick** your next ⬜ sub-part whose dependencies are ✅. Set it to 🟡 and add today's date in
   this file, and commit that one-line change first.
2. **Sync and switch to your area branch.** The team already uses one long-lived branch per area
   (see [1.5 Branch map](#15-branch-map)). Do **not** create a new branch per sub-part:
   ```bash
   git switch feature/<your-area> && git pull
   git merge origin/DEV          # bring in other members' merged work; resolve conflicts here
   ```
   Every sub-part is a separate series of commits **tagged with its ID** (step 4). That tag is
   what keeps sub-parts separate inside a shared branch.
3. **Read before coding.** Read the relevant ADR, the contract section and the "How to implement"
   steps for your sub-part below.
4. **Implement in small commits.** Use one commit per layer, in this order where it applies:
   1. `feat(<module>): add <table> migration [<id>]`: Flyway migration in
      `db/migration/<module>/V<yyyyMMddHHmm>__<module>_<description>.sql`
   2. `feat(<module>): add <entity> domain model [<id>]`: entity, repository, domain rules
   3. `feat(<module>): add <use case> service [<id>]`: application service and exceptions
   4. `feat(<module>): add <endpoint> endpoint [<id>]`: controller, DTOs, exception handler
   5. `test(<module>): cover <behaviour> [<id>]`: unit and MockMvc tests (may be combined with
      the commit they test)
   6. `feat(frontend): add <page> page [<id>]`: UI work, when the sub-part has any
5. **Run the checks** (they must pass without Docker):
   ```powershell
   cd backend;  .\mvnw.cmd clean verify
   cd ../frontend; npm run lint; npm run build      # only if frontend changed
   git diff --check
   ```
6. **Update this file in the same branch.** Set the status to 🔵, fill in the PR number, and add a
   line to the [change log](#4-change-log).
   Commit: `docs(progress): update <id> status [<id>]`.
7. **Push your area branch.** Open a PR into `DEV` when one or more sub-parts are finished. List
   the sub-part IDs in the title, for example
   `feat(dispatch): unassigned parcels and assignment [7.2, 7.3]`. Don't wait for the whole
   area to be finished; merge after each finished sub-part (or small group) so branches don't
   drift apart.
8. **After review and merge into `DEV`,** set each merged sub-part to ✅, add the merge date, and
   update the [summary](#2-summary) counts.
9. **Releases:** the team lead merges `DEV` into `main` when the build is green. Nobody commits
   to `main` or `DEV` directly.

### 1.3 Pull request checklist

- [ ] Title lists the sub-part IDs, e.g. `[7.2]` or `[7.2, 7.3]`; commits are tagged the same way
- [ ] Only files of my module, or files agreed with their owner, are changed
- [ ] `mvnw clean verify` passes; `npm run lint` and `npm run build` pass if the frontend changed
- [ ] Tests added for the new behaviour, including authorization (401/403/404) where relevant
- [ ] No secrets, passwords or personal data in code, logs or error messages
- [ ] `docs/PROGRESS.md` row and change log updated
- [ ] `docs/architecture.md` status labels updated if something became **Implemented**

### 1.4 Rules for sharing a branch

- A branch can have several authors (for example `feature/courier-module`). Before you push,
  run `git pull --rebase` so you don't overwrite a teammate's commits. Never force-push a shared
  branch.
- Keep one sub-part per commit series. Don't mix two sub-parts in one commit, so
  `git log --grep "\[7.2\]"` shows exactly the work for 7.2.
- Edit `docs/PROGRESS.md` only on **your own member's rows** and the change log. This keeps merge
  conflicts in this file small. If a conflict happens, keep both sides' rows.

### 1.5 Branch map

Existing branches on `origin`, mapped to members. The "Branch" column in the workstream tables
uses these names. **Confirm the Member column with the team**: it is inferred from commit authors
and branch names, so it may be wrong.

| Branch | Area | Member | State on 2026-11-14 |
|---|---|---|---|
| `main` | stable / release | team lead | updated only from `DEV` |
| `DEV` | integration | team lead | all PRs merge here; CI must be green |
| `DEV_2` | second integration line | team lead | 19 commits ahead of `main`, has courier work merged in; decide whether it merges into `DEV` or is retired |
| `feature/rbac-user-management` | Identity, users, RBAC | M1 | active (12 commits) |
| `feature/authentication` | Login, sign-up, Google | M1 | no commits yet |
| `feature/delivery-request-pricing` | Booking and pricing | M2 / M8 | no commits yet |
| `feature/customer_history` | History and receipt | M2 | no commits yet |
| `feature/courier-module` | Courier operations | M6 | active (18 commits) |
| `feature/dispatcher` | Dispatch | M7 | active (2 commits) |
| `feature/merchant` | Merchant registration, API keys, orders | M4 / M5 | 1 commit |
| `feature/messaging-and-webhook` | RabbitMQ, notifications, webhook delivery | M9 / M5 | 1 commit |
| `feature/tracking-realtime` | SSE tracking | M10 | 1 commit |

In the workstream tables, leave the Branch column empty until you start a sub-part, then enter
your area branch from this map.

Areas without a branch yet (delivery core M3, containers and demo M10, pricing admin and
reports M8) get one of the same form, `feature/<area>`, created from the latest `DEV`.

### 1.6 Module boundaries (do not break them)

From [ADR 0003](adr/0003-backend-modules-and-boundaries.md). `ModuleBoundaryTest` enforces them.

```text
pricing      → (none)
identity     → (none)
delivery     → identity, pricing
dispatch     → identity, delivery
merchant     → identity, delivery
notification → delivery (events), identity
tracking     → delivery (events)
```

Other modules may be called **only through their `api` package**. Never read another module's
tables.

---

## 2. Summary

| # | Workstream | Owner | ✅ Done | Total | Progress |
|---|---|---|---|---|---|
| M1 | Identity, access control, shared foundations | M1 | 8 | 10 | 80% |
| M2 | Customer booking | M2 | 3 | 7 | 43% |
| M3 | Delivery core (parcel lifecycle) | M3 | 6 | 8 | 75% |
| M4 | Merchant registration, approval, dashboard, webhook endpoints | M4 | 0 | 7 | 0% |
| M5 | Merchant API keys, order intake, webhook delivery | M5 | 0 | 7 | 0% |
| M6 | Courier operations | M6 | 4 | 6 | 67% |
| M7 | Dispatch and quality lead | M7 | 1 | 8 | 13% |
| M8 | Pricing and reports | M8 | 3 | 7 | 43% |
| M9 | RabbitMQ events and notifications | M9 | 0 | 7 | 0% |
| M10 | Live tracking, containers, demo | M10 | 1 | 8 | 13% |
| | **Total** | | **26** | **75** | **35%** |

### 2.1 Build order (phases)

Follow the dependencies: a parcel must exist before it can be assigned, delivered or reported
on.

| Phase | Goal | Main sub-parts |
|---|---|---|
| 1 | Foundations | 1.1–1.8, 3.1–3.6, 8.1–8.3 *(mostly done)* |
| 2 | **Customer** can book and see parcels | 2.4, 2.5 |
| 3 | **Dispatcher** can assign couriers | 7.1–7.5, 3.7 |
| 4 | **Courier** can deliver | 6.5, 6.6 |
| 5 | Events and notifications | 9.1–9.7 |
| 6 | **Merchant** integration | 4.1–4.7, 5.1–5.7 |
| 7 | Admin completion: pricing admin, reports | 8.4–8.7 |
| 8 | Containers, demo and submission | 10.2–10.8, 7.7, 7.8 |

---

## 3. Workstreams and sub-parts

### M1 — Identity, access control, shared foundations

Owns `identity`, `config`, `shared`, `frontend/lib/api.ts`, `frontend/lib/auth-context.tsx`,
the auth pages, and `SecurityConfiguration`.

| ID | Sub-part | SD | Status | Branch | PR | Date |
|---|---|---|---|---|---|---|
| 1.1 | Shared problem-details error handler and pagination types | SD-002 | ✅ | | | |
| 1.2 | Bootstrap the first admin account at startup | SD-101 | ✅ | | | |
| 1.3 | Admin user management API and users page | SD-102 | ✅ | | | |
| 1.4 | `identity.api.UserDirectory` for other modules | SD-103 | ✅ | | | |
| 1.5 | Google sign-in handoff (one-time code exchange) | SD-104 | ✅ | | | |
| 1.6 | Frontend shell: API client, auth context, role guard | SD-008 | ✅ | | | |
| 1.7 | Login, sign-up and Google callback pages | SD-105 | ✅ | | | |
| 1.8 | Authorization test-matrix harness | SD-106 | ✅ | | | |
| 1.9 | Integration-test profile with real PostgreSQL **and RabbitMQ** | SD-003 | 🟡 | | | |
| 1.10 | *(Optional, needs ADR)* Split `DISPATCHER_ADMIN` into `ADMIN` and `DISPATCHER` | — | ⬜ | | | |

**How to implement the open sub-parts**

- **1.9:** `DatabaseMigrationIT` already runs against PostgreSQL under `-Pintegration`.
  1. Add a RabbitMQ Testcontainers base class next to it.
  2. Add a smoke test that publishes and consumes one message.
  3. Document `.\mvnw.cmd verify -Pintegration` in `backend/README.md`.
- **1.10:** The reference project made this split. It touches every module's security rules.
  1. Write a Proposed ADR first and get team approval.
  2. Add a migration that replaces the role.
  3. Update `SecurityConfiguration`, the authorization matrix and the frontend role guard.

### M2 — Customer booking

Owns `delivery.web` customer endpoints (shared with M3) and `frontend/app/(customer)`.

| ID | Sub-part | SD | Status | Branch | PR | Date |
|---|---|---|---|---|---|---|
| 2.1 | `POST /api/v1/parcels`: customer creates a delivery request | SD-206 | ✅ | | | |
| 2.2 | `GET /api/v1/parcels` and `/{id}`: history and detail | SD-207 | ✅ | | | |
| 2.3 | `GET /api/v1/parcels/{id}/receipt` | SD-208 | ✅ | | | |
| 2.4 | Booking UI with live fee estimate (`/customer/new`) | SD-209 | ⬜ | | | |
| 2.5 | History, parcel detail and printable receipt pages | SD-210 | ⬜ | | | |
| 2.6 | *(Optional)* Customer profile and saved addresses | — | ⬜ | | | |
| 2.7 | *(Optional)* Customer cancels a `CREATED` parcel | — | ⬜ | | | |

**How to implement**

- **2.4:**
  1. Read the Next.js docs in `frontend/node_modules/next/dist/docs/`.
  2. Build a form for pickup address, drop-off address and size/weight, validated on the client.
  3. On change, call `POST /api/v1/price-quotes` through `lib/api.ts` and show the fee.
  4. On confirm, call `POST /api/v1/parcels` and redirect to the parcel detail page. The server
     recalculates the fee; never trust the client's value.
- **2.5:**
  1. History page: a paged table from `GET /api/v1/parcels`.
  2. Detail page `customer/parcels/[id]`: status timeline from the detail response.
  3. Receipt page `customer/parcels/[id]/receipt`: a print-friendly layout (`window.print()`)
     built from the receipt endpoint.
- **2.6:**
  1. Agree the fields with M1 (the identity owner).
  2. Add a migration in `identity` for the addresses table.
  3. Add `GET/POST/PUT/DELETE /api/v1/users/me/addresses`.
  4. In the booking form, let the customer pick a saved address.
- **2.7:**
  1. Confirm the cancellation rules in [ADR 0006](adr/0006-delivery-lifecycle-and-transition-rules.md).
  2. Add `POST /api/v1/parcels/{id}/cancellation` (owner only, only in `CREATED`), which writes
     history and an outbox event.
  3. Add a "Cancel" button on the detail page.

### M3 — Delivery core (parcel lifecycle)

Owns the `delivery` module: domain, state machine, history, outbox, `delivery.api`.

| ID | Sub-part | SD | Status | Branch | PR | Date |
|---|---|---|---|---|---|---|
| 3.1 | Parcel aggregate and schema | SD-204 | ✅ | | | |
| 3.2 | Tracking-number generator | SD-205 | ✅ | | | |
| 3.3 | Status state machine, history and sequence | SD-301 | ✅ | | | |
| 3.4 | `delivery.api` assignment and query contracts | SD-302 | ✅ | | | |
| 3.5 | Outbox row written with every status change | SD-303 | ✅ | | | |
| 3.6 | Public tracking lookup `GET /api/v1/tracked-parcels/{n}` | SD-304 | ✅ | | | |
| 3.7 | Assignment history (who assigned which courier, when) in `delivery.api` | — | ⬜ | | | |
| 3.8 | `delivery.api.ParcelEvents` for consumer reconciliation | SD-505 | 🟡 | | | |

**How to implement**

- **3.7** (needed by M7's 7.4; inspired by the reference project's assignment-history table):
  1. Add a migration `delivery_add_parcel_assignment_history` with these columns: `parcel_id`,
     `courier_user_id`, `previous_courier_user_id`, `assigned_by`, `assigned_at`.
  2. Make `ParcelAssignmentService` write a row in the same transaction as the assignment.
  3. Add `ParcelQueries.findAssignmentHistory(parcelId)` that returns an API record.
  4. Unit test for reassignment; integration test for the query.
- **3.8:** The interface exists. Check that it has an implementation that rebuilds events from
  history, as ADR 0004 §4.3 requires. Add the missing implementation and tests with M9.

### M4 — Merchant registration, approval, dashboard, webhook endpoints

Owns the `merchant` module (with M5), `frontend/app/(merchant)` and `(admin)/admin/merchants`.

| ID | Sub-part | SD | Status | Branch | PR | Date |
|---|---|---|---|---|---|---|
| 4.1 | Merchant registration (`PENDING`): `POST /api/v1/merchants` | SD-401 | ⬜ | | | |
| 4.2 | Admin approval and rejection, with status history | SD-402 | ⬜ | | | |
| 4.3 | Admin merchants page (list, approve, reject) | SD-402 | ⬜ | | | |
| 4.4 | Merchant dashboard API `GET /api/v1/merchants/me/orders` | SD-406 | ⬜ | | | |
| 4.5 | Merchant sign-up page and dashboard UI | SD-407 | ⬜ | | | |
| 4.6 | Webhook endpoint registration with encrypted secret | SD-408 | ⬜ | | | |
| 4.7 | Webhook endpoints and delivery history page | SD-412 | ⬜ | | | |

**How to implement**

- **4.1:**
  1. Migrations: `merchant_create_merchants` (business name, contact, status, `user_id`) and
     `merchant_create_merchant_status_history`.
  2. Create the login user through `UserDirectory.createMerchantUser`.
  3. Return 201 with status `PENDING`; a duplicate email returns 409.
- **4.2:**
  1. Add `POST /api/v1/merchants/{id}/approval` and `.../rejection`, admin only.
  2. Write a status-history row with the actor.
  3. Only a `PENDING` merchant can change state; anything else returns 409.
- **4.3:** Build the `(admin)/admin/merchants` page: a paged table filtered by status, with
  approve/reject buttons and a reason dialog.
- **4.4:**
  1. Call `ParcelQueries.findParcelsByMerchant(merchantId, status, page)`.
  2. Only an approved merchant sees their own parcels; another merchant's parcels return 404.
- **4.5:**
  1. Add `/signup/merchant`.
  2. Add an approval gate component that shows "waiting for approval" until approved.
  3. Dashboard with status counts and an orders table.
- **4.6:**
  1. Read ADR 0004 §7.4 (secret encryption) and §7.3 (SSRF rules).
  2. Add the `merchant_webhook_endpoints` migration.
  3. Add `PUT /api/v1/merchants/me/webhook-endpoint`. Show the secret once, encrypt it at rest
     with a key from an environment variable, and allow only `https` URLs (validated with 5.4).
- **4.7:** Build the webhooks page: endpoint form, "show secret once" dialog, delivery history
  table and a redeliver button (endpoints from 5.6).

### M5 — Merchant API keys, order intake, webhook delivery

Owns `merchant` packages `apikey`, `orders`, `webhook.delivery`, and `tools/webhook-receiver`.

| ID | Sub-part | SD | Status | Branch | PR | Date |
|---|---|---|---|---|---|---|
| 5.1 | Merchant API keys: create (shown once), list, revoke | SD-403 | ⬜ | | | |
| 5.2 | API-key authentication in the security chain | SD-404 | ⬜ | | | |
| 5.3 | Order intake `POST /api/v1/merchant-orders` (safe to retry) | SD-405 | ⬜ | | | |
| 5.4 | SSRF-safe webhook HTTP client | SD-409 | ⬜ | | | |
| 5.5 | Merchant event consumer → webhook delivery records | SD-410 | ⬜ | | | |
| 5.6 | Webhook sender: signing, retries, backoff, manual redelivery | SD-411, SD-412 | ⬜ | | | |
| 5.7 | Demo webhook receiver and merchant integration guide | SD-413 | ⬜ | | | |

**How to implement**

- **5.1:**
  1. Add the `merchant_create_merchant_api_keys` migration.
  2. Generate a random key with a public prefix and store **only a hash**.
  3. Add `GET/POST /api/v1/merchants/me/api-keys` and `DELETE .../{id}`.
  4. Build the `merchant/api-keys` page.
- **5.2:**
  1. With M1, add a filter that reads `X-API-Key`, looks the key up by prefix and compares the
     hash in constant time.
  2. Grant a merchant-key authority used only on `/api/v1/merchant-orders/**`.
- **5.3:**
  1. Call `delivery.api.ParcelCreation` with source `MERCHANT`.
  2. Make the call idempotent on `merchantOrderReference`: a repeat call returns the same parcel.
  3. Add `GET /api/v1/merchant-orders/{ref}`.
- **5.4:**
  1. Resolve the host and reject private, loopback and link-local addresses.
  2. Disable redirects and set timeouts.
  3. Write tests for each rule in ADR 0004 §7.3.
- **5.5:**
  1. Add a RabbitMQ consumer of parcel status events (uses 9.1 and 9.3).
  2. Create one delivery record per endpoint per event, without duplicates.
- **5.6:**
  1. Sign each payload with HMAC-SHA256 and a timestamp header (ADR 0004 §7.2).
  2. Retry with exponential backoff and record every attempt.
  3. Add `POST .../webhook-deliveries/{id}/redeliveries`.
- **5.7:**
  1. Add `tools/webhook-receiver`, a small program that verifies the signature and prints events.
  2. Write `docs/merchant-integration-guide.md`. Use it for demo DEM-2.

### M6 — Courier operations

Owns `delivery.web` courier endpoints and `frontend/app/(courier)`.

| ID | Sub-part | SD | Status | Branch | PR | Date |
|---|---|---|---|---|---|---|
| 6.1 | `GET /api/v1/courier/parcels`: deliveries for the day | SD-309 | ✅ | | | |
| 6.2 | `POST /api/v1/parcels/{id}/status-changes` with note | SD-310 | ✅ | | | |
| 6.3 | Proof of delivery: recipient name and photo | SD-311 | ✅ | | | |
| 6.4 | Failed delivery with reason code | SD-312 | ✅ | | | |
| 6.5 | Courier mobile UI (list, detail, next status, proof, failure) | SD-313 | ⬜ | | | |
| 6.6 | Parcel ownership authorization suite (TST-2) | SD-316 | 🟡 | | | |

**How to implement**

- **6.5:**
  1. `courier` page: today's parcels as cards, usable at 360 px wide.
  2. `courier/parcels/[id]` page: contact details and a "next status" button that shows only
     legal transitions.
  3. Failure-reason picker.
  4. Proof form with `<input type="file" accept="image/*" capture="environment">`.
- **6.6:**
  1. Using M1's matrix harness, add rows for every parcel endpoint × {owner customer, other
     customer, assigned courier, other courier, admin, merchant, anonymous}.
  2. Every "not owned" case must return 404, including "courier B cannot update courier A's
     parcel".

### M7 — Dispatch and quality lead

Owns the `dispatch` module, `frontend/app/(dispatch)`, coverage configuration and the CI coverage
step. Also leads the security review, the test report and the contribution sheet.

| ID | Sub-part | SD | Status | Branch | PR | Date |
|---|---|---|---|---|---|---|
| 7.1 | Courier profiles: schema, service, admin API | SD-305 | 🟡 | `feature/dispatcher` | | 2026-11-11 |
| 7.2 | Unassigned parcels and courier assignment | SD-306 | ⬜ | | | |
| 7.3 | Courier workload view | SD-307 | ⬜ | | | |
| 7.4 | Assignment history per parcel (needs 3.7) | — | ⬜ | | | |
| 7.5 | Dispatcher UI | SD-308 | ⬜ | | | |
| 7.6 | JaCoCo code coverage in the build and CI | SD-004 | ✅ | | | |
| 7.7 | Security and personal-data review | SD-704 | ⬜ | | | |
| 7.8 | Test report, coverage summary and contribution sheet | SD-703, SD-908, SD-909 | ⬜ | | | |

**How to implement (detailed)**

The contracts dispatch needs **already exist**, so nothing outside the module blocks you:

| Need | Existing contract |
|---|---|
| Unassigned parcels | `delivery.api.ParcelQueries.findUnassignedParcels(PageRequest)` |
| Active parcels per courier | `delivery.api.ParcelQueries.countActiveParcelsByCourier()` |
| Record an assignment | `delivery.api.ParcelAssignment.assignCourier(parcelId, courierUserId, dispatcherUserId)` |
| Check a user | `identity.api.UserDirectory.findUser(id)` → `UserSummary` |

**Courier eligibility** (contract §5.4): the user is **enabled**, **and** has the `COURIER`
role, **and** has an **active** courier profile.

#### 7.1 Courier profiles (SD-305): in progress

Already present (commit `setup1`): migration `V202611112100__dispatch_create_couriers.sql`,
`CourierProfile`, `CourierProfileRepository`, `VehicleType`, `CourierProfileService`
(`createProfile`, `findByUserId`), three exceptions, and unit and persistence tests.

Remaining steps, one commit each:
1. **Service:**
   1. Add `listProfiles(PageRequest, Boolean active)`, `updateProfile(id, phone, vehicleType)`,
      `activate(id)` and `deactivate(id)`.
   2. Add a `CourierProfileNotFoundException`.
   3. Unit-test each method.
2. **DTOs** in `dispatch/web`: `CreateCourierProfileRequest` (`userId`, `phone`, `vehicleType`,
   with Bean Validation), `UpdateCourierProfileRequest`, `CourierProfileResponse`. The response
   includes the user's name from `UserDirectory`. Show the phone only to admins.
3. **Controller** `CourierProfileController`:
   - `GET /api/v1/couriers` (paged, `?active=`)
   - `POST /api/v1/couriers` → 201
   - `PATCH /api/v1/couriers/{id}` (phone, vehicle, active)
   - All admin only.
4. **Error mapping** `DispatchExceptionHandler`, using the shared problem-details format:
   - not found → 404
   - duplicate profile → 409
   - user not a courier or disabled → 409
   - validation → 400
5. **Security:** ask M1 to add `/api/v1/couriers/**` as admin only in `SecurityConfiguration`,
   or follow the existing method-security pattern.
6. **Tests:**
   1. MockMvc tests for 401 (anonymous), 403 (customer or courier), 201, 409 and 400.
   2. Add rows to the authorization matrix.
   3. Run `ModuleBoundaryTest` (dispatch may import only `identity.api` and `delivery.api`).
7. Update this file and open a PR: `feat(dispatch): courier profile admin API [7.1]`.

#### 7.2 Unassigned parcels and assignment (SD-306)

1. **Eligibility check:** add `CourierEligibility.requireEligible(courierUserId)` in
   `dispatch.application`. It throws `CourierNotEligibleException` unless the user is enabled,
   is a `COURIER` and has an active profile. Unit-test all four cases.
2. **Service** `DispatchService`:
   - `unassigned(page)` delegates to `ParcelQueries.findUnassignedParcels`.
   - `assign(parcelId, courierUserId, dispatcherUserId)` checks eligibility, then calls
     `ParcelAssignment.assignCourier`.
   - Write unit tests with mocked `delivery.api` and `identity.api`.
3. **Endpoints:**
   - `GET /api/v1/unassigned-parcels` (admin, paged)
   - `POST /api/v1/parcel-assignments` with body `{ parcelId, courierUserId }` → 200 or 201
   - The dispatcher ID comes from the JWT, never from the request body.
4. **Errors:**
   - ineligible courier → 409
   - parcel not in `CREATED` (`ParcelConflictException` from delivery) → 409
   - unknown parcel (`ParcelNotFoundException`) → 404
5. **Tests:**
   1. MockMvc for 401, 403, 404, 409 and success.
   2. Show that dispatch never touches delivery tables (`ModuleBoundaryTest`).
   3. *(Inspired by the reference project)* A concurrency test: two dispatchers assign the same
      parcel at once, and exactly one wins. Use the optimistic-lock `version` on the parcel
      (with M3).
6. Update this file, then open a PR: `feat(dispatch): unassigned parcels and assignment [7.2]`.

#### 7.3 Courier workload view (SD-307)

1. In `DispatchService.workloads()`:
   1. Load all **active** courier profiles.
   2. Join them with `ParcelQueries.countActiveParcelsByCourier()`.
   3. A courier missing from the map has **0**.
   4. Sort by count, lowest first, so the least busy courier comes first.
2. Endpoint `GET /api/v1/courier-workloads` (admin) returns
   `[{ courierUserId, name, vehicleType, activeParcels }]`.
3. Tests:
   1. Unit test with a courier at 0, one at 3 and an inactive courier that is excluded.
   2. MockMvc tests for 401, 403 and 200.
4. Open a PR: `feat(dispatch): courier workload view [7.3]`.

#### 7.4 Assignment history (depends on M3's 3.7)

1. Agree the `delivery.api` method signature with M3, then wait for 3.7 to merge.
2. Add `GET /api/v1/parcels/{id}/assignment-history` (admin) in `dispatch.web`. It returns the
   courier and dispatcher names, resolved through `UserDirectory`.
3. Write MockMvc tests, then open a PR.

#### 7.5 Dispatcher UI (SD-308)

1. Read `frontend/AGENTS.md` and the Next.js docs in `frontend/node_modules/next/dist/docs/`.
2. Add `frontend/lib/dispatch.ts` with typed API functions for 7.1–7.4, using `lib/api.ts`.
3. `(dispatch)/dispatch/page.tsx`:
   - an **unassigned parcels** table (paged)
   - an **Assign** button that opens a dialog with a courier picker sorted by workload, showing
     each courier's active parcel count
   - on success, refresh the table and show a toast
   - show 409 errors as a readable message
4. **Couriers** management page or tab: list profiles, create a profile for an existing courier
   user, edit phone and vehicle, activate/deactivate.
5. **Workload** panel: cards or a bar list of active parcels per courier.
6. *(Optional)* An assignment-history drawer on a parcel row (7.4).
7. Run `npm run lint` and `npm run build`, check the page at mobile width, then open a PR:
   `feat(frontend): dispatcher dashboard [7.5]`.

#### 7.6 Coverage: done

JaCoCo is configured in `backend/pom.xml`, and CI uploads `backend/target/site/jacoco/` as the
`jacoco-coverage-report` artifact. Later, agree a minimum coverage threshold with the team and
add a `check` goal.

#### 7.7 Security and personal-data review (SD-704)

1. Go through SYS-7 and IMP-6 module by module:
   - input validation on every request DTO
   - passwords hashed
   - no personal data (names, phones, addresses) in logs, error messages, events or the public
     tracking view
   - no hard-coded secrets
2. Record the findings in `docs/reviews/security-review.md` and open fix issues for module owners.

#### 7.8 Test report and contribution sheet (SD-703, SD-908, SD-909)

1. Collect the Surefire and Failsafe results and the JaCoCo summary into `docs/testing/` for the
   submission (SUB-3).
2. Build the contribution sheet (SUB-4) from the [summary](#2-summary) and each member's ✅
   sub-parts in this file.

### M8 — Pricing and reports

Owns the `pricing` module, the `delivery` report package, and the `(admin)/admin/pricing` and
`(admin)/admin/reports` pages.

| ID | Sub-part | SD | Status | Branch | PR | Date |
|---|---|---|---|---|---|---|
| 8.1 | Pricing schema: zones and pricing rules | SD-201 | ✅ | | | |
| 8.2 | `pricing.api.FeeCalculator` (Strategy pattern) | SD-202 | ✅ | | | |
| 8.3 | Public price-quote endpoint | SD-203 | ✅ | | | |
| 8.4 | Pricing administration API (zones and rules CRUD) | SD-211 | ⬜ | | | |
| 8.5 | Pricing administration UI | SD-212 | ⬜ | | | |
| 8.6 | Reports API: deliveries per day, success/failure rate | SD-314 | ⬜ | | | |
| 8.7 | Reports UI with date range and chart | SD-315 | ⬜ | | | |

**How to implement**

- **8.4:**
  1. Add `POST /api/v1/zones`, `PUT /api/v1/zones/{id}`, `GET/POST /api/v1/pricing-rules` and
     `PUT /api/v1/pricing-rules/{id}`, all admin only.
  2. Validate weight bands so they don't overlap.
  3. MockMvc tests: admin-only writes; public `GET /zones` still works.
- **8.5:** Build the `admin/pricing` page: a zones table and a rules table with add/edit dialogs.
- **8.6:** Follow decision D-08:
  - "Deliveries per day" = parcels created per business day in `Asia/Colombo`, including days
    with 0.
  - The rate = `DELIVERED / (DELIVERED + FAILED)` by the date the parcel reached its final status.
  - Endpoints: `GET /api/v1/reports/daily-deliveries?from&to` and `.../delivery-outcomes`, with a
    92-day maximum range.
  - Unit tests with a fixed `Clock`.
- **8.7:** Build the `admin/reports` page: a date-range picker, a table and a simple bar or line
  chart.

### M9 — RabbitMQ events and notifications

Owns the `notification` module, the outbox relay and `shared.messaging`.

| ID | Sub-part | SD | Status | Branch | PR | Date |
|---|---|---|---|---|---|---|
| 9.1 | RabbitMQ topology (exchange, queues, dead-letter) and broker check | SD-501 | ⬜ | | | |
| 9.2 | Outbox relay: publish `parcel_outbox_events` in order | SD-502 | ⬜ | | | |
| 9.3 | Shared ordered-consumer support (duplicates, gaps) | SD-503 | ⬜ | | | |
| 9.4 | Notification consumer and mock SMS/email store | SD-504 | ⬜ | | | |
| 9.5 | Notifications inbox API, page and unread bell | SD-506 | ⬜ | | | |
| 9.6 | Dead-letter replay (audited, admin only) | SD-507 | ⬜ | | | |
| 9.7 | Event-flow integration test (TST-1) | SD-701 | ⬜ | | | |

**How to implement**

- **9.1:**
  1. Declare the topic exchange, queues and dead-letter queues exactly as in ADR 0004 §5.
  2. Put the settings in `application.properties`.
  3. Add a test that the declarations load.
- **9.2:**
  1. Add a scheduled relay that reads unpublished outbox rows in sequence order.
  2. Publish them with publisher confirms and mark them published.
  3. On failure, stop and retry without skipping a row (ADR 0004 §4.2).
- **9.3:**
  1. Add a consumer-state table and a helper that ignores duplicates or older sequences.
  2. When there is a gap, reconcile it through 3.8.
- **9.4:**
  1. Add the `notification_create_tables` migration.
  2. Add a consumer that creates a notification for the parcel owner on each status change,
     without personal data in the logs.
- **9.5:**
  1. Add `GET /api/v1/notifications`, `GET .../unread-count`, `PATCH .../{id}/read` and
     `POST .../read-all`.
  2. Add a notification bell in the nav header and a `/notifications` page.
- **9.6:** Add an admin endpoint that moves a dead-lettered message back to its queue and writes
  an audit row.
- **9.7:**
  1. Add a Testcontainers test (PostgreSQL and RabbitMQ) that changes a status.
  2. Assert that the notification row exists **and** a merchant webhook delivery record exists.

### M10 — Live tracking, containers, demo

Owns the `tracking` module, `frontend/app/track`, the Dockerfiles, the `compose.yaml` app
profile and the demo script.

| ID | Sub-part | SD | Status | Branch | PR | Date |
|---|---|---|---|---|---|---|
| 10.1 | SSE tracking stream `GET /api/v1/tracked-parcels/{n}/events` | SD-508 | ✅ | | | |
| 10.2 | Public mobile tracking page with live updates | SD-510 | 🟡 | | | |
| 10.3 | Backend Dockerfile | SD-801 | ⬜ | | | |
| 10.4 | Frontend Dockerfile | SD-802 | ⬜ | | | |
| 10.5 | Full-stack Compose profile (`docker compose --profile app up`) | SD-803 | ⬜ | | | |
| 10.6 | Demo and seed data | SD-009 | ⬜ | | | |
| 10.7 | README setup instructions for submission (SUB-1) | SD-804 | ⬜ | | | |
| 10.8 | *(Bonus BON-2)* AI delivery assistant calling backend APIs | — | ⬜ | | | |

**How to implement**

- **10.2:**
  1. On the `track` page, enter a tracking number and load the snapshot from
     `GET /api/v1/tracked-parcels/{n}`.
  2. Open an `EventSource` on `.../events`.
  3. Update the timeline live, and reconnect on error.
  4. Check the layout at 360 px.
- **10.3:** Multi-stage build: a Temurin 21 JDK builds with `./mvnw package`, then a Temurin 21
  JRE runtime image runs as a non-root user, with a health check on `/actuator/health`.
- **10.4:** Node 24 multi-stage build using Next.js `output: "standalone"`.
- **10.5:**
  1. Add `backend` and `frontend` services under `profiles: ["app"]`.
  2. Make them depend on healthy PostgreSQL and RabbitMQ.
  3. Read all settings from `.env`.
- **10.6:** Add a dev-only seeder (behind a Spring profile) that creates demo users, zones, a
  courier profile and parcels in different statuses, using the DEM-1 to DEM-4 scenarios.
- **10.7:** Write clean-machine setup steps, test accounts and the demo script in `README.md`.
- **10.8:** Optional. The reference project used an LLM chatbot plus MCP tools (its ADR 0010).
  1. Write a Proposed ADR first.
  2. Expose read-only tools ("where is my parcel", "price quote") that call the existing
     endpoints.

---

## 4. Change log

Newest first. Use one line per status change: `YYYY-MM-DD · <id> · <status> · <member> · <PR or note>`.

- 2026-11-14 · all · tracker created; statuses set from the current code · M7 · —
- 2026-11-11 · 7.1 · 🟡 · M7 · commit `setup1` (couriers migration, `CourierProfile`, service, tests)
