package com.swiftdrop.pricing.web;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.swiftdrop.pricing.application.InvalidPricingRuleException;
import com.swiftdrop.pricing.application.PricingRuleConflictException;
import com.swiftdrop.pricing.application.PricingRuleNotFoundException;
import com.swiftdrop.pricing.application.RouteNotPricedException;
import com.swiftdrop.pricing.application.ZoneConflictException;
import com.swiftdrop.pricing.application.ZoneNotFoundException;

/**
 * Maps pricing failures to safe RFC 9457 bodies. Not limited to pricing controllers: delivery's
 * booking endpoints call {@code FeeCalculator} and must return the same 409 for an unpriced route.
 * Ordered before {@code GlobalExceptionHandler}, whose catch-all would otherwise answer 500.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class PricingExceptionHandler {

    @ExceptionHandler(RouteNotPricedException.class)
    ProblemDetail routeNotPriced(RouteNotPricedException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(ZoneConflictException.class)
    ProblemDetail zoneConflict(ZoneConflictException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(ZoneNotFoundException.class)
    ProblemDetail zoneNotFound(ZoneNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(PricingRuleConflictException.class)
    ProblemDetail ruleConflict(PricingRuleConflictException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(PricingRuleNotFoundException.class)
    ProblemDetail ruleNotFound(PricingRuleNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(InvalidPricingRuleException.class)
    ProblemDetail invalidRule(InvalidPricingRuleException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }
}
