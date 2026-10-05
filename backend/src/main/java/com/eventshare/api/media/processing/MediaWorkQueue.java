package com.eventshare.api.media.processing;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Atomically claims media rows for processing (change C5).
 *
 * <p>One statement selects up to {@code limit} rows that are UPLOADED, or stuck in
 * PROCESSING past the staleness cutoff, locks them with {@code FOR UPDATE SKIP LOCKED},
 * flips them to PROCESSING and bumps {@code updated_at}. Concurrent pollers (several API
 * replicas, or an overlapping tick) therefore receive disjoint sets: a row locked by one
 * claimer is skipped by the others, and once committed its fresh {@code updated_at}
 * means it is no longer "stale".
 *
 * <p>Plain JDBC is used on purpose: {@code UPDATE ... RETURNING} is a PostgreSQL feature
 * and bypassing the JPA persistence context avoids stale first-level-cache entities.
 *
 * <p>Operational note: {@code eventshare.processing.stale-after-seconds} must be longer
 * than the slowest expected processing run (large video + ffmpeg), otherwise a row that
 * is still being processed can be reclaimed by another poller.
 */
@Component
public class MediaWorkQueue {

    static final String CLAIM_SQL = """
            UPDATE media
               SET status = 'PROCESSING', updated_at = now()
             WHERE id IN (
                   SELECT id FROM media
                    WHERE status = 'UPLOADED'
                       OR (status = 'PROCESSING' AND updated_at < :staleCutoff)
                    ORDER BY created_at ASC, id ASC
                    LIMIT :limit
                    FOR UPDATE SKIP LOCKED)
            RETURNING id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public MediaWorkQueue(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Claims up to {@code limit} rows and returns their ids (order not guaranteed). */
    @Transactional
    public List<UUID> claim(int limit, Instant staleCutoff) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("limit", limit)
                .addValue("staleCutoff", Timestamp.from(staleCutoff));
        return jdbc.query(CLAIM_SQL, params, (rs, rowNum) -> rs.getObject("id", UUID.class));
    }
}
