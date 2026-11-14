package com.swiftdrop.identity.application;

import com.swiftdrop.identity.api.EmailTakenException;

/** Identity-internal duplicate-email signal; also an {@link EmailTakenException} for other modules. */
public class EmailAlreadyRegisteredException extends EmailTakenException {

    EmailAlreadyRegisteredException() {
        super();
    }
}
