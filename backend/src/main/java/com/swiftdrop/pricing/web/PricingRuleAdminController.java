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

import com.swiftdrop.pricing.application.PricingRuleAdministrationService;
import com.swiftdrop.pricing.domain.PricingRule;

/** Admin maintenance of pricing rules (ADM-3, sub-part 8.4). Admin only. */
@RestController
@RequestMapping("/api/v1/admin/pricing-rules")
@PreAuthorize("hasRole('ADMIN')")
class PricingRuleAdminController {

    private final PricingRuleAdministrationService rules;

    PricingRuleAdminController(PricingRuleAdministrationService rules) {
        this.rules = rules;
    }

    @GetMapping
    List<PricingRuleAdminResponse> list() {
        return rules.listAll().stream().map(PricingRuleAdminResponse::from).toList();
    }

    @PostMapping
    ResponseEntity<PricingRuleAdminResponse> create(@Valid @RequestBody PricingRuleRequest request) {
        PricingRule rule = rules.create(request.toValues());
        return ResponseEntity.created(URI.create("/api/v1/admin/pricing-rules/" + rule.getId()))
                .body(PricingRuleAdminResponse.from(rule));
    }

    @PutMapping("/{id}")
    PricingRuleAdminResponse update(@PathVariable UUID id, @Valid @RequestBody PricingRuleRequest request) {
        return PricingRuleAdminResponse.from(rules.update(id, request.toValues()));
    }
}
