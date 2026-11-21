package com.swiftdrop.pricing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import com.swiftdrop.pricing.application.PricingRuleAdministrationService.RuleValues;
import com.swiftdrop.pricing.domain.PricingRule;
import com.swiftdrop.pricing.domain.PricingRuleRepository;
import com.swiftdrop.pricing.domain.ZoneRepository;

class PricingRuleAdministrationServiceTest {

    private final PricingRuleRepository rules = mock(PricingRuleRepository.class);
    private final ZoneRepository zones = mock(ZoneRepository.class);
    private final PricingRuleAdministrationService service = new PricingRuleAdministrationService(rules, zones);

    private final UUID origin = UUID.randomUUID();
    private final UUID destination = UUID.randomUUID();

    @Test
    void createSavesRuleWithNormalizedCurrency() {
        when(zones.existsById(origin)).thenReturn(true);
        when(zones.existsById(destination)).thenReturn(true);
        when(rules.existsByOriginZoneIdAndDestinationZoneId(origin, destination)).thenReturn(false);
        when(rules.saveAndFlush(any(PricingRule.class))).thenAnswer(inv -> inv.getArgument(0));

        PricingRule created = service.create(values(origin, destination, "lkr", true));

        assertThat(created.getCurrency()).isEqualTo("LKR");
        assertThat(created.getBaseFee()).isEqualByComparingTo("300.00");
        assertThat(created.isActive()).isTrue();
    }

    @Test
    void createRejectsUnknownOriginZone() {
        when(zones.existsById(origin)).thenReturn(false);

        assertThatThrownBy(() -> service.create(values(origin, destination, "LKR", true)))
                .isInstanceOf(InvalidPricingRuleException.class)
                .hasMessageContaining("origin");
        verify(rules, never()).saveAndFlush(any());
    }

    @Test
    void createRejectsUnknownDestinationZone() {
        when(zones.existsById(origin)).thenReturn(true);
        when(zones.existsById(destination)).thenReturn(false);

        assertThatThrownBy(() -> service.create(values(origin, destination, "LKR", true)))
                .isInstanceOf(InvalidPricingRuleException.class)
                .hasMessageContaining("destination");
    }

    @Test
    void createRejectsAPairThatAlreadyHasARule() {
        when(zones.existsById(any())).thenReturn(true);
        when(rules.existsByOriginZoneIdAndDestinationZoneId(origin, destination)).thenReturn(true);

        assertThatThrownBy(() -> service.create(values(origin, destination, "LKR", true)))
                .isInstanceOf(PricingRuleConflictException.class);
        verify(rules, never()).saveAndFlush(any());
    }

    @Test
    void racingDuplicateBecomesAConflict() {
        when(zones.existsById(any())).thenReturn(true);
        when(rules.existsByOriginZoneIdAndDestinationZoneId(any(), any())).thenReturn(false);
        when(rules.saveAndFlush(any(PricingRule.class))).thenThrow(new DataIntegrityViolationException("uq"));

        assertThatThrownBy(() -> service.create(values(origin, destination, "LKR", true)))
                .isInstanceOf(PricingRuleConflictException.class);
    }

    @Test
    void updateKeepingTheSamePairDoesNotCheckForDuplicates() {
        PricingRule existing = new PricingRule(origin, destination, BigDecimal.ONE, BigDecimal.ONE);
        UUID id = UUID.randomUUID();
        when(rules.findById(id)).thenReturn(Optional.of(existing));
        when(zones.existsById(any())).thenReturn(true);
        when(rules.saveAndFlush(existing)).thenReturn(existing);

        PricingRule updated = service.update(id, values(origin, destination, "LKR", false));

        assertThat(updated.getBaseFee()).isEqualByComparingTo("300.00");
        assertThat(updated.isActive()).isFalse();
        verify(rules, never()).existsByOriginZoneIdAndDestinationZoneId(any(), any());
    }

    @Test
    void movingARuleOntoAnotherPairIsAConflict() {
        PricingRule existing = new PricingRule(origin, origin, BigDecimal.ONE, BigDecimal.ONE);
        UUID id = UUID.randomUUID();
        when(rules.findById(id)).thenReturn(Optional.of(existing));
        when(zones.existsById(any())).thenReturn(true);
        when(rules.existsByOriginZoneIdAndDestinationZoneId(origin, destination)).thenReturn(true);

        assertThatThrownBy(() -> service.update(id, values(origin, destination, "LKR", true)))
                .isInstanceOf(PricingRuleConflictException.class);
        verify(rules, never()).saveAndFlush(any());
    }

    @Test
    void updateOfUnknownRuleIsNotFound() {
        UUID id = UUID.randomUUID();
        when(rules.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(id, values(origin, destination, "LKR", true)))
                .isInstanceOf(PricingRuleNotFoundException.class);
    }

    private static RuleValues values(UUID origin, UUID destination, String currency, boolean active) {
        return new RuleValues(origin, destination,
                new BigDecimal("300.00"), new BigDecimal("100.00"),
                BigDecimal.ZERO, new BigDecimal("150.00"), new BigDecimal("350.00"),
                currency, active);
    }
}
