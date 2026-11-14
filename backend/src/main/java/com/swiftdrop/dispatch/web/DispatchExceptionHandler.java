package com.swiftdrop.dispatch.web;

import java.util.List;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.swiftdrop.dispatch.application.CourierProfileNotFoundException;
import com.swiftdrop.dispatch.application.CourierUserNotEligibleException;
import com.swiftdrop.dispatch.application.CourierUserNotFoundException;
import com.swiftdrop.dispatch.application.DuplicateCourierProfileException;

/**
 * Safe RFC 9457 bodies for dispatch endpoints. Limited to this package and ordered before
 * {@code GlobalExceptionHandler}, whose catch-all would otherwise answer 500.
 */
@RestControllerAdvice(basePackageClasses = DispatchExceptionHandler.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class DispatchExceptionHandler {

    @ExceptionHandler({CourierProfileNotFoundException.class, CourierUserNotFoundException.class})
    ProblemDetail notFound(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler({DuplicateCourierProfileException.class, CourierUserNotEligibleException.class})
    ProblemDetail conflict(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    /** Domain validation such as the phone format; the message is not echoed. */
    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail invalid(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid request");
    }

    /** A malformed path or query value, such as a bad UUID. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail typeMismatch(MethodArgumentTypeMismatchException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid request");
        problem.setProperty("invalidFields", List.of(e.getName()));
        return problem;
    }
}
