package com.eventshare.api.media;

import com.eventshare.api.audit.AuditService;
import com.eventshare.api.common.error.BadRequestException;
import com.eventshare.api.common.error.ForbiddenException;
import com.eventshare.api.common.error.QuotaExceededException;
import com.eventshare.api.common.error.TooManyRequestsException;
import com.eventshare.api.common.error.UploadRejectedException;
import com.eventshare.api.config.AppProperties;
import com.eventshare.api.demo.DemoGuard;
import com.eventshare.api.event.Event;
import com.eventshare.api.event.EventMembershipRepository;
import com.eventshare.api.event.EventRepository;
import com.eventshare.api.event.EventMembership;
import com.eventshare.api.event.EventStatus;
import com.eventshare.api.event.MembershipStatus;
import com.eventshare.api.media.dto.CompleteUploadRequest;
import com.eventshare.api.media.dto.DeleteOwnMediaRequest;
import com.eventshare.api.media.dto.GalleryPageResponse;
import com.eventshare.api.media.dto.MediaResponse;
import com.eventshare.api.media.dto.UploadUrlRequest;
import com.eventshare.api.media.dto.UploadUrlResponse;
import com.eventshare.api.media.r2.R2StorageService;
import com.eventshare.api.subscription.PlanLimitService;
import com.eventshare.api.user.UserRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MediaServiceTest {

    @Mock MediaRepository media;
    @Mock EventRepository events;
    @Mock EventMembershipRepository memberships;
    @Mock R2StorageService storage;
    @Mock AuditService audit;
    @Mock com.eventshare.api.common.util.RateLimiter rateLimiter;
    @Mock UserRepository users;
    @Mock PlanLimitService planLimits;
    @Mock DemoGuard demoGuard;

    MediaService service;

    private static AppProperties props() {
        return new AppProperties(
                "http://localhost:3000",
                new AppProperties.Cors("http://localhost:3000"),
                new AppProperties.Auth("", ""),
                new AppProperties.R2("http://r2", "auto", "k", "s", "bucket", 900, 3600),
                new AppProperties.Media(1000L, "image/jpeg,video/mp4"),
                new AppProperties.Processing(true, 600, "00:00:01.000", 3, 300),
                new AppProperties.RateLimit(60, 30));
    }

    private Event activeEvent(boolean autoApprove) {
        Event event = new Event();
        event.setId(UUID.randomUUID());
        event.setHostId(UUID.randomUUID());
        event.setInviteCode("CODE123456");
        event.setStatus(EventStatus.ACTIVE);
        event.setAutoApprove(autoApprove);
        return event;
    }

    @BeforeEach
    void setUp() {
        service = new MediaService(media, events, memberships, storage, audit, rateLimiter, props(),
                new SimpleMeterRegistry(), users, planLimits, demoGuard);
        when(rateLimiter.tryAcquire(any(), anyInt())).thenReturn(true);
        when(media.save(any(Media.class))).thenAnswer(i -> i.getArgument(0));
        when(storage.presignDownload(anyString())).thenReturn("https://r2.example/dl");
        when(storage.uploadTtlSeconds()).thenReturn(900L);
    }

    @Test
    void requestUploadUrlRejectsDisallowedContentType() {
        UploadUrlRequest request = new UploadUrlRequest(
                "CODE123456", "x.gif", "image/gif", 10L, "Guest", null);
        assertThatThrownBy(() -> service.requestUploadUrl(request, "203.0.113.1"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void requestUploadUrlRejectsOversizeFile() {
        when(events.findByInviteCodeAndDeletedAtIsNull("CODE123456"))
                .thenReturn(Optional.of(activeEvent(true)));
        UploadUrlRequest request = new UploadUrlRequest(
                "CODE123456", "big.jpg", "image/jpeg", 5000L, "Guest", null);
        assertThatThrownBy(() -> service.requestUploadUrl(request, "203.0.113.1"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void requestUploadUrlIsRateLimited() {
        when(rateLimiter.tryAcquire(any(), anyInt())).thenReturn(false);
        UploadUrlRequest request = new UploadUrlRequest(
                "CODE123456", "x.jpg", "image/jpeg", 10L, "Guest", null);
        assertThatThrownBy(() -> service.requestUploadUrl(request, "203.0.113.1"))
                .isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void requestUploadUrlReservesPendingMediaAndPresigns() {
        Event event = activeEvent(true);
        when(events.findByInviteCodeAndDeletedAtIsNull("CODE123456")).thenReturn(Optional.of(event));
        when(storage.presignUpload(anyString(), anyString(), anyLong())).thenReturn("https://r2.example/put");

        UploadUrlRequest request = new UploadUrlRequest(
                "CODE123456", "sunset.jpg", "image/jpeg", 500L, "Guest", null);
        UploadUrlResponse response = service.requestUploadUrl(request, "203.0.113.1");

        assertThat(response.uploadUrl()).isEqualTo("https://r2.example/put");
        assertThat(response.requiredContentType()).isEqualTo("image/jpeg");
        assertThat(response.objectKey()).contains(event.getId().toString());

        ArgumentCaptor<Media> captor = ArgumentCaptor.forClass(Media.class);
        verify(media).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(MediaStatus.PENDING);
        assertThat(captor.getValue().getModerationState()).isEqualTo(ModerationState.VISIBLE);
        assertThat(captor.getValue().getMediaType()).isEqualTo(MediaType.PHOTO);
        // C1: the presigned URL signs the declared byte count.
        verify(storage).presignUpload(anyString(), eq("image/jpeg"), eq(500L));
    }

    // ---- C3: plan limits checked inside the transaction, after locking the host row ----

    @Test
    void requestUploadUrlLocksHostBeforeCheckingPlanLimits() {
        Event event = activeEvent(true);
        when(events.findByInviteCodeAndDeletedAtIsNull("CODE123456")).thenReturn(Optional.of(event));
        when(storage.presignUpload(anyString(), anyString(), anyLong())).thenReturn("https://r2.example/put");

        service.requestUploadUrl(new UploadUrlRequest(
                "CODE123456", "a.jpg", "image/jpeg", 100L, "Guest", null), "203.0.113.1");

        var order = inOrder(users, demoGuard, planLimits, media);
        order.verify(users).findByIdForUpdate(event.getHostId());
        // DG2/DG6: the demo caps are checked under the same lock, before anything is reserved.
        order.verify(demoGuard).assertGuestUploadAllowed(event, 100L, "203.0.113.1");
        order.verify(planLimits).checkCanUpload(event, MediaType.PHOTO, 100L);
        order.verify(media).save(any(Media.class));
    }

    @Test
    void demoUploadStoresIpHashAndRejectedDemoUploadReservesNothing() {
        Event event = activeEvent(true);
        when(events.findByInviteCodeAndDeletedAtIsNull("CODE123456")).thenReturn(Optional.of(event));
        when(storage.presignUpload(anyString(), anyString(), anyLong())).thenReturn("https://r2.example/put");
        when(demoGuard.assertGuestUploadAllowed(event, 100L, "203.0.113.1")).thenReturn("a".repeat(64));

        service.requestUploadUrl(new UploadUrlRequest(
                "CODE123456", "a.jpg", "image/jpeg", 100L, "Guest", null), "203.0.113.1");

        ArgumentCaptor<Media> captor = ArgumentCaptor.forClass(Media.class);
        verify(media).save(captor.capture());
        assertThat(captor.getValue().getUploaderIpHash()).isEqualTo("a".repeat(64));

        when(demoGuard.assertGuestUploadAllowed(event, 200L, "203.0.113.1"))
                .thenThrow(new QuotaExceededException("cap"));
        assertThatThrownBy(() -> service.requestUploadUrl(new UploadUrlRequest(
                "CODE123456", "b.jpg", "image/jpeg", 200L, "Guest", null), "203.0.113.1"))
                .isInstanceOf(QuotaExceededException.class);
        verify(media, times(1)).save(any(Media.class));
        verify(storage, times(1)).presignUpload(anyString(), anyString(), anyLong());
    }

    @Test
    void requestUploadUrlDoesNotReserveWhenQuotaExceeded() {
        Event event = activeEvent(true);
        when(events.findByInviteCodeAndDeletedAtIsNull("CODE123456")).thenReturn(Optional.of(event));
        doThrow(new QuotaExceededException("limit")).when(planLimits)
                .checkCanUpload(any(), any(), anyLong());

        assertThatThrownBy(() -> service.requestUploadUrl(new UploadUrlRequest(
                "CODE123456", "a.jpg", "image/jpeg", 100L, "Guest", null), "203.0.113.1"))
                .isInstanceOf(QuotaExceededException.class);
        verify(media, never()).save(any(Media.class));
        verify(storage, never()).presignUpload(anyString(), anyString(), anyLong());
    }

    // ---- C4: membership supplied on upload must be valid for the event ----

    @Test
    void requestUploadUrlRejectsMembershipFromAnotherEventOrInactive() {
        Event event = activeEvent(true);
        UUID foreignMembership = UUID.randomUUID();
        when(events.findByInviteCodeAndDeletedAtIsNull("CODE123456")).thenReturn(Optional.of(event));
        when(memberships.findByIdAndEventIdAndStatus(foreignMembership, event.getId(), MembershipStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requestUploadUrl(new UploadUrlRequest(
                "CODE123456", "a.jpg", "image/jpeg", 100L, "Guest", foreignMembership), "203.0.113.1"))
                .isInstanceOf(ForbiddenException.class);
        verify(media, never()).save(any(Media.class));
    }

    @Test
    void requestUploadUrlTakesUploaderNameFromMembershipNotRequest() {
        Event event = activeEvent(true);
        EventMembership membership = new EventMembership();
        membership.setId(UUID.randomUUID());
        membership.setEventId(event.getId());
        membership.setGuestDisplayName("Real Name");
        when(events.findByInviteCodeAndDeletedAtIsNull("CODE123456")).thenReturn(Optional.of(event));
        when(memberships.findByIdAndEventIdAndStatus(membership.getId(), event.getId(), MembershipStatus.ACTIVE))
                .thenReturn(Optional.of(membership));
        when(storage.presignUpload(anyString(), anyString(), anyLong())).thenReturn("https://r2.example/put");

        service.requestUploadUrl(new UploadUrlRequest(
                "CODE123456", "a.jpg", "image/jpeg", 100L, "Spoofed Name", membership.getId()), "203.0.113.1");

        ArgumentCaptor<Media> captor = ArgumentCaptor.forClass(Media.class);
        verify(media).save(captor.capture());
        assertThat(captor.getValue().getUploaderDisplayName()).isEqualTo("Real Name");
        assertThat(captor.getValue().getUploaderMembershipId()).isEqualTo(membership.getId());
    }

    // ---- C1: object larger than declared is rejected at completion ----

    @Test
    void completeUploadRejectsObjectLargerThanDeclared() {
        Media pending = pendingMedia(UUID.randomUUID(), 100L);
        when(media.findById(pending.getId())).thenReturn(Optional.of(pending));
        when(storage.headObject(pending.getObjectKey()))
                .thenReturn(Optional.of(HeadObjectResponse.builder().contentLength(5_000_000L).build()));

        assertThatThrownBy(() -> service.completeUpload(
                pending.getId(), new CompleteUploadRequest("a".repeat(64), null, null), "203.0.113.1"))
                .isInstanceOf(UploadRejectedException.class);

        verify(storage).deleteObject(pending.getObjectKey());
        assertThat(pending.getStatus()).isEqualTo(MediaStatus.FAILED);
        assertThat(pending.getModerationState()).isEqualTo(ModerationState.DELETED);
    }

    // ---- C2: self-delete is authorised by membership id only ----

    @Test
    void deleteOwnMediaRejectsDisplayNameOnlyRequests() {
        Event event = activeEvent(true);
        Media item = pendingMedia(event.getId(), 10L);
        item.setUploaderMembershipId(UUID.randomUUID());
        item.setUploaderDisplayName("Alice");
        when(events.findByInviteCodeAndDeletedAtIsNull("CODE123456")).thenReturn(Optional.of(event));
        when(media.findByIdAndEventId(item.getId(), event.getId())).thenReturn(Optional.of(item));

        // Attacker knows the public uploader name but not the membership id.
        assertThatThrownBy(() -> service.deleteOwnMedia("CODE123456", item.getId(),
                new DeleteOwnMediaRequest(UUID.randomUUID(), "Alice"), "203.0.113.9"))
                .isInstanceOf(ForbiddenException.class);
        assertThat(item.getModerationState()).isNotEqualTo(ModerationState.DELETED);
    }

    @Test
    void deleteOwnMediaRejectsMediaWithoutUploaderMembership() {
        Event event = activeEvent(true);
        Media item = pendingMedia(event.getId(), 10L);
        item.setUploaderDisplayName("Alice");
        when(events.findByInviteCodeAndDeletedAtIsNull("CODE123456")).thenReturn(Optional.of(event));
        when(media.findByIdAndEventId(item.getId(), event.getId())).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> service.deleteOwnMedia("CODE123456", item.getId(),
                new DeleteOwnMediaRequest(UUID.randomUUID(), "Alice"), "203.0.113.9"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void deleteOwnMediaAllowsActiveUploaderMembership() {
        Event event = activeEvent(true);
        Media item = pendingMedia(event.getId(), 10L);
        UUID membershipId = UUID.randomUUID();
        item.setUploaderMembershipId(membershipId);
        EventMembership membership = new EventMembership();
        membership.setId(membershipId);
        membership.setEventId(event.getId());
        membership.setGuestDisplayName("Alice");
        when(events.findByInviteCodeAndDeletedAtIsNull("CODE123456")).thenReturn(Optional.of(event));
        when(media.findByIdAndEventId(item.getId(), event.getId())).thenReturn(Optional.of(item));
        when(memberships.findByIdAndEventIdAndStatus(membershipId, event.getId(), MembershipStatus.ACTIVE))
                .thenReturn(Optional.of(membership));

        service.deleteOwnMedia("CODE123456", item.getId(),
                new DeleteOwnMediaRequest(membershipId, null), "203.0.113.9");

        assertThat(item.getModerationState()).isEqualTo(ModerationState.DELETED);
    }

    @Test
    void galleryFlagsOnlyItemsOwnedByRequester() {
        Event event = activeEvent(true);
        event.setShowUploaderNames(true);
        UUID mine = UUID.randomUUID();
        Media own = pendingMedia(event.getId(), 1L);
        own.setUploaderMembershipId(mine);
        own.setCreatedAt(Instant.now());
        Media other = pendingMedia(event.getId(), 1L);
        other.setUploaderMembershipId(UUID.randomUUID());
        other.setCreatedAt(Instant.now());
        when(events.findByInviteCodeAndDeletedAtIsNull("CODE123456")).thenReturn(Optional.of(event));
        when(media.findGalleryFirstPage(eq(event.getId()), eq(ModerationState.VISIBLE), any()))
                .thenReturn(List.of(own, other));

        GalleryPageResponse page = service.gallery("CODE123456", null, 10, mine);

        assertThat(page.items()).extracting(MediaResponse::ownedByRequester).containsExactly(true, false);
    }

    @Test
    void galleryItemsCarryAttachmentDownloadUrl() {
        Event event = activeEvent(true);
        Media item = pendingMedia(event.getId(), 1L);
        item.setOriginalFilename("sunset.jpg");
        item.setCreatedAt(Instant.now());
        when(events.findByInviteCodeAndDeletedAtIsNull("CODE123456")).thenReturn(Optional.of(event));
        when(media.findGalleryFirstPage(eq(event.getId()), eq(ModerationState.VISIBLE), any()))
                .thenReturn(List.of(item));
        when(storage.presignAttachment(item.getObjectKey(), "sunset.jpg")).thenReturn("https://r2.example/attach");

        GalleryPageResponse page = service.gallery("CODE123456", null, 10, null);

        // Used by the web "Download" button so the browser saves instead of opening the file.
        assertThat(page.items().get(0).downloadUrl()).isEqualTo("https://r2.example/attach");
    }

    private static Media pendingMedia(UUID eventId, long declaredBytes) {
        Media m = new Media();
        m.setId(UUID.randomUUID());
        m.setEventId(eventId);
        m.setObjectKey("events/" + eventId + "/originals/" + UUID.randomUUID() + "/f.jpg");
        m.setContentType("image/jpeg");
        m.setMediaType(MediaType.PHOTO);
        m.setStatus(MediaStatus.PENDING);
        m.setModerationState(ModerationState.VISIBLE);
        m.setSizeBytes(declaredBytes);
        return m;
    }

    @Test
    void completeUploadFlagsExactDuplicateAndEnqueues() {
        UUID eventId = UUID.randomUUID();
        Media pending = new Media();
        pending.setId(UUID.randomUUID());
        pending.setEventId(eventId);
        pending.setObjectKey("events/" + eventId + "/originals/x/y.jpg");
        pending.setContentType("image/jpeg");
        pending.setMediaType(MediaType.PHOTO);
        pending.setStatus(MediaStatus.PENDING);

        Media original = new Media();
        original.setId(UUID.randomUUID());

        when(media.findById(pending.getId())).thenReturn(Optional.of(pending));
        when(storage.headObject(pending.getObjectKey()))
                .thenReturn(Optional.of(HeadObjectResponse.builder().contentLength(123L).build()));
        String sha = "a".repeat(64);
        when(media.findFirstByEventIdAndSha256OrderByCreatedAtAscIdAsc(eventId, sha))
                .thenReturn(Optional.of(original));

        MediaResponse response = service.completeUpload(
                pending.getId(), new CompleteUploadRequest(sha, 800, 600), "203.0.113.1");

        assertThat(response.duplicate()).isTrue();
        assertThat(pending.getDuplicateOfId()).isEqualTo(original.getId());
        // Left in UPLOADED so the in-process scheduler picks it up (no broker publish).
        assertThat(pending.getStatus()).isEqualTo(MediaStatus.UPLOADED);
        assertThat(pending.getSizeBytes()).isEqualTo(123L);
    }

    @Test
    void completeUploadIsIdempotentAfterCompletion() {
        Media done = new Media();
        done.setId(UUID.randomUUID());
        done.setEventId(UUID.randomUUID());
        done.setObjectKey("k");
        done.setContentType("image/jpeg");
        done.setMediaType(MediaType.PHOTO);
        done.setStatus(MediaStatus.UPLOADED);
        when(media.findById(done.getId())).thenReturn(Optional.of(done));

        MediaResponse response = service.completeUpload(
                done.getId(), new CompleteUploadRequest("b".repeat(64), null, null), "203.0.113.1");

        assertThat(response.status()).isEqualTo("UPLOADED");
        verify(storage, never()).headObject(anyString());
    }

    @Test
    void keysetCursorRoundTrips() {
        Instant created = Instant.ofEpochMilli(1_725_000_000_123L);
        UUID id = UUID.randomUUID();
        MediaService.Cursor cursor = new MediaService.Cursor(created, id);
        MediaService.Cursor decoded = MediaService.Cursor.decode(cursor.encode());
        assertThat(decoded.createdAt()).isEqualTo(created);
        assertThat(decoded.id()).isEqualTo(id);
    }
}
