package com.swiftdrop.notification.web;

import java.util.List;

import org.springframework.amqp.AmqpException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.swiftdrop.notification.application.NotificationNotFoundException;

/**
 * Safe RFC 9457 bodies for notification endpoints; nothing about other users' messages is echoed, rejected values are
 * never repeated, and broker error text is never passed on.
 */
@RestControllerAdvice(basePackageClasses = NotificationExceptionHandler.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class NotificationExceptionHandler {

    @ExceptionHandler(NotificationNotFoundException.class)
    ProblemDetail notFound(NotificationNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail invalidRequest(MethodArgumentNotValidException e) {
        List<String> fields = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getField).distinct().sorted().toList();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid request");
        problem.setProperty("fields", fields);
        return problem;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail unreadable() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Malformed request body");
    }

    /** The broker is down or refused: the dead-letter tools cannot work, nothing else is affected. */
    @ExceptionHandler(AmqpException.class)
    ProblemDetail brokerUnavailable(AmqpException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "The message broker is not available");
    }
}
