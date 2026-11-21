package com.swiftdrop.delivery.application;

/** A report date range that is reversed or too long; the message is safe to show to the caller. */
public class InvalidReportRangeException extends RuntimeException {

    public InvalidReportRangeException(String message) {
        super(message);
    }
}
