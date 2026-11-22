package com.swiftdrop.pricing.api;

import java.util.UUID;

/**
 * A delivery zone as other modules see it: its generated ID, its stable code and its display name.
 * Nothing else about the zone leaves the pricing module.
 */
public record ZoneSummary(UUID id, String code, String name) {
}
