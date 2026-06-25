package com.eventshare.api.event;

import com.eventshare.api.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "event_memberships")
public class EventMembership extends BaseEntity {

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    /** Null for anonymous guests who joined by invite code without an account. */
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "guest_display_name")
    private String guestDisplayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private MembershipRole role = MembershipRole.GUEST;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private MembershipStatus status = MembershipStatus.ACTIVE;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt = Instant.now();

    @Column(name = "last_activity_at")
    private Instant lastActivityAt;
}
