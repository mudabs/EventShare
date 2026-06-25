package com.eventshare.api.media;

import com.eventshare.api.audit.AuditService;
import com.eventshare.api.common.error.ForbiddenException;
import com.eventshare.api.common.error.NotFoundException;
import com.eventshare.api.event.Event;
import com.eventshare.api.event.EventRepository;
import com.eventshare.api.media.dto.GalleryPageResponse;
import com.eventshare.api.media.dto.MediaResponse;
import com.eventshare.api.media.r2.R2StorageService;
import com.eventshare.api.user.User;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Owner moderation: visibility transitions, permanent purge, and the owner gallery. */
@Service
public class MediaModerationService {

    private static final int DEFAULT_PAGE_SIZE = 30;
    private static final int MAX_PAGE_SIZE = 100;

    private final MediaRepository media;
    private final EventRepository events;
    private final R2StorageService storage;
    private final AuditService audit;

    public MediaModerationService(MediaRepository media, EventRepository events,
                                  R2StorageService storage, AuditService audit) {
        this.media = media;
        this.events = events;
        this.storage = storage;
        this.audit = audit;
    }

    @Transactional
    public MediaResponse moderate(User host, UUID eventId, UUID mediaId, ModerationAction action) {
        requireOwner(host, eventId);
        Media item = media.findByIdAndEventId(mediaId, eventId)
                .orElseThrow(() -> new NotFoundException("Media not found"));

        ModerationState newState = switch (action) {
            case HIDE -> ModerationState.HIDDEN;
            case ARCHIVE -> ModerationState.ARCHIVED;
            case DELETE -> ModerationState.DELETED;
            case UNHIDE, RESTORE -> ModerationState.VISIBLE;
        };
        item.setModerationState(newState);
        Media saved = media.save(item);

        audit.record(eventId, host.getId(), host.getDisplayName(), "MEDIA_" + action.name(),
                "MEDIA", mediaId, Map.of("state", newState.name()), null);
        return toResponse(saved);
    }

    /** Irreversible: removes the R2 objects and the database row. */
    @Transactional
    public void permanentDelete(User host, UUID eventId, UUID mediaId) {
        requireOwner(host, eventId);
        Media item = media.findByIdAndEventId(mediaId, eventId)
                .orElseThrow(() -> new NotFoundException("Media not found"));
        storage.deleteObject(item.getObjectKey());
        if (item.getThumbnailKey() != null) {
            storage.deleteObject(item.getThumbnailKey());
        }
        media.delete(item);
        audit.record(eventId, host.getId(), host.getDisplayName(), "MEDIA_PURGED",
                "MEDIA", mediaId, null, null);
    }

    @Transactional(readOnly = true)
    public GalleryPageResponse ownerGallery(User host, UUID eventId, ModerationState state,
                                            String cursor, Integer requestedLimit) {
        requireOwner(host, eventId);
        int limit = clampLimit(requestedLimit);
        PageRequest page = PageRequest.of(0, limit + 1);

        List<Media> rows;
        if (cursor == null || cursor.isBlank()) {
            rows = media.findGalleryFirstPage(eventId, state, page);
        } else {
            MediaService.Cursor decoded = MediaService.Cursor.decode(cursor);
            rows = media.findGalleryAfter(eventId, state, decoded.createdAt(), decoded.id(), page);
        }

        boolean hasMore = rows.size() > limit;
        List<Media> pageRows = hasMore ? rows.subList(0, limit) : rows;

        List<MediaResponse> items = pageRows.stream().map(this::toResponse).toList();
        String nextCursor = null;
        if (hasMore && !pageRows.isEmpty()) {
            Media last = pageRows.get(pageRows.size() - 1);
            nextCursor = new MediaService.Cursor(last.getCreatedAt(), last.getId()).encode();
        }
        return new GalleryPageResponse(items, nextCursor, hasMore);
    }

    private void requireOwner(User host, UUID eventId) {
        Event event = events.findByIdAndDeletedAtIsNull(eventId)
                .orElseThrow(() -> new NotFoundException("Event not found"));
        if (!event.getHostId().equals(host.getId())) {
            throw new ForbiddenException("You do not have access to this event");
        }
    }

    private MediaResponse toResponse(Media m) {
        String originalUrl = storage.presignDownload(m.getObjectKey());
        String thumbnailUrl = m.getThumbnailKey() != null
                ? storage.presignDownload(m.getThumbnailKey()) : null;
        return MediaResponse.from(m, originalUrl, thumbnailUrl);
    }

    private int clampLimit(Integer requested) {
        if (requested == null) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.max(1, Math.min(MAX_PAGE_SIZE, requested));
    }
}
