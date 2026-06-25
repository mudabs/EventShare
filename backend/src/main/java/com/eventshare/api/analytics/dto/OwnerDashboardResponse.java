package com.eventshare.api.analytics.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OwnerDashboardResponse(
        UUID eventId,
        long totalPhotos,
        long totalVideos,
        long uploadsToday,
        long storageUsedBytes,
        long totalGuests,
        long uniqueVisitors,
        long activeGuests,
        Instant createdAt,
        Instant expiresAt,
        List<DayCount> uploadActivity
) {
}
