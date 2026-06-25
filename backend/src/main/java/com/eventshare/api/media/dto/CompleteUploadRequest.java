package com.eventshare.api.media.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CompleteUploadRequest(
        @NotBlank
        @Pattern(regexp = "^[A-Fa-f0-9]{64}$", message = "must be a 64-character hex SHA-256")
        String sha256,
        Integer width,
        Integer height
) {
}
