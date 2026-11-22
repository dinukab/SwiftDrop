package com.swiftdrop.pricing.web;

import java.util.UUID;

import com.swiftdrop.pricing.domain.Zone;

/** A zone as an admin sees it, including inactive zones. */
record ZoneAdminResponse(UUID id, String code, String name, boolean active) {

    static ZoneAdminResponse from(Zone zone) {
        return new ZoneAdminResponse(zone.getId(), zone.getCode(), zone.getName(), zone.isActive());
    }
}
