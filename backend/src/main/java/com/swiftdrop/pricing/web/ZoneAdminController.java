package com.swiftdrop.pricing.web;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.swiftdrop.pricing.application.ZoneAdministrationService;
import com.swiftdrop.pricing.domain.Zone;

/**
 * Admin maintenance of delivery zones (ADM-3, sub-part 8.4). Admins see inactive zones too; the
 * public list at {@code /api/v1/zones} keeps returning active zones only.
 */
@RestController
@RequestMapping("/api/v1/admin/zones")
@PreAuthorize("hasRole('ADMIN')")
class ZoneAdminController {

    private final ZoneAdministrationService zones;

    ZoneAdminController(ZoneAdministrationService zones) {
        this.zones = zones;
    }

    @GetMapping
    List<ZoneAdminResponse> list() {
        return zones.listAll().stream().map(ZoneAdminResponse::from).toList();
    }

    @PostMapping
    ResponseEntity<ZoneAdminResponse> create(@Valid @RequestBody ZoneRequest request) {
        Zone zone = zones.create(request.code(), request.name(), request.active());
        return ResponseEntity.created(URI.create("/api/v1/admin/zones/" + zone.getId()))
                .body(ZoneAdminResponse.from(zone));
    }

    @PutMapping("/{id}")
    ZoneAdminResponse update(@PathVariable UUID id, @Valid @RequestBody ZoneRequest request) {
        return ZoneAdminResponse.from(zones.update(id, request.code(), request.name(), request.active()));
    }
}
