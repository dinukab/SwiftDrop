package com.swiftdrop.identity.application;

import com.swiftdrop.identity.api.PasswordRejectedException;

/** Identity-internal password rejection; also a {@link PasswordRejectedException} for other modules. */
public class InvalidPasswordException extends PasswordRejectedException {

    InvalidPasswordException(String message) {
        super(message);
    }
}
