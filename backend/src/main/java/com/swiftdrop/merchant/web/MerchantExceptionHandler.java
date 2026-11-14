package com.swiftdrop.merchant.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.swiftdrop.identity.api.PasswordRejectedException;
import com.swiftdrop.merchant.application.MerchantConflictException;
import com.swiftdrop.merchant.application.MerchantNotApprovedException;

/**
 * Safe RFC 9457 error bodies for merchant endpoints. Rejected field values are never echoed.
 */
@RestControllerAdvice(basePackageClasses = MerchantExceptionHandler.class)
class MerchantExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail invalidRequest(MethodArgumentNotValidException e) {
        List<String> fields = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getField).distinct().sorted().toList();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid request");
        problem.setProperty("invalidFields", fields);
        return problem;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail unreadable() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Malformed request body");
    }

    @ExceptionHandler(MerchantConflictException.class)
    ProblemDetail conflict(MerchantConflictException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(MerchantNotApprovedException.class)
    ProblemDetail notApproved(MerchantNotApprovedException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler(PasswordRejectedException.class)
    ProblemDetail invalidPassword(PasswordRejectedException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        problem.setProperty("invalidFields", List.of("password"));
        return problem;
    }
}
