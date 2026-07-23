package com.eventshare.api.media;

import com.eventshare.api.audit.AuditService;
import com.eventshare.api.common.error.BadRequestException;
import com.eventshare.api.common.error.TooManyRequestsException;
import com.eventshare.api.config.AppProperties;
import com.eventshare.api.event.Event;
import com.eventshare.api.event.EventRepository;
import com.eventshare.api.event.EventStatus;
import com.eventshare.api.media.dto.CompleteUploadRequest;
import com.eventshare.api.media.dto.MediaResponse;
import com.eventshare.api.media.dto.UploadUrlRequest;
import com.eventshare.api.media.dto.UploadUrlResponse;
import com.eventshare.api.media.r2.R2StorageService;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MediaServiceTest {

    @Mock MediaRepository media;
    @Mock EventRepository events;
    @Mock R2StorageService storage;
    @Mock AuditService audit;
    @Mock com.eventshare.api.common.util.RateLimiter rateLimiter;

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
        event.setInviteCode("CODE123456");
        event.setStatus(EventStatus.ACTIVE);
        event.setAutoApprove(autoApprove);
        return event;
    }

    @BeforeEach
    void setUp() {
        service = new MediaService(media, events, storage, audit, rateLimiter, props(), new SimpleMeterRegistry());
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
        when(storage.presignUpload(anyString(), anyString())).thenReturn("https://r2.example/put");

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
