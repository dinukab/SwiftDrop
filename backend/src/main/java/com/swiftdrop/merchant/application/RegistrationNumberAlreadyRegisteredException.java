package com.swiftdrop.merchant.application;

public class RegistrationNumberAlreadyRegisteredException extends RuntimeException {

    public RegistrationNumberAlreadyRegisteredException(String registrationNumber) {
        super("Registration number already registered: " + registrationNumber);
    }
}

