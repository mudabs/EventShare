package com.eventshare.api.event;

import com.eventshare.api.audit.AuditService;
import com.eventshare.api.common.error.BadRequestException;
import com.eventshare.api.common.error.ForbiddenException;
import com.eventshare.api.common.error.NotFoundException;
import com.eventshare.api.event.dto.MemberView;
import com.eventshare.api.event.dto.MyEventCard;
import com.eventshare.api.media.Media;
import com.eventshare.api.media.MediaRepository;
import com.eventshare.api.media.MediaType;
import com.eventshare.api.media.ModerationState;
import com.eventshare.api.media.r2.R2StorageService;
import com.eventshare.api.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Persistent event membership: signed-in users keep access to events they own or
 * have joined, without needing the original invite link again.
 */
@Service
public class MembershipService {

    private final EventRepository events;
    private final EventMembershipRepository memberships;
    private final MediaRepository media;
    private final R2StorageService storage;
    private final AuditService audit;

    public MembershipService(EventRepository events,
                             EventMembershipRepository memberships,
                             MediaRepository media,
                             R2StorageService storage,
                             AuditService audit) {
        this.events = events;
        this.memberships = memberships;
        this.media = media;
        this.storage = storage;
        this.audit = audit;
    }

    @Transactional
    public void joinAsUser(User user, String inviteCode, String clientIp) {
        Event event = events.findByInviteCodeAndDeletedAtIsNull(inviteCode)
                .orElseThrow(() -> new NotFoundException("Event not found"));
        if (!event.isActive()) {
            throw new ForbiddenException("This event is not currently accepting participants");
        }
        if (event.getHostId().equals(user.getId())) {
            return; // the owner already has a HOST membership
        }

        EventMembership membership = memberships
                .findByEventIdAndUserId(event.getId(), user.getId())
                .orElseGet(() -> {
                    EventMembership created = new EventMembership();
                    created.setEventId(event.getId());
                    created.setUserId(user.getId());
                    created.setRole(MembershipRole.GUEST);
                    created.setJoinedAt(Instant.now());
                    return created;
                });
        membership.setStatus(MembershipStatus.ACTIVE);
        membership.setLastActivityAt(Instant.now());
        if (membership.getGuestDisplayName() == null) {
            membership.setGuestDisplayName(user.getDisplayName());
        }
        EventMembership saved = memberships.save(membership);

        audit.record(event.getId(), user.getId(), user.getDisplayName(), "MEMBER_JOINED",
                "MEMBERSHIP", saved.getId(), null, clientIp);
    }

    @Transactional(readOnly = true)
    public List<MyEventCard> myEvents(User user) {
        List<MyEventCard> cards = new ArrayList<>();
        for (Event event : events.findByHostIdAndDeletedAtIsNullOrderByCreatedAtDesc(user.getId())) {
            cards.add(toCard(event, "OWNER", event.getUpdatedAt()));
        }
        for (EventMembership membership : memberships.findByUserIdAndStatus(user.getId(), MembershipStatus.ACTIVE)) {
            if (membership.getRole() == MembershipRole.HOST) {
                continue;
            }
            events.findByIdAndDeletedAtIsNull(membership.getEventId()).ifPresent(event -> {
                if (!event.getHostId().equals(user.getId())) {
                    Instant lastActivity = membership.getLastActivityAt() != null
                            ? membership.getLastActivityAt() : event.getUpdatedAt();
                    cards.add(toCard(event, "GUEST", lastActivity));
                }
            });
        }
        return cards;
    }

    @Transactional
    public void leave(User user, UUID eventId) {
        EventMembership membership = memberships.findByEventIdAndUserId(eventId, user.getId())
                .orElseThrow(() -> new NotFoundException("You are not a member of this event"));
        if (membership.getRole() == MembershipRole.HOST) {
            throw new BadRequestException("Owners cannot leave their own event");
        }
        membership.setStatus(MembershipStatus.LEFT);
        memberships.save(membership);
        audit.record(eventId, user.getId(), user.getDisplayName(), "MEMBER_LEFT",
                "MEMBERSHIP", membership.getId(), null, null);
    }

    @Transactional(readOnly = true)
    public List<MemberView> listMembers(User host, UUID eventId) {
        requireOwner(host, eventId);
        return memberships.findByEventIdOrderByJoinedAtAsc(eventId).stream()
                .map(MemberView::from)
                .toList();
    }

    @Transactional
    public void removeMember(User host, UUID eventId, UUID membershipId) {
        requireOwner(host, eventId);
        EventMembership membership = memberships.findById(membershipId)
                .orElseThrow(() -> new NotFoundException("Membership not found"));
        if (!membership.getEventId().equals(eventId)) {
            throw new NotFoundException("Membership not found");
        }
        if (membership.getRole() == MembershipRole.HOST) {
            throw new BadRequestException("Cannot remove the event owner");
        }
        membership.setStatus(MembershipStatus.REMOVED);
        memberships.save(membership);
        audit.record(eventId, host.getId(), host.getDisplayName(), "MEMBER_REMOVED",
                "MEMBERSHIP", membership.getId(), null, null);
    }

    private Event requireOwner(User host, UUID eventId) {
        Event event = events.findByIdAndDeletedAtIsNull(eventId)
                .orElseThrow(() -> new NotFoundException("Event not found"));
        if (!event.getHostId().equals(host.getId())) {
            throw new ForbiddenException("You do not have access to this event");
        }
        return event;
    }

    private MyEventCard toCard(Event event, String role, Instant lastActivity) {
        String coverUrl = null;
        if (event.getCoverMediaId() != null) {
            coverUrl = media.findById(event.getCoverMediaId())
                    .map(this::coverUrlFor)
                    .orElse(null);
        }
        long photos = media.countByEventIdAndMediaTypeAndModerationStateNot(
                event.getId(), MediaType.PHOTO, ModerationState.DELETED);
        long videos = media.countByEventIdAndMediaTypeAndModerationStateNot(
                event.getId(), MediaType.VIDEO, ModerationState.DELETED);
        return new MyEventCard(event.getId(), event.getName(), event.getEventType(), coverUrl,
                event.getEventDate(), role, event.getStatus().name(), event.getInviteCode(),
                photos, videos, lastActivity);
    }

    private String coverUrlFor(Media cover) {
        String key = cover.getThumbnailKey() != null ? cover.getThumbnailKey() : cover.getObjectKey();
        return storage.presignDownload(key);
    }
}
