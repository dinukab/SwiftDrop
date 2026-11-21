# Notification module

> Branch `feature/notifications`, 2026-11-21. Implements ADR 0004 section 8 (mock SMS) for customers. PROGRESS rows 9.4
> and 9.5 (backend and UI built); 9.3, 9.6 and 9.7 are partly covered, see section 8.

## 1. What it does

A customer who booked a parcel gets an in-app message when the parcel is **picked up**, **out for delivery**,
**delivered** or **failed**. The message is a *mock SMS*: it is stored in the `notifications` table and shown in the
app, nothing is sent to a phone. Merchant parcels never notify (the merchant gets webhooks). `CREATED`, `IN_TRANSIT`
and every hub move (`HUB_ARRIVED`, `HUB_SORTED`, direct hub transfers) are skipped to limit noise.

## 2. Flow

```
status change in delivery (courier, dispatcher, merchant)
  -> same transaction: status history row + outbox row (parcel_outbox_events)
  -> OutboxRelay (every second, one instance) publishes to exchange swiftdrop.events with publisher confirms
  -> queue notification.parcel-status-changed (quorum, dead-letter queue .dlq)
  -> ParcelStatusNotificationConsumer -> NotificationService.record -> notifications row
  -> customer: bell with unread count, /customer/notifications inbox
```

Failures never affect the delivery: the notification is made after the status change was committed and published.

## 3. Code

| Part | Files (`backend/src/main/java/com/swiftdrop/notification/`) |
|---|---|
| Migration | `db/migration/notification/V202611211000__notification_create_notifications.sql` (unique `event_id`, inbox indexes) |
| Domain | `domain/Notification`, `NotificationRepository` |
| Rules | `application/NotificationPolicy` (who and which statuses), `NotificationService` (record, inbox, read), `NotificationNotFoundException` |
| Consumer | `infrastructure/messaging/ParcelStatusNotificationConsumer`; `NotificationMessagingConfiguration` binds the queue to `swiftdrop.events` only when the consumer is on |
| API | `web/NotificationController`, `NotificationResponse`, `NotificationExceptionHandler`, `Callers` |
| Frontend | `lib/notifications.ts`, `components/notifications/notification-bell.tsx`, `app/(customer)/customer/notifications/page.tsx`, bell via `PortalShell` `headerAction`, nav item "Notifications" |

The module uses only `delivery.api` (the event) and `shared`; `NotificationMessagingConfiguration` already imported
`ParcelStatusChanged`. The consumer reads only the event fields it needs and ignores the rest.

## 4. API (role `CUSTOMER`; the user is always the caller from the token)

| Endpoint | Meaning |
|---|---|
| `GET /api/v1/notifications?page&size` | the caller's inbox, newest first |
| `GET /api/v1/notifications/unread-count` | `{count}` for the bell |
| `PATCH /api/v1/notifications/{id}/read` | marks one read; 404 if it is not the caller's (same as missing) |
| `POST /api/v1/notifications/read-all` | marks all of the caller's read, `{updated}` |

Other roles get 403 and anonymous callers 401.

## 5. Rules and guarantees

- **One event, at most one message:** `event_id` is unique and `record` checks it first, so a redelivered message
  creates nothing.
- **Malformed events** are rejected without requeue and go to the dead-letter queue.
- **Privacy:** the text names the tracking number and the status only. No phone number or address is stored, and the
  text is never logged (IDs only).
- **Ordering:** each notification is independent, so the consumer does not need per-parcel ordering state. A late event
  can produce a late message (ADR 0004 section 10 leaves suppression of superseded statuses open).

## 6. Turning it on (off by default, like the relay and webhooks)

```
SWIFTDROP_MESSAGING_RELAY_ENABLED=true     # publishes the outbox to RabbitMQ (one instance only)
SWIFTDROP_NOTIFICATIONS_ENABLED=true       # the consumer and the queue binding
```

RabbitMQ must be running (`docker compose up -d`). Without the relay no event reaches the queue; without the consumer
the queue is not bound, so nothing piles up.

## 7. Tests

- Unit: `NotificationPolicyTest`, `NotificationServiceTest`, `ParcelStatusNotificationConsumerTest`; the migration lists
  in `MigrationConventionTest` and `DatabaseMigrationIT` (32 migrations).
- PostgreSQL: `NotificationInboxIT` (inbox, unread count, reading, per-user isolation, roles, one message per event).
- End to end: `NotificationFlowIT` (PostgreSQL and RabbitMQ: courier status changes reach the inbox through the relay;
  hub arrival and merchant parcels leave nothing).
- Frontend: `tsc`, eslint and `next build` pass; there is no frontend test framework and the pages were not checked in a
  browser.

## 8. Not done

- **9.3** a shared ordered-consumer helper with gap reconciliation (not needed for independent messages).
- **9.6** admin dead-letter replay.
- Notifications for couriers, dispatchers or the recipients of merchant parcels (needs contact data and consent rules).
- Real email or SMS delivery (would need its own delivery records and retries, like webhooks).
- Live refresh of the bell: it polls every 30 seconds.
