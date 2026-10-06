package com.eventshare.api.privacy;

import com.eventshare.api.media.r2.R2StorageService;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Permanently erases deleted content after the grace period
 * ({@code eventshare.privacy.media-purge-days}, default 30), as the Privacy Policy promises.
 *
 * <ol>
 *   <li>Photos and videos in moderation state DELETED whose {@code deleted_at} is older than
 *       the grace period: the original and thumbnail are removed from R2, then the row
 *       (its reports cascade).</li>
 *   <li>Events soft-deleted longer ago than the grace period: all their media is erased the
 *       same way, then the event row (memberships, visits and analytics cascade; audit rows
 *       keep their history with the event reference cleared).</li>
 * </ol>
 *
 * <p>Within the grace period a host can still restore a deleted item. A row is deleted only
 * after its R2 objects were removed, so a storage failure leaves it for the next run instead
 * of orphaning files. Work is batched; S3/R2 deletes are idempotent, so overlapping runs on
 * several replicas are harmless.
 */
@Component
public class MediaPurgeJob {

    private static final Logger log = LoggerFactory.getLogger(MediaPurgeJob.class);
    private static final int BATCH = 200;

    private final JdbcTemplate jdbc;
    private final R2StorageService storage;
    private final MeterRegistry meters;
    private final int graceDays;

    public MediaPurgeJob(JdbcTemplate jdbc, R2StorageService storage, MeterRegistry meters,
                         @Value("${eventshare.privacy.media-purge-days:30}") int graceDays) {
        this.jdbc = jdbc;
        this.storage = storage;
        this.meters = meters;
        this.graceDays = Math.max(1, graceDays);
    }

    @Scheduled(cron = "${eventshare.privacy.media-purge-cron:0 45 3 * * *}")
    public void run() {
        try {
            int media = purgeDeletedMedia();
            int events = purgeDeletedEvents();
            if (media > 0 || events > 0) {
                log.info("Purged {} deleted media items and {} deleted events older than {} days",
                        media, events, graceDays);
            }
        } catch (RuntimeException e) {
            log.error("Media purge failed: {}", e.getMessage(), e);
        }
    }

    record Item(UUID id, String objectKey, String thumbnailKey) {
    }

    /** Items deleted (moderation state DELETED) longer ago than the grace period. */
    int purgeDeletedMedia() {
        int total = 0;
        while (true) {
            List<Item> batch = jdbc.query("""
                    SELECT id, object_key, thumbnail_key FROM media
                     WHERE moderation_state = 'DELETED'
                       AND deleted_at < now() - make_interval(days => ?)
                     ORDER BY deleted_at
                     LIMIT ?
                    """, (rs, n) -> new Item(rs.getObject("id", UUID.class),
                    rs.getString("object_key"), rs.getString("thumbnail_key")), graceDays, BATCH);
            int erased = eraseAll(batch);
            total += erased;
            if (batch.size() < BATCH || erased == 0) {
                return total;
            }
        }
    }

    /** Events soft-deleted longer ago than the grace period, with all their media. */
    int purgeDeletedEvents() {
        List<UUID> eventIds = jdbc.query("""
                SELECT id FROM events
                 WHERE deleted_at IS NOT NULL AND deleted_at < now() - make_interval(days => ?)
                 ORDER BY deleted_at
                 LIMIT ?
                """, (rs, n) -> rs.getObject("id", UUID.class), graceDays, BATCH);
        int purged = 0;
        for (UUID eventId : eventIds) {
            List<Item> items = jdbc.query(
                    "SELECT id, object_key, thumbnail_key FROM media WHERE event_id = ?",
                    (rs, n) -> new Item(rs.getObject("id", UUID.class),
                            rs.getString("object_key"), rs.getString("thumbnail_key")), eventId);
            int erased = eraseAll(items);
            if (erased == items.size()) {
                purged += jdbc.update("""
                        DELETE FROM events e
                         WHERE e.id = ? AND e.deleted_at IS NOT NULL
                           AND NOT EXISTS (SELECT 1 FROM media m WHERE m.event_id = e.id)
                        """, eventId);
            }
        }
        if (purged > 0) {
            meters.counter("eventshare.purge.events").increment(purged);
        }
        return purged;
    }

    /** Removes R2 objects, then rows. Returns how many rows were erased. */
    private int eraseAll(List<Item> items) {
        int erased = 0;
        for (Item item : items) {
            try {
                storage.deleteObject(item.objectKey());
                if (item.thumbnailKey() != null) {
                    storage.deleteObject(item.thumbnailKey());
                }
            } catch (RuntimeException e) {
                log.warn("Could not delete R2 objects for media {}; will retry next run: {}", item.id(), e.getMessage());
                continue;
            }
            erased += jdbc.update("DELETE FROM media WHERE id = ?", item.id());
        }
        if (erased > 0) {
            meters.counter("eventshare.purge.media").increment(erased);
        }
        return erased;
    }
}
