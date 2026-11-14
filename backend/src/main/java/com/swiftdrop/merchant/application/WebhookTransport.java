package com.swiftdrop.merchant.application;

import java.net.URI;
import java.util.Map;

/**
 * Sends one webhook POST and reports only the HTTP status. The implementation enforces the network
 * rules (timeouts, no redirects, public addresses); the sender decides what a status means.
 */
public interface WebhookTransport {

    /**
     * @param httpStatus the response status, or {@code null} when no response arrived (connection
     *        refused, timeout, address refused)
     */
    record Result(Integer httpStatus) {
    }

    Result post(URI uri, Map<String, String> headers, String body);
}
