package com.swiftdrop.dispatch.web;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.swiftdrop.delivery.api.ParcelConflictException;
import com.swiftdrop.delivery.api.ParcelNotFoundException;
import com.swiftdrop.dispatch.application.CourierNotEligibleException;

/**
 * Safe RFC 9457 bodies for dispatch endpoints. Each message names the broken rule only; no order,
 * courier or personal data is echoed.
 */
@RestControllerAdvice(basePackageClasses = DispatchExceptionHandler.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class DispatchExceptionHandler {

    @ExceptionHandler(CourierNotEligibleException.class)
    ProblemDetail notEligible(CourierNotEligibleException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(ParcelConflictException.class)
    ProblemDetail parcelConflict(ParcelConflictException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(ParcelNotFoundException.class)
    ProblemDetail parcelNotFound(ParcelNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Parcel not found");
    }
}
