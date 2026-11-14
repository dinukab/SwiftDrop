package com.swiftdrop.identity.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Admin rejection of a pending courier registration. */
public record RejectCourierRequest(
        @NotBlank @Size(max = 500) String reason) {

    public RejectCourierRequest {
        reason = reason == null ? null : reason.trim();
    }
}
