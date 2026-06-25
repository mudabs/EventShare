package com.eventshare.api.event;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventMembershipRepository extends JpaRepository<EventMembership, UUID> {

    Optional<EventMembership> findByEventIdAndUserId(UUID eventId, UUID userId);

    long countByEventId(UUID eventId);

    long countByEventIdAndStatus(UUID eventId, MembershipStatus status);

    List<EventMembership> findByUserIdAndStatus(UUID userId, MembershipStatus status);

    List<EventMembership> findByEventIdOrderByJoinedAtAsc(UUID eventId);
}
