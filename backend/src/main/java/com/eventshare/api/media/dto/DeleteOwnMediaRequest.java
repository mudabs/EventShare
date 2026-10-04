package com.eventshare.api.media.dto;

import jakarta.validation.constraints.Size;

import java.util.UUID;

public record DeleteOwnMediaRequest(
        UUID membershipId,
        @Size(max = 120) String displayName
) {
}
