package com.eventshare.api.event.dto;

import com.eventshare.api.event.EventMembership;

import java.time.Instant;
import java.util.UUID;

/** Owner-facing view of one participant. */
public record MemberView(
        UUID membershipId,
        UUID userId,
        String displayName,
        String role,
        String status,
        Instant joinedAt,
        Instant lastActivityAt
) {
    public static MemberView from(EventMembership m) {
        return new MemberView(
                m.getId(), m.getUserId(), m.getGuestDisplayName(),
                m.getRole().name(), m.getStatus().name(), m.getJoinedAt(), m.getLastActivityAt());
    }
}
