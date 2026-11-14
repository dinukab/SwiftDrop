package com.swiftdrop.identity.web;

import java.util.List;
import java.util.UUID;

import com.swiftdrop.identity.domain.User;

public record UserResponse(UUID id, String email, String fullName, List<String> roles, boolean enabled,
        String courierApprovalStatus) {

    public UserResponse(UUID id, String email, String fullName, List<String> roles) {
        this(id, email, fullName, roles, true, null);
    }

    static UserResponse from(User user) {
        return from(user, null);
    }

    static UserResponse from(User user, String courierApprovalStatus) {
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(),
                user.getRoles().stream().map(Enum::name).sorted().toList(), user.isEnabled(),
                courierApprovalStatus);
    }
}
