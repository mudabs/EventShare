package com.eventshare.api.admin.dto;

import com.eventshare.api.event.Event;

import java.time.Instant;
import java.util.UUID;

public record AdminEventView(
        UUID id,
        String name,
        String eventType,
        String status,
        UUID hostId,
        long mediaCount,
        Instant createdAt
) {
    public static AdminEventView from(Event e, long mediaCount) {
        return new AdminEventView(e.getId(), e.getName(), e.getEventType().name(), e.getStatus().name(),
                e.getHostId(), mediaCount, e.getCreatedAt());
    }
}
