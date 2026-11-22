package com.swiftdrop.assistant.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** One chat message from the customer (assistant contract). */
record ChatRequest(@NotBlank @Size(max = 1000) String message) {
}
