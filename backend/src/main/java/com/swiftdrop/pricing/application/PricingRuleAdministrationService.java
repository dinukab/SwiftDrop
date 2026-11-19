package com.swiftdrop.pricing.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.pricing.domain.PricingRule;
import com.swiftdrop.pricing.domain.PricingRuleRepository;
import com.swiftdrop.pricing.domain.ZoneRepository;

/**
 * Admin maintenance of pricing rules (ADM-3, sub-part 8.4). There is at most one rule per ordered
 * origin → destination pair; a second rule for the same pair is a conflict, not an overwrite.
 */
@Service
public class PricingRuleAdministrationService {

    private final PricingRuleRepository rules;
    private final ZoneRepository zones;

    public PricingRuleAdministrationService(PricingRuleRepository rules, ZoneRepository zones) {
        this.rules = rules;
        this.zones = zones;
    }

    /** Every rule, active or not, ordered by origin then destination zone id. */
    @Transactional(readOnly = true)
    public List<PricingRule> listAll() {
        return rules.findAll(Sort.by("originZoneId", "destinationZoneId"));
    }

    /**
     * @throws InvalidPricingRuleException if a referenced zone does not exist
     * @throws PricingRuleConflictException if the origin → destination pair already has a rule
     */
    @Transactional
    public PricingRule create(RuleValues values) {
        requireZonesExist(values.originZoneId(), values.destinationZoneId());
        if (rules.existsByOriginZoneIdAndDestinationZoneId(values.originZoneId(), values.destinationZoneId())) {
            throw new PricingRuleConflictException();
        }
        PricingRule rule = new PricingRule(
                values.originZoneId(),
                values.destinationZoneId(),
                values.baseFee(),
                values.perKgFee(),
                values.smallSizeSurcharge(),
                values.mediumSizeSurcharge(),
                values.largeSizeSurcharge(),
                values.currency(),
                values.active());
        return save(rule);
    }

    /**
     * Replaces the fees, currency and active flag of a rule. Moving a rule to a different pair must
     * not collide with another rule.
     *
     * @throws PricingRuleNotFoundException if no rule has this id
     */
    @Transactional
    public PricingRule update(UUID id, RuleValues values) {
        PricingRule rule = rules.findById(id).orElseThrow(() -> new PricingRuleNotFoundException(id));
        requireZonesExist(values.originZoneId(), values.destinationZoneId());

        boolean samePair = values.originZoneId().equals(rule.getOriginZoneId())
                && values.destinationZoneId().equals(rule.getDestinationZoneId());
        if (!samePair && rules.existsByOriginZoneIdAndDestinationZoneId(values.originZoneId(), values.destinationZoneId())) {
            throw new PricingRuleConflictException();
        }

        rule.setOriginZoneId(values.originZoneId());
        rule.setDestinationZoneId(values.destinationZoneId());
        rule.setBaseFee(values.baseFee());
        rule.setPerKgFee(values.perKgFee());
        rule.setSmallSizeSurcharge(values.smallSizeSurcharge());
        rule.setMediumSizeSurcharge(values.mediumSizeSurcharge());
        rule.setLargeSizeSurcharge(values.largeSizeSurcharge());
        rule.setCurrency(values.currency());
        rule.setActive(values.active());
        return save(rule);
    }

    private void requireZonesExist(UUID originZoneId, UUID destinationZoneId) {
        if (!zones.existsById(originZoneId)) {
            throw new InvalidPricingRuleException("The origin zone does not exist");
        }
        if (!zones.existsById(destinationZoneId)) {
            throw new InvalidPricingRuleException("The destination zone does not exist");
        }
    }

    private PricingRule save(PricingRule rule) {
        try {
            return rules.saveAndFlush(rule);
        } catch (DataIntegrityViolationException e) {
            // Another request created the same pair between the check and the write
            throw new PricingRuleConflictException();
        }
    }

    /** The values an admin supplies for a rule, already checked for presence by the web layer. */
    public record RuleValues(
            UUID originZoneId,
            UUID destinationZoneId,
            BigDecimal baseFee,
            BigDecimal perKgFee,
            BigDecimal smallSizeSurcharge,
            BigDecimal mediumSizeSurcharge,
            BigDecimal largeSizeSurcharge,
            String currency,
            boolean active) {
    }
}
