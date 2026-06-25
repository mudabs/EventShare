package com.eventshare.api.event.dto;

import java.util.UUID;

public record EventAnalyticsResponse(
        UUID eventId,
        long memberCount,
        long mediaTotal,
        long visibleCount,
        long hiddenCount,
        long archivedCount,
        long totalBytes
) {
}
