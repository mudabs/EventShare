package com.eventshare.api.media.messaging;

import java.util.UUID;

/**
 * Message contract published when an upload completes. The worker consumes this
 * to generate thumbnails and extract metadata. Kept as a flat record so it
 * serialises cleanly to JSON and stays stable across both services.
 */
public record MediaUploadedEvent(
        UUID mediaId,
        UUID eventId,
        String objectKey,
        String contentType,
        String mediaType,
        Long sizeBytes,
        String sha256,
        String originalFilename
) {
}
