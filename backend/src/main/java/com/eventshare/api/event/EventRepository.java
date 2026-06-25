package com.eventshare.api.event;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID> {

    Optional<Event> findByInviteCodeAndDeletedAtIsNull(String inviteCode);

    Optional<Event> findByIdAndDeletedAtIsNull(UUID id);

    boolean existsByInviteCode(String inviteCode);

    List<Event> findByHostIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID hostId);

    long countByHostIdAndDeletedAtIsNull(UUID hostId);

    long countByDeletedAtIsNull();

    @Query("""
            select e from Event e
            where e.deletedAt is null
              and (:q = '' or lower(e.name) like lower(concat('%', :q, '%')))
            order by e.createdAt desc
            """)
    List<Event> searchEvents(@Param("q") String q, Pageable pageable);
}
