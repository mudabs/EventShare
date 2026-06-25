package com.eventshare.api.media.dto;

import java.util.UUID;

public record UploadUrlResponse(
        UUID mediaId,
        String objectKey,
        String uploadUrl,
        String httpMethod,
        String requiredContentType,
        long expiresInSeconds
) {
}
