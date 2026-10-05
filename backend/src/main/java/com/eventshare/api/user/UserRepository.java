package com.eventshare.api.user;

import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByClerkUserId(String clerkUserId);

    /**
     * Loads the user row with {@code SELECT ... FOR UPDATE}. Used to serialise quota
     * checks for one event host: concurrent upload-URL requests against any of the
     * host's events queue behind this lock, so "count, compare to plan limit, insert"
     * behaves atomically. Must be called inside a read-write transaction (change C3).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
            select u from User u
            where u.deletedAt is null
              and (:q = '' or lower(u.email) like lower(concat('%', :q, '%'))
                   or lower(u.displayName) like lower(concat('%', :q, '%')))
            order by u.createdAt desc
            """)
    List<User> search(@Param("q") String q, Pageable pageable);

    @Query("select u.createdAt from User u where u.deletedAt is null")
    List<Instant> allCreatedAt();

    long countByDeletedAtIsNull();
}
