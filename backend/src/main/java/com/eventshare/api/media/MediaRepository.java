package com.eventshare.api.media;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MediaRepository extends JpaRepository<Media, UUID> {

    // ---- Gallery (keyset pagination, stable under concurrent inserts) ----

    @Query("""
            select m from Media m
            where m.eventId = :eventId and m.moderationState = :state
            order by m.createdAt desc, m.id desc
            """)
    List<Media> findGalleryFirstPage(@Param("eventId") UUID eventId,
                                     @Param("state") ModerationState state,
                                     Pageable pageable);

    @Query("""
            select m from Media m
            where m.eventId = :eventId and m.moderationState = :state
              and (m.createdAt < :cursorCreatedAt
                   or (m.createdAt = :cursorCreatedAt and m.id < :cursorId))
            order by m.createdAt desc, m.id desc
            """)
    List<Media> findGalleryAfter(@Param("eventId") UUID eventId,
                                 @Param("state") ModerationState state,
                                 @Param("cursorCreatedAt") Instant cursorCreatedAt,
                                 @Param("cursorId") UUID cursorId,
                                 Pageable pageable);

    // ---- In-process media processing queue (status-driven) ----

    /**
     * Assets awaiting processing: freshly UPLOADED rows, plus rows left in
     * PROCESSING past the staleness cutoff (recovery after a crash mid-processing).
     * Oldest first for fair, roughly FIFO handling.
     */
    @Query("""
            select m from Media m
            where m.status = :uploaded
               or (m.status = :processing and m.updatedAt < :staleCutoff)
            order by m.createdAt asc, m.id asc
            """)
    List<Media> findProcessableBatch(@Param("uploaded") MediaStatus uploaded,
                                     @Param("processing") MediaStatus processing,
                                     @Param("staleCutoff") Instant staleCutoff,
                                     Pageable pageable);

    // ---- Duplicate detection (exact, SHA-256) ----

    Optional<Media> findFirstByEventIdAndSha256OrderByCreatedAtAscIdAsc(UUID eventId, String sha256);

    // ---- Analytics ----

    long countByEventId(UUID eventId);

    long countByEventIdAndModerationState(UUID eventId, ModerationState state);

    @Query("""
            select coalesce(sum(m.sizeBytes), 0) from Media m
            where m.eventId = :eventId and m.moderationState <> :excluded
            """)
    long sumSizeBytesByEvent(@Param("eventId") UUID eventId,
                             @Param("excluded") ModerationState excluded);

    long countByEventIdAndMediaTypeAndModerationStateNot(UUID eventId, MediaType mediaType, ModerationState moderationState);

    long countByEventIdAndCreatedAtAfter(UUID eventId, Instant after);

    @Query("""
            select m.createdAt from Media m
            where m.eventId = :eventId and m.moderationState <> :excluded and m.createdAt >= :since
            """)
    List<Instant> findCreatedAtSince(@Param("eventId") UUID eventId,
                                     @Param("excluded") ModerationState excluded,
                                     @Param("since") Instant since);

    Optional<Media> findByIdAndEventId(UUID id, UUID eventId);

    @Query("""
            select coalesce(sum(m.sizeBytes), 0) from Media m, com.eventshare.api.event.Event e
            where m.eventId = e.id and e.hostId = :hostId and m.moderationState <> :excluded
            """)
    long sumStorageForHost(@Param("hostId") UUID hostId,
                           @Param("excluded") ModerationState excluded);

    @Query("select coalesce(sum(m.sizeBytes), 0) from Media m where m.moderationState <> :excluded")
    long sumAllStorage(@Param("excluded") ModerationState excluded);
}
