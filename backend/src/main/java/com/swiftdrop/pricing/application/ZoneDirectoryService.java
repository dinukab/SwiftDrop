package com.swiftdrop.pricing.application;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.pricing.api.ZoneDirectory;
import com.swiftdrop.pricing.domain.Zone;
import com.swiftdrop.pricing.domain.ZoneRepository;

/** {@link ZoneDirectory} for other modules; read-only and free of personal data. */
@Service
@Transactional(readOnly = true)
class ZoneDirectoryService implements ZoneDirectory {

    private final ZoneRepository zones;

    ZoneDirectoryService(ZoneRepository zones) {
        this.zones = zones;
    }

    @Override
    public Optional<UUID> findZoneIdByCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return zones.findByCode(Zone.normalizeCode(code)).map(Zone::getId);
    }

    @Override
    public Map<UUID, String> findCodes(Collection<UUID> zoneIds) {
        if (zoneIds == null || zoneIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> codes = new HashMap<>();
        for (Zone zone : zones.findAllById(zoneIds)) {
            codes.put(zone.getId(), zone.getCode());
        }
        return Map.copyOf(codes);
    }
}
