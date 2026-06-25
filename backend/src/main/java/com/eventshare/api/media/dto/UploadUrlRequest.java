package com.eventshare.api.media.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record UploadUrlRequest(
        @NotBlank String inviteCode,
        @NotBlank @Size(max = 512) String filename,
        @NotBlank @Size(max = 128) String contentType,
        @NotNull @Positive Long sizeBytes,
        @Size(max = 120) String uploaderDisplayName,
        UUID membershipId
) {
}
