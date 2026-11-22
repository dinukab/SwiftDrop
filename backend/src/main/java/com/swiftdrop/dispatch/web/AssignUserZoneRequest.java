package com.swiftdrop.dispatch.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of {@code PUT /api/v1/users/{userId}/zone}: the province code of the new zone (hub). */
record AssignUserZoneRequest(@NotBlank @Size(max = 10) String hubCode) {
}
