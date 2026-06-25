package com.eventshare.api.event.dto;

import com.eventshare.api.event.Event;

import java.time.LocalDate;
import java.util.UUID;

public record EventSettingsResponse(
        UUID eventId,
        String name,
        LocalDate eventDate,
        String uploaderVisibility,
        boolean showUploadTimestamps,
        boolean showUploaderNames,
        boolean showUploadStats,
        UUID coverMediaId,
        String status
) {
    public static EventSettingsResponse from(Event e) {
        return new EventSettingsResponse(
                e.getId(), e.getName(), e.getEventDate(), e.getUploaderVisibility().name(),
                e.isShowUploadTimestamps(), e.isShowUploaderNames(), e.isShowUploadStats(),
                e.getCoverMediaId(), e.getStatus().name());
    }
}
