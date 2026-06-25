package com.eventshare.api.event.dto;

import com.eventshare.api.event.EventType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A card on the "My Events" screen. */
public record MyEventCard(
        UUID id,
        String name,
        EventType eventType,
        String coverImageUrl,
        LocalDate eventDate,
        String role,
        String status,
        String inviteCode,
        long photoCount,
        long videoCount,
        Instant lastActivityAt
) {
}
