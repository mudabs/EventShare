package com.eventshare.api.event.dto;

import com.eventshare.api.event.Event;
import com.eventshare.api.event.EventType;
import com.eventshare.api.event.UploaderVisibility;

/** Minimal, non-sensitive view shown on the public join/gallery page. */
public record PublicEventResponse(
        String name,
        EventType eventType,
        boolean active,
        boolean allowGuestDownloads,
        boolean showUploaderNames,
        boolean showUploadTimestamps,
        boolean anonymous,
        String coverImageUrl,
        /* Host's plan includes ZIP downloads (paid plans). Drives the guest download UI. */
        boolean zipDownloads
) {
    public static PublicEventResponse from(Event event, String coverImageUrl) {
        return from(event, coverImageUrl, false);
    }

    public static PublicEventResponse from(Event event, String coverImageUrl, boolean zipDownloads) {
        boolean anon = event.getUploaderVisibility() == UploaderVisibility.ANONYMOUS;
        return new PublicEventResponse(
                event.getName(),
                event.getEventType(),
                event.isActive(),
                event.isAllowGuestDownloads(),
                event.isShowUploaderNames() && !anon,
                event.isShowUploadTimestamps(),
                anon,
                coverImageUrl,
                zipDownloads);
    }
}
