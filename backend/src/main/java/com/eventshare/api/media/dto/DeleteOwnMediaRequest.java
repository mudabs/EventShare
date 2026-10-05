package com.eventshare.api.media.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Guest self-delete request. {@code membershipId} is the only credential that
 * authorises the delete (change C2). {@code displayName} is accepted for backwards
 * compatibility with older clients but is ignored for authorisation.
 */
public record DeleteOwnMediaRequest(
        @NotNull UUID membershipId,
        @Size(max = 120) String displayName
) {
}
