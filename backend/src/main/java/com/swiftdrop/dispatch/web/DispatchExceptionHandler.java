package com.swiftdrop.dispatch.web;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.swiftdrop.dispatch.application.CourierProfileNotFoundException;
import com.swiftdrop.dispatch.application.CourierUserNotEligibleException;
import com.swiftdrop.dispatch.application.CourierUserNotFoundException;
import com.swiftdrop.dispatch.application.DuplicateCourierProfileException;

/** Maps dispatch exceptions to problem details. Messages never include phone numbers. */
@RestControllerAdvice(basePackageClasses = DispatchExceptionHandler.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class DispatchExceptionHandler {

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

    @ExceptionHandler(CourierUserNotEligibleException.class)
    ProblemDetail notEligible(CourierUserNotEligibleException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    /** Domain validation, e.g. a phone number that is not in E.164 format; the message is safe to show. */
    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail invalid(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }
}
