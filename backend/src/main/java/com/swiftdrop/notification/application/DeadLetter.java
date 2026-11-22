package com.swiftdrop.notification.application;

import java.time.Instant;
import java.util.UUID;

/**
 * What an administrator may see of one dead-lettered message: IDs, the status and the broker's reason. The body is
 * never exposed, because it names a parcel and its owner.
 *
 * @param eventId       the event ID, or {@code null} if the body could not be parsed
 * @param status        the new parcel status the event carried, or {@code null} if unknown
 * @param reason        why the broker dead-lettered it (for example {@code rejected} or {@code delivery_limit})
 * @param deathCount    how many times the broker dead-lettered it
 * @param deadLetteredAt when it was first dead-lettered, or {@code null} if the broker did not say
 */
public record DeadLetter(UUID eventId, String status, String reason, long deathCount, Instant deadLetteredAt) {
}
