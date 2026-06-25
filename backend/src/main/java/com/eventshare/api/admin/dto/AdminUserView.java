package com.eventshare.api.admin.dto;

import com.eventshare.api.user.User;

import java.time.Instant;
import java.util.UUID;

public record AdminUserView(
        UUID id,
        String email,
        String displayName,
        String role,
        boolean disabled,
        String planCode,
        Instant createdAt,
        Instant lastSeenAt
) {
    public static AdminUserView from(User u, String planCode) {
        return new AdminUserView(u.getId(), u.getEmail(), u.getDisplayName(), u.getRole().name(),
                u.isDisabled(), planCode, u.getCreatedAt(), u.getLastSeenAt());
    }
}
