package com.swiftdrop.pricing.api;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Zone lookups for other modules (ADR 0003). A zone's {@code code} is stable across environments
 * (for example {@code LK-UP} for the Uva province); its ID is generated per environment, so modules
 * that need to talk about a province use the code.
 */
public interface ZoneDirectory {

    /** The ID of the zone with this code (trimmed, case-insensitive), empty if there is none. */
    Optional<UUID> findZoneIdByCode(String code);

    /**
     * Codes for the given zone IDs, in one lookup. IDs without a zone are absent from the map;
     * a {@code null} or empty collection gives an empty map.
     */
    Map<UUID, String> findCodes(Collection<UUID> zoneIds);
}
