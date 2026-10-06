package com.eventshare.api.report;

import com.eventshare.api.audit.AuditService;
import com.eventshare.api.common.error.NotFoundException;
import com.eventshare.api.common.error.TooManyRequestsException;
import com.eventshare.api.common.util.Hashing;
import com.eventshare.api.common.util.RateLimiter;
import com.eventshare.api.event.Event;
import com.eventshare.api.event.EventRepository;
import com.eventshare.api.media.Media;
import com.eventshare.api.media.MediaRepository;
import com.eventshare.api.media.ModerationState;
import com.eventshare.api.report.dto.ReportMediaRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Lets any gallery viewer report a photo or video (Terms of Use, "Reporting content").
 *
 * <ul>
 *   <li>One report per visitor per item (keyed by a salted hash of the IP, no raw IP stored);
 *       repeats are accepted silently so the endpoint does not reveal prior reports.</li>
 *   <li>The host sees the open report count in the manage gallery.</li>
 *   <li>{@link #AUTO_HIDE_THRESHOLD} open reports from different visitors, or any single
 *       child-safety report, hide the item until the host reviews it.</li>
 *   <li>Rate limited per IP.</li>
 * </ul>
 */
@Service
public class MediaReportService {

    static final int AUTO_HIDE_THRESHOLD = 3;
    private static final int REPORTS_PER_MINUTE = 10;
    private static final String IP_SALT = "eventshare-report|";

    private final MediaReportRepository reports;
    private final MediaRepository media;
    private final EventRepository events;
    private final AuditService audit;
    private final RateLimiter rateLimiter;

    public MediaReportService(MediaReportRepository reports, MediaRepository media, EventRepository events,
                              AuditService audit, RateLimiter rateLimiter) {
        this.reports = reports;
        this.media = media;
        this.events = events;
        this.audit = audit;
        this.rateLimiter = rateLimiter;
    }

    @Transactional
    public void report(String inviteCode, UUID mediaId, ReportMediaRequest request, String clientIp) {
        if (!rateLimiter.tryAcquire("report:" + clientIp, REPORTS_PER_MINUTE)) {
            throw new TooManyRequestsException("Too many reports. Please try again in a minute.");
        }
        Event event = events.findByInviteCodeAndDeletedAtIsNull(inviteCode)
                .orElseThrow(() -> new NotFoundException("Event not found"));
        Media item = media.findByIdAndEventId(mediaId, event.getId())
                .filter(m -> m.getModerationState() == ModerationState.VISIBLE)
                .orElseThrow(() -> new NotFoundException("Media not found"));

        String ipHash = Hashing.sha256Hex(IP_SALT + (clientIp == null ? "unknown" : clientIp.trim()));
        if (reports.existsByMediaIdAndReporterIpHash(mediaId, ipHash)) {
            return;
        }

        MediaReport report = new MediaReport();
        report.setMediaId(mediaId);
        report.setEventId(event.getId());
        report.setReason(request.reason());
        report.setDetails(trimToNull(request.details()));
        report.setReporterIpHash(ipHash);
        reports.save(report);

        audit.record(event.getId(), null, null, "MEDIA_REPORTED", "MEDIA", mediaId,
                Map.of("reason", request.reason().name()), clientIp);

        long open = reports.countByMediaIdAndResolvedAtIsNull(mediaId);
        if (request.reason() == ReportReason.CHILD_SAFETY || open >= AUTO_HIDE_THRESHOLD) {
            item.setModerationState(ModerationState.HIDDEN);
            media.save(item);
            audit.record(event.getId(), null, null, "MEDIA_AUTO_HIDDEN", "MEDIA", mediaId,
                    Map.of("openReports", open, "trigger", request.reason().name()), null);
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }
}
