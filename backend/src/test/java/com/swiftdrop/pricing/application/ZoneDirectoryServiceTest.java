package com.swiftdrop.pricing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.swiftdrop.pricing.api.ZoneSummary;
import com.swiftdrop.pricing.domain.Zone;
import com.swiftdrop.pricing.domain.ZoneRepository;

class ZoneDirectoryServiceTest {

    private ZoneRepository zones;
    private ZoneDirectoryService service;

    @BeforeEach
    void setUp() {
        zones = mock(ZoneRepository.class);
        service = new ZoneDirectoryService(zones);
    }

    private static Zone zone(String code, String name) {
        Zone zone = new Zone(code, name);
        ReflectionTestUtils.setField(zone, "id", UUID.randomUUID());
        return zone;
    }

    @Test
    void findsTheZoneIdByCodeIgnoringCaseAndSpaces() {
        Zone uva = zone("LK-UP", "Uva Province");
        when(zones.findByCode("LK-UP")).thenReturn(Optional.of(uva));

        assertThat(service.findZoneIdByCode(" lk-up ")).contains(uva.getId());
    }

    @Test
    void anUnknownOrBlankCodeFindsNothing() {
        when(zones.findByCode("LK-XX")).thenReturn(Optional.empty());

        assertThat(service.findZoneIdByCode("LK-XX")).isEmpty();
        assertThat(service.findZoneIdByCode("  ")).isEmpty();
        assertThat(service.findZoneIdByCode(null)).isEmpty();
        verify(zones, never()).findByCode("");
    }

    @Test
    void mapsZoneIdsToCodesInOneLookup() {
        Zone uva = zone("LK-UP", "Uva Province");
        Zone southern = zone("LK-SP", "Southern Province");
        UUID missing = UUID.randomUUID();
        when(zones.findAllById(Set.of(uva.getId(), southern.getId(), missing)))
                .thenReturn(List.of(uva, southern));

        assertThat(service.findCodes(Set.of(uva.getId(), southern.getId(), missing)))
                .containsEntry(uva.getId(), "LK-UP")
                .containsEntry(southern.getId(), "LK-SP")
                .doesNotContainKey(missing)
                .hasSize(2);
    }

    @Test
    void listsTheActiveZonesAsSummaries() {
        Zone uva = zone("LK-UP", "Uva Province");
        Zone southern = zone("LK-SP", "Southern Province");
        when(zones.findByActiveTrue()).thenReturn(List.of(uva, southern));

        assertThat(service.findActiveZones()).containsExactly(
                new ZoneSummary(uva.getId(), "LK-UP", "Uva Province"),
                new ZoneSummary(southern.getId(), "LK-SP", "Southern Province"));
    }

    @Test
    void noActiveZonesGivesAnEmptyList() {
        when(zones.findByActiveTrue()).thenReturn(List.of());

        assertThat(service.findActiveZones()).isEmpty();
    }

    @Test
    void noIdsMeansNoLookup() {
        assertThat(service.findCodes(null)).isEmpty();
        assertThat(service.findCodes(Set.of())).isEmpty();
        verify(zones, never()).findAllById(org.mockito.ArgumentMatchers.any());
    }
}
