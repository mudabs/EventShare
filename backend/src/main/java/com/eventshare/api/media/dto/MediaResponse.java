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
        Long sizeBytes,
        Integer width,
        Integer height,
        BigDecimal durationSeconds,
        boolean duplicate,
        Instant createdAt,
        String originalUrl,
        String thumbnailUrl,
        /*
         * Presigned GET that makes the browser save the file instead of opening it
         * (Content-Disposition: attachment). Cross-origin <a download> is ignored by
         * browsers, which is why "Download" used to open the image in a new page.
         */
        String downloadUrl,
        /*
         * True when the caller's X-Membership-Id header matches the membership that
         * uploaded this item. The guest UI uses it to decide whether to show "Delete".
         * Replaces the old client-side display-name comparison (change C2).
         */
        boolean ownedByRequester
) {
    public static MediaResponse from(Media media, String originalUrl, String thumbnailUrl) {
        return from(media, originalUrl, thumbnailUrl, null);
    }

    public static MediaResponse from(Media media, String originalUrl, String thumbnailUrl, String downloadUrl) {
        return new MediaResponse(
                media.getId(),
                media.getEventId(),
                media.getMediaType().name(),
                media.getContentType(),
                media.getOriginalFilename(),
                media.getStatus().name(),
                media.getModerationState().name(),
                media.getUploaderDisplayName(),
                media.getSizeBytes(),
                media.getWidth(),
                media.getHeight(),
                media.getDurationSeconds(),
                media.isDuplicate(),
                media.getCreatedAt(),
                originalUrl,
                thumbnailUrl,
                downloadUrl,
                false);
    }

    /** A copy with uploader identity removed (for anonymous-mode galleries). */
    public MediaResponse withoutUploader() {
        return new MediaResponse(id, eventId, mediaType, contentType, originalFilename, status,
                moderationState, null, sizeBytes, width, height, durationSeconds, duplicate, createdAt,
                originalUrl, thumbnailUrl, downloadUrl, ownedByRequester);
    }

    /** A copy with the ownership flag set for the requesting guest. */
    public MediaResponse withOwnedByRequester(boolean owned) {
        return new MediaResponse(id, eventId, mediaType, contentType, originalFilename, status,
                moderationState, uploaderDisplayName, sizeBytes, width, height, durationSeconds,
                duplicate, createdAt, originalUrl, thumbnailUrl, downloadUrl, owned);
    }
}
