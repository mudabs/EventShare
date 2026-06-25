package com.eventshare.api.event.dto;

import com.eventshare.api.event.UploaderVisibility;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/** All fields optional (PATCH semantics); only non-null values are applied. */
public record UpdateEventSettingsRequest(
        @Size(max = 200) String name,
        LocalDate eventDate,
        UploaderVisibility uploaderVisibility,
        Boolean showUploadTimestamps,
        Boolean showUploaderNames,
        Boolean showUploadStats,
        UUID coverMediaId
) {
}
