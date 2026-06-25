package com.eventshare.api.event.dto;

import com.eventshare.api.event.Event;
import com.eventshare.api.event.EventStatus;
import com.eventshare.api.event.EventType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record EventResponse(
        UUID id,
        String name,
        String description,
        EventType eventType,
        EventStatus status,
        String inviteCode,
        String inviteUrl,
        LocalDate eventDate,
        boolean allowGuestDownloads,
        boolean autoApprove,
        Instant createdAt
) {
    public static EventResponse from(Event event, String inviteUrl) {
        return new EventResponse(
                event.getId(),
                event.getName(),
                event.getDescription(),
                event.getEventType(),
                event.getStatus(),
                event.getInviteCode(),
                inviteUrl,
                event.getEventDate(),
                event.isAllowGuestDownloads(),
                event.isAutoApprove(),
                event.getCreatedAt());
    }
}
