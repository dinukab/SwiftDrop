package com.swiftdrop.merchant.web;

import java.util.List;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.swiftdrop.merchant.application.ApiKeyLimitExceededException;
import com.swiftdrop.merchant.application.MerchantNotApprovedException;
import com.swiftdrop.merchant.application.MerchantNotFoundException;
import com.swiftdrop.merchant.application.RegistrationNumberAlreadyRegisteredException;

/**
 * Safe RFC 9457 ProblemDetail bodies for merchant module endpoints.
 */
@RestControllerAdvice(basePackageClasses = MerchantExceptionHandler.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class MerchantExceptionHandler {

    @ExceptionHandler(ApiKeyLimitExceededException.class)
    ProblemDetail limitExceeded(ApiKeyLimitExceededException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
    }

    @ExceptionHandler(MerchantNotFoundException.class)
    ProblemDetail notFound(MerchantNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(RegistrationNumberAlreadyRegisteredException.class)
    ProblemDetail duplicateRegistration(RegistrationNumberAlreadyRegisteredException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(MerchantNotApprovedException.class)
    ProblemDetail notApproved(MerchantNotApprovedException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail badRequest(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid request");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail typeMismatch(MethodArgumentTypeMismatchException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid request");
        problem.setProperty("invalidFields", List.of(e.getName()));
    @ExceptionHandler(PasswordRejectedException.class)
    ProblemDetail invalidPassword(PasswordRejectedException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        problem.setProperty("invalidFields", List.of("password"));
        return problem;
    }
}
