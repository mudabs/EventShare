package com.eventshare.api.media.dto;

import com.eventshare.api.media.Media;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record MediaResponse(
        UUID id,
        UUID eventId,
        String mediaType,
        String contentType,
        String originalFilename,
        String status,
        String moderationState,
        String uploaderDisplayName,
        Integer width,
        Integer height,
        BigDecimal durationSeconds,
        boolean duplicate,
        Instant createdAt,
        String originalUrl,
        String thumbnailUrl
) {
    public static MediaResponse from(Media media, String originalUrl, String thumbnailUrl) {
        return new MediaResponse(
                media.getId(),
                media.getEventId(),
                media.getMediaType().name(),
                media.getContentType(),
                media.getOriginalFilename(),
                media.getStatus().name(),
                media.getModerationState().name(),
                media.getUploaderDisplayName(),
                media.getWidth(),
                media.getHeight(),
                media.getDurationSeconds(),
                media.isDuplicate(),
                media.getCreatedAt(),
                originalUrl,
                thumbnailUrl);
    }

    /** A copy with uploader identity removed (for anonymous-mode galleries). */
    public MediaResponse withoutUploader() {
        return new MediaResponse(id, eventId, mediaType, contentType, originalFilename, status,
                moderationState, null, width, height, durationSeconds, duplicate, createdAt,
                originalUrl, thumbnailUrl);
    }
}
