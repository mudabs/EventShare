package com.eventshare.api.event;

import com.eventshare.api.audit.AuditService;
import com.eventshare.api.common.error.ConflictException;
import com.eventshare.api.common.error.ForbiddenException;
import com.eventshare.api.common.error.NotFoundException;
import com.eventshare.api.common.error.TooManyRequestsException;
import com.eventshare.api.common.util.InviteCodeGenerator;
import com.eventshare.api.common.util.RateLimiter;
import com.eventshare.api.config.AppProperties;
import com.eventshare.api.event.dto.CreateEventRequest;
import com.eventshare.api.event.dto.EventAnalyticsResponse;
import com.eventshare.api.event.dto.EventResponse;
import com.eventshare.api.event.dto.JoinEventRequest;
import com.eventshare.api.event.dto.JoinEventResponse;
import com.eventshare.api.event.dto.PublicEventResponse;
import com.eventshare.api.media.Media;
import com.eventshare.api.media.MediaRepository;
import com.eventshare.api.media.ModerationState;
import com.eventshare.api.media.r2.R2StorageService;
import com.eventshare.api.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class EventService {

    private static final int INVITE_CODE_ATTEMPTS = 8;

    private final EventRepository events;
    private final EventMembershipRepository memberships;
    private final MediaRepository media;
    private final InviteCodeGenerator inviteCodes;
    private final AuditService audit;
    private final RateLimiter rateLimiter;
    private final AppProperties props;
    private final R2StorageService storage;

    public EventService(EventRepository events,
                        EventMembershipRepository memberships,
                        MediaRepository media,
                        InviteCodeGenerator inviteCodes,
                        AuditService audit,
                        RateLimiter rateLimiter,
                        AppProperties props,
                        R2StorageService storage) {
        this.events = events;
        this.memberships = memberships;
        this.media = media;
        this.inviteCodes = inviteCodes;
        this.audit = audit;
        this.rateLimiter = rateLimiter;
        this.props = props;
        this.storage = storage;
    }

    @Transactional
    public EventResponse createEvent(User host, CreateEventRequest request) {
        Event event = new Event();
        event.setHostId(host.getId());
        event.setName(request.name().trim());
        event.setDescription(request.description());
        event.setEventType(request.eventType());
        event.setEventDate(request.eventDate());
        if (request.allowGuestDownloads() != null) {
            event.setAllowGuestDownloads(request.allowGuestDownloads());
        }
        if (request.autoApprove() != null) {
            event.setAutoApprove(request.autoApprove());
        }
        event.setInviteCode(allocateUniqueInviteCode());
        event.setStatus(EventStatus.ACTIVE);
        Event saved = events.save(event);

        EventMembership hostMembership = new EventMembership();
        hostMembership.setEventId(saved.getId());
        hostMembership.setUserId(host.getId());
        hostMembership.setRole(MembershipRole.HOST);
        hostMembership.setJoinedAt(Instant.now());
        memberships.save(hostMembership);

        audit.record(saved.getId(), host.getId(), host.getDisplayName(), "EVENT_CREATED",
                "EVENT", saved.getId(),
                Map.of("name", saved.getName(), "type", saved.getEventType().name()), null);

        return EventResponse.from(saved, inviteUrl(saved.getInviteCode()));
    }

    @Transactional(readOnly = true)
    public EventResponse getEvent(User host, UUID eventId) {
        Event event = loadOwned(eventId, host);
        return EventResponse.from(event, inviteUrl(event.getInviteCode()));
    }

    @Transactional(readOnly = true)
    public PublicEventResponse getPublicByInviteCode(String inviteCode) {
        Event event = events.findByInviteCodeAndDeletedAtIsNull(inviteCode)
                .orElseThrow(() -> new NotFoundException("Event not found"));
        return PublicEventResponse.from(event, resolveCoverUrl(event));
    }

    private String resolveCoverUrl(Event event) {
        if (event.getCoverMediaId() == null) {
            return null;
        }
        return media.findById(event.getCoverMediaId())
                .map(m -> storage.presignDownload(
                        m.getThumbnailKey() != null ? m.getThumbnailKey() : m.getObjectKey()))
                .orElse(null);
    }

    @Transactional
    public JoinEventResponse join(String inviteCode, JoinEventRequest request, String clientIp) {
        if (!rateLimiter.tryAcquire("join:" + clientIp, props.ratelimit().joinRequestsPerMinute())) {
            throw new TooManyRequestsException("Too many join attempts. Please wait a moment.");
        }
        Event event = events.findByInviteCodeAndDeletedAtIsNull(inviteCode)
                .orElseThrow(() -> new NotFoundException("Event not found"));
        if (!event.isActive()) {
            throw new ForbiddenException("This event is not currently accepting participants");
        }

        EventMembership membership = new EventMembership();
        membership.setEventId(event.getId());
        membership.setGuestDisplayName(request.displayName().trim());
        membership.setRole(MembershipRole.GUEST);
        membership.setJoinedAt(Instant.now());
        EventMembership saved = memberships.save(membership);

        audit.record(event.getId(), null, request.displayName().trim(), "GUEST_JOINED",
                "MEMBERSHIP", saved.getId(), null, clientIp);

        return new JoinEventResponse(saved.getId(), event.getId(), event.getInviteCode(),
                event.getName(), saved.getGuestDisplayName());
    }

    @Transactional(readOnly = true)
    public EventAnalyticsResponse analytics(User host, UUID eventId) {
        loadOwned(eventId, host);
        long total = media.countByEventId(eventId);
        long visible = media.countByEventIdAndModerationState(eventId, ModerationState.VISIBLE);
        long hidden = media.countByEventIdAndModerationState(eventId, ModerationState.HIDDEN);
        long archived = media.countByEventIdAndModerationState(eventId, ModerationState.ARCHIVED);
        long members = memberships.countByEventId(eventId);
        long bytes = media.sumSizeBytesByEvent(eventId, ModerationState.DELETED);
        return new EventAnalyticsResponse(eventId, members, total, visible, hidden, archived, bytes);
    }

    private Event loadOwned(UUID eventId, User host) {
        Event event = events.findByIdAndDeletedAtIsNull(eventId)
                .orElseThrow(() -> new NotFoundException("Event not found"));
        if (!event.getHostId().equals(host.getId())) {
            throw new ForbiddenException("You do not have access to this event");
        }
        return event;
    }

    private String allocateUniqueInviteCode() {
        for (int attempt = 0; attempt < INVITE_CODE_ATTEMPTS; attempt++) {
            String code = inviteCodes.generate();
            if (!events.existsByInviteCode(code)) {
                return code;
            }
        }
        throw new ConflictException("Could not allocate a unique invite code. Please retry.");
    }

    private String inviteUrl(String inviteCode) {
        return props.appBaseUrl() + "/e/" + inviteCode;
    }
}
