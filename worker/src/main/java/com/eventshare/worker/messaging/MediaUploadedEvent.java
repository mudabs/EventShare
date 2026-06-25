package com.eventshare.worker.messaging;

import java.util.UUID;

/** Mirror of the API message contract consumed from RabbitMQ. */
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
