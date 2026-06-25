package com.eventshare.api.analytics;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface EventVisitRepository extends JpaRepository<EventVisit, UUID> {
    Optional<EventVisit> findByEventIdAndVisitorKey(UUID eventId, String visitorKey);
    long countByEventId(UUID eventId);
    long countByEventIdAndLastSeenAtAfter(UUID eventId, Instant since);
}
