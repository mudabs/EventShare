package com.eventshare.api.event;

import com.eventshare.api.audit.AuditService;
import com.eventshare.api.common.error.ForbiddenException;
import com.eventshare.api.common.error.NotFoundException;
import com.eventshare.api.demo.DemoGuard;
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
    private final DemoGuard demoGuard;

    public EventSettingsService(EventRepository events, AuditService audit, DemoGuard demoGuard) {
        this.events = events;
        this.audit = audit;
        this.demoGuard = demoGuard;
    }

    @Transactional(readOnly = true)
    public EventSettingsResponse getSettings(User host, UUID eventId) {
        return EventSettingsResponse.from(loadOwned(host, eventId));
    }

    @Transactional
    public EventSettingsResponse updateSettings(User host, UUID eventId, UpdateEventSettingsRequest request) {
        Event event = loadOwned(host, eventId);
        // DG7: name and cover are public on the guest page; locked on the demo events.
        boolean changesName = request.name() != null && !request.name().isBlank()
                && !request.name().trim().equals(event.getName());
        boolean changesCover = request.coverMediaId() != null && !request.coverMediaId().equals(event.getCoverMediaId());
        demoGuard.assertCanEditIdentity(event, changesName, changesCover);
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
        demoGuard.assertCanManage(host, event);
        return event;
    }
}
