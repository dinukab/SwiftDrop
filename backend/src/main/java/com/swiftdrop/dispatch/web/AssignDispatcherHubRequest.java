package com.swiftdrop.dispatch.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of {@code PUT /api/v1/dispatchers/{userId}/hub}: the province code of the hub. */
record AssignDispatcherHubRequest(@NotBlank @Size(max = 10) String hubCode) {
}
