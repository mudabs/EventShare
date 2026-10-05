package com.eventshare.api.media;

import com.eventshare.api.audit.AuditService;
import com.eventshare.api.common.error.BadRequestException;
import com.eventshare.api.common.error.ForbiddenException;
import com.eventshare.api.common.error.NotFoundException;
import com.eventshare.api.common.error.TooManyRequestsException;
import com.eventshare.api.common.error.UploadRejectedException;
import com.eventshare.api.common.util.ObjectKeys;
import com.eventshare.api.common.util.RateLimiter;
import com.eventshare.api.config.AppProperties;
import com.eventshare.api.event.Event;
import com.eventshare.api.event.EventMembership;
import com.eventshare.api.event.EventMembershipRepository;
import com.eventshare.api.event.EventRepository;
import com.eventshare.api.event.MembershipStatus;
import com.eventshare.api.event.UploaderVisibility;
import com.eventshare.api.media.dto.CompleteUploadRequest;
import com.eventshare.api.media.dto.DeleteOwnMediaRequest;
import com.eventshare.api.media.dto.GalleryPageResponse;
import com.eventshare.api.media.dto.MediaResponse;
import com.eventshare.api.media.dto.UploadUrlRequest;
import com.eventshare.api.media.dto.UploadUrlResponse;
import com.eventshare.api.media.r2.R2StorageService;
import com.eventshare.api.subscription.PlanLimitService;
import com.eventshare.api.user.UserRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(MediaService.class);

    private static final int DEFAULT_PAGE_SIZE = 30;
    private static final int MAX_PAGE_SIZE = 100;

    private final MediaRepository media;
    private final EventRepository events;
    private final EventMembershipRepository memberships;
    private final R2StorageService storage;
    private final AuditService audit;
    private final RateLimiter rateLimiter;
    private final AppProperties props;
    private final Set<String> allowedContentTypes;
    private final MeterRegistry meterRegistry;
    private final UserRepository users;
    private final PlanLimitService planLimits;

    public MediaService(MediaRepository media,
                        EventRepository events,
                        EventMembershipRepository memberships,
                        R2StorageService storage,
                        AuditService audit,
                        RateLimiter rateLimiter,
                        AppProperties props,
                        MeterRegistry meterRegistry,
                        UserRepository users,
                        PlanLimitService planLimits) {
        this.users = users;
        this.planLimits = planLimits;
        this.media = media;
        this.events = events;
        this.memberships = memberships;
        this.storage = storage;
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
     *
     * <p>Hardening (docs/changes/2026-10-05-security-and-correctness-hardening.md):
     * <ul>
     *   <li>C3: the event host's user row is locked FOR UPDATE before the plan-limit
     *       check, and the check runs in this transaction, so parallel requests cannot
     *       all pass the same "count &lt; limit" test. PENDING rows count toward the
     *       quota, so a reservation holds its slot until it is completed or rejected.</li>
     *   <li>C4: a supplied membershipId must be an ACTIVE membership of this event, and
     *       the stored uploader name comes from that membership, not the request.</li>
     *   <li>C1: the presigned PUT signs the declared size.</li>
     * </ul>
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

        String uploaderName = trimToNull(request.uploaderDisplayName());
        if (request.membershipId() != null) {
            EventMembership membership = memberships
                    .findByIdAndEventIdAndStatus(request.membershipId(), event.getId(), MembershipStatus.ACTIVE)
                    .orElseThrow(() -> new ForbiddenException(
                            "Your guest session for this event is no longer valid. Please rejoin the event."));
            if (trimToNull(membership.getGuestDisplayName()) != null) {
                uploaderName = membership.getGuestDisplayName().trim();
            }
        }

        MediaType mediaType = MediaType.fromContentType(contentType);
        if (event.getHostId() != null) {
            users.findByIdForUpdate(event.getHostId());
        }
        planLimits.checkCanUpload(event, mediaType, request.sizeBytes());

        UUID mediaId = UUID.randomUUID();
        String objectKey = ObjectKeys.original(event.getId(), mediaId, request.filename());

        Media entity = new Media();
        entity.setId(mediaId);
        entity.setEventId(event.getId());
        entity.setUploaderMembershipId(request.membershipId());
        entity.setUploaderDisplayName(uploaderName);
        entity.setOriginalFilename(request.filename());
        entity.setContentType(contentType);
        entity.setMediaType(mediaType);
        entity.setSizeBytes(request.sizeBytes());
        entity.setObjectKey(objectKey);
        entity.setStatus(MediaStatus.PENDING);
        entity.setModerationState(event.isAutoApprove() ? ModerationState.VISIBLE : ModerationState.HIDDEN);
        media.save(entity);

        String uploadUrl = storage.presignUpload(objectKey, contentType, request.sizeBytes());
        return new UploadUrlResponse(mediaId, objectKey, uploadUrl, "PUT",
                contentType, storage.uploadTtlSeconds());
    }

    /**
     * Confirms an upload: verifies the object exists in R2, records hash/size, and
     * runs exact duplicate detection. Leaving the row in UPLOADED enqueues it for the
     * in-process media processor (thumbnail/poster generation), which polls by status.
     * Idempotent: re-calling after completion returns the current state.
     *
     * <p>C1 defence in depth: if the stored object is larger than the size declared when
     * the URL was issued, the object is deleted, the row is marked FAILED/DELETED (so it
     * stops counting toward quota) and {@link UploadRejectedException} is thrown. The
     * transaction does not roll back on that exception so the rejection is durable.
     */
    @Transactional(noRollbackFor = UploadRejectedException.class)
    public MediaResponse completeUpload(UUID mediaId, CompleteUploadRequest request, String clientIp) {
        Media entity = media.findById(mediaId)
                .orElseThrow(() -> new NotFoundException("Media not found"));

        if (entity.getStatus() != MediaStatus.PENDING) {
            return toResponse(entity);
        }

        var head = storage.headObject(entity.getObjectKey())
                .orElseThrow(() -> new BadRequestException(
                        "Upload was not found in storage. Re-upload and try again."));
        Long declaredSize = entity.getSizeBytes();
        Long actualSize = head.contentLength();
        if (declaredSize != null && actualSize != null && actualSize > declaredSize) {
            rejectOversizeUpload(entity, declaredSize, actualSize, clientIp);
        }
        if (actualSize != null) {
            entity.setSizeBytes(actualSize);
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
        // No message published: the row is now UPLOADED and the in-process scheduler
        // (MediaProcessingScheduler) will pick it up on its next poll.

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

    /** Backwards-compatible overload: gallery without a requesting guest identity. */
    @Transactional(readOnly = true)
    public GalleryPageResponse gallery(String inviteCode, String cursor, Integer requestedLimit) {
        return gallery(inviteCode, cursor, requestedLimit, null);
    }

    /**
     * Shared gallery page. When {@code requesterMembershipId} is supplied (guest UI sends
     * it as the X-Membership-Id header) each item carries {@code ownedByRequester}, which
     * drives the Delete button. The membership id itself is never returned (C2).
     */
    @Transactional(readOnly = true)
    public GalleryPageResponse gallery(String inviteCode, String cursor, Integer requestedLimit,
                                       UUID requesterMembershipId) {
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
            boolean owned = requesterMembershipId != null
                    && requesterMembershipId.equals(m.getUploaderMembershipId());
            MediaResponse response = toResponse(m).withOwnedByRequester(owned);
            items.add(hideUploader ? response.withoutUploader() : response);
        }

        String nextCursor = null;
        if (hasMore && !pageRows.isEmpty()) {
            Media last = pageRows.get(pageRows.size() - 1);
            nextCursor = new Cursor(last.getCreatedAt(), last.getId()).encode();
        }
        return new GalleryPageResponse(items, nextCursor, hasMore);
    }

    /**
     * Lets a guest soft-delete media they uploaded.
     *
     * <p>C2: authorisation is the uploader's membership id only. The old fallback that
     * accepted a matching display name was removed: names are public in the gallery, so
     * any guest could delete anyone's photos by typing the uploader's name. Media
     * uploaded without a membership can only be removed by the host via moderation.
     */
    @Transactional
    public void deleteOwnMedia(String inviteCode, UUID mediaId, DeleteOwnMediaRequest request, String clientIp) {
        Event event = events.findByInviteCodeAndDeletedAtIsNull(inviteCode)
                .orElseThrow(() -> new NotFoundException("Event not found"));

        Media entity = media.findByIdAndEventId(mediaId, event.getId())
                .orElseThrow(() -> new NotFoundException("Media not found"));

        if (entity.getUploaderMembershipId() == null
                || !entity.getUploaderMembershipId().equals(request.membershipId())) {
            throw new ForbiddenException("Only the uploader can delete this media");
        }
        EventMembership membership = memberships
                .findByIdAndEventIdAndStatus(request.membershipId(), event.getId(), MembershipStatus.ACTIVE)
                .orElseThrow(() -> new ForbiddenException("Uploader membership is no longer active"));

        entity.setModerationState(ModerationState.DELETED);
        media.save(entity);

        String actorLabel = trimToNull(membership.getGuestDisplayName());
        audit.record(entity.getEventId(), null, actorLabel,
                "MEDIA_SELF_DELETED", "MEDIA", entity.getId(),
                Map.of("mediaType", entity.getMediaType().name(),
                        "sizeBytes", entity.getSizeBytes() == null ? 0 : entity.getSizeBytes()),
                clientIp);
    }

    private void rejectOversizeUpload(Media entity, long declaredSize, long actualSize, String clientIp) {
        try {
            storage.deleteObject(entity.getObjectKey());
        } catch (RuntimeException e) {
            // Row is still marked DELETED below; an orphaned object can be swept later.
            log.warn("Could not delete oversize object {}: {}", entity.getObjectKey(), e.getMessage());
        }
        entity.setSizeBytes(actualSize);
        entity.setStatus(MediaStatus.FAILED);
        entity.setModerationState(ModerationState.DELETED);
        media.save(entity);

        audit.record(entity.getEventId(), entity.getUploaderUserId(), entity.getUploaderDisplayName(),
                "MEDIA_UPLOAD_REJECTED", "MEDIA", entity.getId(),
                Map.of("reason", "SIZE_MISMATCH",
                        "declaredBytes", declaredSize,
                        "actualBytes", actualSize),
                clientIp);
        meterRegistry.counter("eventshare.media.upload.rejected", "reason", "size_mismatch").increment();

        throw new UploadRejectedException(
                "Uploaded file is larger than the size declared when the upload started.");
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
