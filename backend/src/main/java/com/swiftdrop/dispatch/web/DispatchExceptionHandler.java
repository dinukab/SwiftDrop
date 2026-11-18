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
import com.swiftdrop.dispatch.application.CourierProfileNotFoundException;
import com.swiftdrop.dispatch.application.CourierUserNotEligibleException;
import com.swiftdrop.dispatch.application.CourierUserNotFoundException;
import com.swiftdrop.dispatch.application.DispatcherNotEligibleException;
import com.swiftdrop.dispatch.application.DispatcherUserNotFoundException;
import com.swiftdrop.dispatch.application.DuplicateCourierProfileException;
import com.swiftdrop.dispatch.application.HubNotFoundException;

/**
 * Safe RFC 9457 bodies for dispatch endpoints. Each message names the broken rule only; no order,
 * courier, phone number or other personal data is echoed.
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

    @ExceptionHandler(CourierProfileNotFoundException.class)
    ProblemDetail profileNotFound(CourierProfileNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(CourierUserNotFoundException.class)
    ProblemDetail userNotFound(CourierUserNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(DuplicateCourierProfileException.class)
    ProblemDetail duplicate(DuplicateCourierProfileException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    /** Renamed from {@code notEligible}: the same name is already used for the courier-based check above. */
    @ExceptionHandler(CourierUserNotEligibleException.class)
    ProblemDetail userNotEligible(CourierUserNotEligibleException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler({HubNotFoundException.class, DispatcherUserNotFoundException.class})
    ProblemDetail hubOrDispatcherNotFound(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(DispatcherNotEligibleException.class)
    ProblemDetail dispatcherNotEligible(DispatcherNotEligibleException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    /** Domain validation, e.g. a phone number that is not in E.164 format; the message is safe to show. */
    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail invalid(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }
}
