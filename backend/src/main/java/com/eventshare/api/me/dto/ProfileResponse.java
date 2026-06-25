package com.eventshare.api.me.dto;

import com.eventshare.api.user.User;

import java.util.UUID;

public record ProfileResponse(UUID id, String email, String displayName, String role, boolean disabled) {
    public static ProfileResponse from(User u) {
        return new ProfileResponse(u.getId(), u.getEmail(), u.getDisplayName(), u.getRole().name(), u.isDisabled());
    }
}
