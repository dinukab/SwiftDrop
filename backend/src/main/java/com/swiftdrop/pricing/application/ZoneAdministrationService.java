package com.swiftdrop.pricing.application;

import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.pricing.domain.Zone;
import com.swiftdrop.pricing.domain.ZoneRepository;

/**
 * Admin maintenance of delivery zones (ADM-3, sub-part 8.4). Codes and names are unique, so a clash
 * is reported as a conflict before the insert or update; the unique constraint still catches races.
 */
@Service
public class ZoneAdministrationService {

    private final ZoneRepository zones;

    public ZoneAdministrationService(ZoneRepository zones) {
        this.zones = zones;
    }

    /** Every zone, active or not, ordered by name. */
    @Transactional(readOnly = true)
    public List<Zone> listAll() {
        return zones.findAll(Sort.by("name"));
    }

    /**
     * @throws ZoneConflictException if the code or name is already used
     * @throws IllegalArgumentException if the code or name is blank
     */
    @Transactional
    public Zone create(String code, String name, boolean active) {
        String normalizedCode = Zone.normalizeCode(code);
        String normalizedName = Zone.normalizeName(name);
        if (zones.existsByCode(normalizedCode)) {
            throw new ZoneConflictException("A zone with code " + normalizedCode + " already exists");
        }
        if (zones.existsByName(normalizedName)) {
            throw new ZoneConflictException("A zone named " + normalizedName + " already exists");
        }
        return save(new Zone(normalizedCode, normalizedName, active));
    }

    /**
     * Changes a zone. Keeping its own code or name is allowed; taking another zone's is not.
     *
     * @throws ZoneNotFoundException if no zone has this id
     * @throws ZoneConflictException if the code or name belongs to another zone
     */
    @Transactional
    public Zone update(UUID id, String code, String name, boolean active) {
        Zone zone = zones.findById(id).orElseThrow(() -> new ZoneNotFoundException(id));
        String normalizedCode = Zone.normalizeCode(code);
        String normalizedName = Zone.normalizeName(name);
        if (!normalizedCode.equals(zone.getCode()) && zones.existsByCode(normalizedCode)) {
            throw new ZoneConflictException("A zone with code " + normalizedCode + " already exists");
        }
        if (!normalizedName.equals(zone.getName()) && zones.existsByName(normalizedName)) {
            throw new ZoneConflictException("A zone named " + normalizedName + " already exists");
        }
        zone.setCode(normalizedCode);
        zone.setName(normalizedName);
        zone.setActive(active);
        return save(zone);
    }

    private Zone save(Zone zone) {
        try {
            return zones.saveAndFlush(zone);
        } catch (DataIntegrityViolationException e) {
            // Another request took the code or name between the check and the write
            throw new ZoneConflictException("The zone code or name is already in use");
        }
    }
}
