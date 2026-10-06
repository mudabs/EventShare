package com.eventshare.api.report;

import com.eventshare.api.audit.AuditService;
import com.eventshare.api.common.error.NotFoundException;
import com.eventshare.api.common.error.TooManyRequestsException;
import com.eventshare.api.common.util.RateLimiter;
import com.eventshare.api.event.Event;
import com.eventshare.api.event.EventRepository;
import com.eventshare.api.media.Media;
import com.eventshare.api.media.MediaRepository;
import com.eventshare.api.media.ModerationState;
import com.eventshare.api.report.dto.ReportMediaRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

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

/** Photo reports: dedupe, auto-hide, child-safety, visibility and rate limiting. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MediaReportServiceTest {

    @Mock MediaReportRepository reports;
    @Mock MediaRepository media;
    @Mock EventRepository events;
    @Mock AuditService audit;
    @Mock RateLimiter rateLimiter;

    MediaReportService service;
    Event event;
    Media item;

    @BeforeEach
    void setUp() {
        service = new MediaReportService(reports, media, events, audit, rateLimiter);
        event = new Event();
        event.setId(UUID.randomUUID());
        event.setInviteCode("CODE123456");
        item = new Media();
        item.setId(UUID.randomUUID());
        item.setEventId(event.getId());
        item.setModerationState(ModerationState.VISIBLE);
        when(rateLimiter.tryAcquire(anyString(), anyInt())).thenReturn(true);
        when(events.findByInviteCodeAndDeletedAtIsNull("CODE123456")).thenReturn(Optional.of(event));
        when(media.findByIdAndEventId(item.getId(), event.getId())).thenReturn(Optional.of(item));
        when(reports.existsByMediaIdAndReporterIpHash(any(), anyString())).thenReturn(false);
    }

    private void report(ReportReason reason, String ip) {
        service.report("CODE123456", item.getId(), new ReportMediaRequest(reason, "  note  "), ip);
    }

    @Test
    void storesReportWithHashedIpAndTrimmedDetails() {
        when(reports.countByMediaIdAndResolvedAtIsNull(item.getId())).thenReturn(1L);

        report(ReportReason.ME_REMOVE, "203.0.113.7");

        ArgumentCaptor<MediaReport> captor = ArgumentCaptor.forClass(MediaReport.class);
        verify(reports).save(captor.capture());
        assertThat(captor.getValue().getReason()).isEqualTo(ReportReason.ME_REMOVE);
        assertThat(captor.getValue().getDetails()).isEqualTo("note");
        assertThat(captor.getValue().getReporterIpHash()).hasSize(64).doesNotContain("203.0.113.7");
        assertThat(item.getModerationState()).isEqualTo(ModerationState.VISIBLE);
    }

    @Test
    void thirdOpenReportHidesThePhoto() {
        when(reports.countByMediaIdAndResolvedAtIsNull(item.getId())).thenReturn(3L);

        report(ReportReason.INAPPROPRIATE, "203.0.113.7");

        assertThat(item.getModerationState()).isEqualTo(ModerationState.HIDDEN);
        verify(media).save(item);
    }

    @Test
    void childSafetyReportHidesImmediately() {
        when(reports.countByMediaIdAndResolvedAtIsNull(item.getId())).thenReturn(1L);

        report(ReportReason.CHILD_SAFETY, "203.0.113.7");

        assertThat(item.getModerationState()).isEqualTo(ModerationState.HIDDEN);
    }

    @Test
    void repeatReportFromSameVisitorIsIgnoredSilently() {
        when(reports.existsByMediaIdAndReporterIpHash(any(), anyString())).thenReturn(true);

        report(ReportReason.ME_REMOVE, "203.0.113.7");

        verify(reports, never()).save(any());
        verify(media, never()).save(any());
    }

    @Test
    void hiddenOrUnknownMediaCannotBeReported() {
        item.setModerationState(ModerationState.HIDDEN);
        assertThatThrownBy(() -> report(ReportReason.OTHER, "203.0.113.7"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void reportsAreRateLimitedPerIp() {
        when(rateLimiter.tryAcquire(anyString(), anyInt())).thenReturn(false);
        assertThatThrownBy(() -> report(ReportReason.OTHER, "203.0.113.7"))
                .isInstanceOf(TooManyRequestsException.class);
        verify(reports, never()).save(any());
    }
}
