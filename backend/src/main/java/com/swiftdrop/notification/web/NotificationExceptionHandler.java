package com.swiftdrop.notification.web;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.swiftdrop.notification.application.NotificationNotFoundException;

/** Safe RFC 9457 bodies for notification endpoints; nothing about other users' messages is echoed. */
@RestControllerAdvice(basePackageClasses = NotificationExceptionHandler.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class NotificationExceptionHandler {

    @ExceptionHandler(NotificationNotFoundException.class)
    ProblemDetail notFound(NotificationNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }
}
