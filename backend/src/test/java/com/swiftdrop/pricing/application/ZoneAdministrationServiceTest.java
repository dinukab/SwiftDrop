package com.swiftdrop.pricing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import com.swiftdrop.pricing.domain.Zone;
import com.swiftdrop.pricing.domain.ZoneRepository;

class ZoneAdministrationServiceTest {

    private final ZoneRepository zones = mock(ZoneRepository.class);
    private final ZoneAdministrationService service = new ZoneAdministrationService(zones);

    @Test
    void createNormalizesCodeAndSavesActiveZone() {
        when(zones.existsByCode("LK-NP")).thenReturn(false);
        when(zones.existsByName("Northern Province")).thenReturn(false);
        when(zones.saveAndFlush(any(Zone.class))).thenAnswer(inv -> inv.getArgument(0));

        Zone created = service.create(" lk-np ", " Northern Province ", true);

        assertThat(created.getCode()).isEqualTo("LK-NP");
        assertThat(created.getName()).isEqualTo("Northern Province");
        assertThat(created.isActive()).isTrue();
    }

    @Test
    void createRejectsDuplicateCode() {
        when(zones.existsByCode("LK-NP")).thenReturn(true);

        assertThatThrownBy(() -> service.create("LK-NP", "Northern Province", true))
                .isInstanceOf(ZoneConflictException.class)
                .hasMessageContaining("LK-NP");
        verify(zones, never()).saveAndFlush(any());
    }

    @Test
    void createRejectsDuplicateName() {
        when(zones.existsByCode("LK-NP")).thenReturn(false);
        when(zones.existsByName("Northern Province")).thenReturn(true);

        assertThatThrownBy(() -> service.create("LK-NP", "Northern Province", true))
                .isInstanceOf(ZoneConflictException.class);
    }

    @Test
    void createTurnsARacingUniqueViolationIntoAConflict() {
        when(zones.existsByCode(any())).thenReturn(false);
        when(zones.existsByName(any())).thenReturn(false);
        when(zones.saveAndFlush(any(Zone.class))).thenThrow(new DataIntegrityViolationException("uq_zones_code"));

        assertThatThrownBy(() -> service.create("LK-NP", "Northern Province", true))
                .isInstanceOf(ZoneConflictException.class);
    }

    @Test
    void updateMayKeepItsOwnCodeAndName() {
        Zone existing = new Zone("LK-NP", "Northern Province");
        UUID id = UUID.randomUUID();
        when(zones.findById(id)).thenReturn(Optional.of(existing));
        when(zones.saveAndFlush(existing)).thenReturn(existing);

        Zone updated = service.update(id, "LK-NP", "Northern Province", false);

        assertThat(updated.isActive()).isFalse();
        verify(zones, never()).existsByCode(any());
        verify(zones, never()).existsByName(any());
    }

    @Test
    void updateRejectsAnotherZonesCode() {
        UUID id = UUID.randomUUID();
        when(zones.findById(id)).thenReturn(Optional.of(new Zone("LK-NP", "Northern Province")));
        when(zones.existsByCode("LK-WP")).thenReturn(true);

        assertThatThrownBy(() -> service.update(id, "LK-WP", "Northern Province", true))
                .isInstanceOf(ZoneConflictException.class);
    }

    @Test
    void updateOfUnknownZoneIsNotFound() {
        UUID id = UUID.randomUUID();
        when(zones.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(id, "LK-NP", "Northern Province", true))
                .isInstanceOf(ZoneNotFoundException.class);
    }
}
