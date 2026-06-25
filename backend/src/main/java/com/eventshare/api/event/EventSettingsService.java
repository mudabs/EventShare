package com.eventshare.api.event;

import com.eventshare.api.audit.AuditService;
import com.eventshare.api.common.error.ForbiddenException;
import com.eventshare.api.common.error.NotFoundException;
import com.eventshare.api.event.dto.EventSettingsResponse;
import com.eventshare.api.event.dto.UpdateEventSettingsRequest;
import com.eventshare.api.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class EventSettingsService {

    private final EventRepository events;
    private final AuditService audit;

    public EventSettingsService(EventRepository events, AuditService audit) {
        this.events = events;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public EventSettingsResponse getSettings(User host, UUID eventId) {
        return EventSettingsResponse.from(loadOwned(host, eventId));
    }

    @Transactional
    public EventSettingsResponse updateSettings(User host, UUID eventId, UpdateEventSettingsRequest request) {
        Event event = loadOwned(host, eventId);
        if (request.name() != null && !request.name().isBlank()) {
            event.setName(request.name().trim());
        }
        if (request.eventDate() != null) {
            event.setEventDate(request.eventDate());
        }
        if (request.uploaderVisibility() != null) {
            event.setUploaderVisibility(request.uploaderVisibility());
        }
        if (request.showUploadTimestamps() != null) {
            event.setShowUploadTimestamps(request.showUploadTimestamps());
        }
        if (request.showUploaderNames() != null) {
            event.setShowUploaderNames(request.showUploaderNames());
        }
        if (request.showUploadStats() != null) {
            event.setShowUploadStats(request.showUploadStats());
        }
        if (request.coverMediaId() != null) {
            event.setCoverMediaId(request.coverMediaId());
        }
        Event saved = events.save(event);
        audit.record(eventId, host.getId(), host.getDisplayName(), "EVENT_SETTINGS_UPDATED",
                "EVENT", eventId, null, null);
        return EventSettingsResponse.from(saved);
    }

    private Event loadOwned(User host, UUID eventId) {
        Event event = events.findByIdAndDeletedAtIsNull(eventId)
                .orElseThrow(() -> new NotFoundException("Event not found"));
        if (!event.getHostId().equals(host.getId())) {
            throw new ForbiddenException("You do not have access to this event");
        }
        return event;
    }
}
