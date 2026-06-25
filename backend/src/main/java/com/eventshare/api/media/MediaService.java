package com.eventshare.api.media;

import com.eventshare.api.audit.AuditService;
import com.eventshare.api.common.error.BadRequestException;
import com.eventshare.api.common.error.ForbiddenException;
import com.eventshare.api.common.error.NotFoundException;
import com.eventshare.api.common.error.TooManyRequestsException;
import com.eventshare.api.common.util.ObjectKeys;
import com.eventshare.api.common.util.RateLimiter;
import com.eventshare.api.config.AppProperties;
import com.eventshare.api.event.Event;
import com.eventshare.api.event.EventRepository;
import com.eventshare.api.event.UploaderVisibility;
import com.eventshare.api.media.dto.CompleteUploadRequest;
import com.eventshare.api.media.dto.GalleryPageResponse;
import com.eventshare.api.media.dto.MediaResponse;
import com.eventshare.api.media.dto.UploadUrlRequest;
import com.eventshare.api.media.dto.UploadUrlResponse;
import com.eventshare.api.media.messaging.MediaEventPublisher;
import com.eventshare.api.media.r2.R2StorageService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class MediaService {

    private static final int DEFAULT_PAGE_SIZE = 30;
    private static final int MAX_PAGE_SIZE = 100;

    private final MediaRepository media;
    private final EventRepository events;
    private final R2StorageService storage;
    private final MediaEventPublisher publisher;
    private final AuditService audit;
    private final RateLimiter rateLimiter;
    private final AppProperties props;
    private final Set<String> allowedContentTypes;
    private final MeterRegistry meterRegistry;

    public MediaService(MediaRepository media,
                        EventRepository events,
                        R2StorageService storage,
                        MediaEventPublisher publisher,
                        AuditService audit,
                        RateLimiter rateLimiter,
                        AppProperties props,
                        MeterRegistry meterRegistry) {
        this.media = media;
        this.events = events;
        this.storage = storage;
        this.publisher = publisher;
        this.audit = audit;
        this.rateLimiter = rateLimiter;
        this.props = props;
        this.meterRegistry = meterRegistry;
        this.allowedContentTypes = Arrays.stream(props.media().allowedContentTypes().split(","))
                .map(s -> s.trim().toLowerCase())
                .filter(s -> !s.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Validates the request and reserves a media row in PENDING state, returning a
     * short-lived presigned PUT URL the client uses to upload directly to R2.
     */
    @Transactional
    public UploadUrlResponse requestUploadUrl(UploadUrlRequest request, String clientIp) {
        if (!rateLimiter.tryAcquire("upload:" + clientIp, props.ratelimit().uploadRequestsPerMinute())) {
            throw new TooManyRequestsException("Too many uploads. Please slow down.");
        }

        String contentType = request.contentType().toLowerCase();
        if (!allowedContentTypes.contains(contentType)) {
            throw new BadRequestException("Unsupported content type: " + request.contentType());
        }

        Event event = loadActiveEvent(request.inviteCode());

        long maxBytes = event.getMaxUploadBytes() != null
                ? event.getMaxUploadBytes()
                : props.media().maxUploadBytes();
        if (request.sizeBytes() > maxBytes) {
            throw new BadRequestException("File exceeds the maximum allowed size of " + maxBytes + " bytes");
        }

        UUID mediaId = UUID.randomUUID();
        String objectKey = ObjectKeys.original(event.getId(), mediaId, request.filename());

        Media entity = new Media();
        entity.setId(mediaId);
        entity.setEventId(event.getId());
        entity.setUploaderMembershipId(request.membershipId());
        entity.setUploaderDisplayName(trimToNull(request.uploaderDisplayName()));
        entity.setOriginalFilename(request.filename());
        entity.setContentType(contentType);
        entity.setMediaType(MediaType.fromContentType(contentType));
        entity.setSizeBytes(request.sizeBytes());
        entity.setObjectKey(objectKey);
        entity.setStatus(MediaStatus.PENDING);
        entity.setModerationState(event.isAutoApprove() ? ModerationState.VISIBLE : ModerationState.HIDDEN);
        media.save(entity);

        String uploadUrl = storage.presignUpload(objectKey, contentType);
        return new UploadUrlResponse(mediaId, objectKey, uploadUrl, "PUT",
                contentType, storage.uploadTtlSeconds());
    }

    /**
     * Confirms an upload: verifies the object exists in R2, records hash/size,
     * runs exact duplicate detection, then publishes the async processing event.
     * Idempotent: re-calling after completion returns the current state.
     */
    @Transactional
    public MediaResponse completeUpload(UUID mediaId, CompleteUploadRequest request, String clientIp) {
        Media entity = media.findById(mediaId)
                .orElseThrow(() -> new NotFoundException("Media not found"));

        if (entity.getStatus() != MediaStatus.PENDING) {
            return toResponse(entity);
        }

        var head = storage.headObject(entity.getObjectKey())
                .orElseThrow(() -> new BadRequestException(
                        "Upload was not found in storage. Re-upload and try again."));
        if (head.contentLength() != null) {
            entity.setSizeBytes(head.contentLength());
        }

        String sha256 = request.sha256().toLowerCase();
        entity.setSha256(sha256);
        entity.setWidth(request.width());
        entity.setHeight(request.height());
        entity.setStatus(MediaStatus.UPLOADED);

        media.findFirstByEventIdAndSha256OrderByCreatedAtAscIdAsc(entity.getEventId(), sha256)
                .filter(existing -> !existing.getId().equals(entity.getId()))
                .ifPresent(original -> {
                    entity.setDuplicate(true);
                    entity.setDuplicateOfId(original.getId());
                });

        Media saved = media.save(entity);
        publisher.publishUploaded(saved);

        audit.record(saved.getEventId(), saved.getUploaderUserId(), saved.getUploaderDisplayName(),
                "MEDIA_UPLOADED", "MEDIA", saved.getId(),
                Map.of("mediaType", saved.getMediaType().name(),
                        "sizeBytes", saved.getSizeBytes() == null ? 0 : saved.getSizeBytes(),
                        "duplicate", saved.isDuplicate()),
                clientIp);

        Counter.builder("eventshare.media.uploaded")
                .tag("mediaType", saved.getMediaType().name())
                .register(meterRegistry)
                .increment();

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public GalleryPageResponse gallery(String inviteCode, String cursor, Integer requestedLimit) {
        int limit = clampLimit(requestedLimit);
        Event event = loadActiveEvent(inviteCode);
        PageRequest page = PageRequest.of(0, limit + 1);

        List<Media> rows;
        if (cursor == null || cursor.isBlank()) {
            rows = media.findGalleryFirstPage(event.getId(), ModerationState.VISIBLE, page);
        } else {
            Cursor decoded = Cursor.decode(cursor);
            rows = media.findGalleryAfter(event.getId(), ModerationState.VISIBLE,
                    decoded.createdAt(), decoded.id(), page);
        }

        boolean hasMore = rows.size() > limit;
        List<Media> pageRows = hasMore ? rows.subList(0, limit) : rows;

        boolean hideUploader = event.getUploaderVisibility() == UploaderVisibility.ANONYMOUS
                || !event.isShowUploaderNames();
        List<MediaResponse> items = new ArrayList<>(pageRows.size());
        for (Media m : pageRows) {
            MediaResponse response = toResponse(m);
            items.add(hideUploader ? response.withoutUploader() : response);
        }

        String nextCursor = null;
        if (hasMore && !pageRows.isEmpty()) {
            Media last = pageRows.get(pageRows.size() - 1);
            nextCursor = new Cursor(last.getCreatedAt(), last.getId()).encode();
        }
        return new GalleryPageResponse(items, nextCursor, hasMore);
    }

    // ---- helpers ----

    private Event loadActiveEvent(String inviteCode) {
        Event event = events.findByInviteCodeAndDeletedAtIsNull(inviteCode)
                .orElseThrow(() -> new NotFoundException("Event not found"));
        if (!event.isActive()) {
            throw new ForbiddenException("This event is not currently accepting uploads");
        }
        return event;
    }

    private MediaResponse toResponse(Media m) {
        String originalUrl = storage.presignDownload(m.getObjectKey());
        String thumbnailUrl = m.getThumbnailKey() != null
                ? storage.presignDownload(m.getThumbnailKey())
                : null;
        return MediaResponse.from(m, originalUrl, thumbnailUrl);
    }

    private int clampLimit(Integer requested) {
        if (requested == null) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.max(1, Math.min(MAX_PAGE_SIZE, requested));
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** Opaque keyset cursor: base64url("epochMillis:uuid"). */
    record Cursor(Instant createdAt, UUID id) {
        String encode() {
            String raw = createdAt.toEpochMilli() + ":" + id;
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        }

        static Cursor decode(String value) {
            try {
                String raw = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
                int sep = raw.indexOf(':');
                long epochMillis = Long.parseLong(raw.substring(0, sep));
                UUID id = UUID.fromString(raw.substring(sep + 1));
                return new Cursor(Instant.ofEpochMilli(epochMillis), id);
            } catch (RuntimeException ex) {
                throw new BadRequestException("Invalid pagination cursor");
            }
        }
    }
}
