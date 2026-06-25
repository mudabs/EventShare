package com.eventshare.api.analytics;

import com.eventshare.api.analytics.dto.DayCount;
import com.eventshare.api.analytics.dto.OwnerDashboardResponse;
import com.eventshare.api.analytics.dto.UserDashboardResponse;
import com.eventshare.api.common.error.ForbiddenException;
import com.eventshare.api.common.error.NotFoundException;
import com.eventshare.api.event.Event;
import com.eventshare.api.event.EventMembershipRepository;
import com.eventshare.api.event.EventRepository;
import com.eventshare.api.event.MembershipService;
import com.eventshare.api.event.MembershipStatus;
import com.eventshare.api.event.dto.MyEventCard;
import com.eventshare.api.media.MediaRepository;
import com.eventshare.api.media.MediaType;
import com.eventshare.api.media.ModerationState;
import com.eventshare.api.user.User;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Dashboard analytics computed by live aggregation over media/memberships, plus
 * lightweight visit tracking for unique-visitor and active-guest counts.
 */
@Service
public class AnalyticsService {

    private static final int ACTIVITY_DAYS = 14;
    private static final int ACTIVE_GUEST_DAYS = 7;

    private final EventRepository events;
    private final EventMembershipRepository memberships;
    private final MediaRepository media;
    private final EventVisitRepository visits;
    private final MembershipService membershipService;

    public AnalyticsService(EventRepository events,
                            EventMembershipRepository memberships,
                            MediaRepository media,
                            EventVisitRepository visits,
                            MembershipService membershipService) {
        this.events = events;
        this.memberships = memberships;
        this.media = media;
        this.visits = visits;
        this.membershipService = membershipService;
    }

    /** Records (or refreshes) a distinct visit. Safe under the unique-key race. */
    @Transactional
    public void recordVisit(UUID eventId, String visitorKey) {
        try {
            EventVisit visit = visits.findByEventIdAndVisitorKey(eventId, visitorKey)
                    .orElseGet(() -> {
                        EventVisit fresh = new EventVisit();
                        fresh.setEventId(eventId);
                        fresh.setVisitorKey(visitorKey);
                        fresh.setFirstSeenAt(Instant.now());
                        return fresh;
                    });
            visit.setLastSeenAt(Instant.now());
            visits.save(visit);
        } catch (DataIntegrityViolationException race) {
            visits.findByEventIdAndVisitorKey(eventId, visitorKey).ifPresent(existing -> {
                existing.setLastSeenAt(Instant.now());
                visits.save(existing);
            });
        }
    }

    @Transactional
    public void recordVisitByCode(String inviteCode, String visitorKey) {
        events.findByInviteCodeAndDeletedAtIsNull(inviteCode)
                .ifPresent(event -> recordVisit(event.getId(), visitorKey));
    }

    @Transactional(readOnly = true)
    public OwnerDashboardResponse ownerDashboard(User host, UUID eventId) {
        Event event = events.findByIdAndDeletedAtIsNull(eventId)
                .orElseThrow(() -> new NotFoundException("Event not found"));
        if (!event.getHostId().equals(host.getId())) {
            throw new ForbiddenException("You do not have access to this event");
        }

        long photos = media.countByEventIdAndMediaTypeAndModerationStateNot(
                eventId, MediaType.PHOTO, ModerationState.DELETED);
        long videos = media.countByEventIdAndMediaTypeAndModerationStateNot(
                eventId, MediaType.VIDEO, ModerationState.DELETED);
        long bytes = media.sumSizeBytesByEvent(eventId, ModerationState.DELETED);

        Instant startOfToday = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
        long uploadsToday = media.countByEventIdAndCreatedAtAfter(eventId, startOfToday);

        long totalGuests = memberships.countByEventIdAndStatus(eventId, MembershipStatus.ACTIVE);
        long uniqueVisitors = visits.countByEventId(eventId);
        long activeGuests = visits.countByEventIdAndLastSeenAtAfter(
                eventId, Instant.now().minus(ACTIVE_GUEST_DAYS, ChronoUnit.DAYS));

        return new OwnerDashboardResponse(eventId, photos, videos, uploadsToday, bytes,
                totalGuests, uniqueVisitors, activeGuests,
                event.getCreatedAt(), event.getExpiresAt(), uploadActivity(eventId));
    }

    @Transactional(readOnly = true)
    public UserDashboardResponse userDashboard(User user) {
        List<MyEventCard> all = membershipService.myEvents(user);
        long owned = all.stream().filter(c -> "OWNER".equals(c.role())).count();
        long joined = all.size() - owned;
        long photos = all.stream().mapToLong(MyEventCard::photoCount).sum();
        long videos = all.stream().mapToLong(MyEventCard::videoCount).sum();
        List<MyEventCard> recent = all.stream().limit(6).collect(Collectors.toList());
        return new UserDashboardResponse(owned, joined, photos, videos, recent);
    }

    private List<DayCount> uploadActivity(UUID eventId) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Instant since = today.minusDays(ACTIVITY_DAYS - 1L).atStartOfDay().toInstant(ZoneOffset.UTC);
        Map<LocalDate, Long> byDay = media.findCreatedAtSince(eventId, ModerationState.DELETED, since).stream()
                .collect(Collectors.groupingBy(
                        ts -> ts.atZone(ZoneOffset.UTC).toLocalDate(),
                        Collectors.counting()));
        List<DayCount> series = new ArrayList<>(ACTIVITY_DAYS);
        for (int i = ACTIVITY_DAYS - 1; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            series.add(new DayCount(day, byDay.getOrDefault(day, 0L)));
        }
        return series;
    }
}
