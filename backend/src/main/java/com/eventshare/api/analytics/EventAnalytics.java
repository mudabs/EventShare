package com.eventshare.api.analytics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One incrementally-maintained rollup row per event. event_id is the primary key. */
@Getter
@Setter
@Entity
@Table(name = "event_analytics")
public class EventAnalytics {

    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "photo_count", nullable = false)
    private int photoCount = 0;

    @Column(name = "video_count", nullable = false)
    private int videoCount = 0;

    @Column(name = "total_bytes", nullable = false)
    private long totalBytes = 0;

    @Column(name = "unique_visitors", nullable = false)
    private int uniqueVisitors = 0;

    @Column(name = "active_guests_7d", nullable = false)
    private int activeGuests7d = 0;

    @Column(name = "uploads_today", nullable = false)
    private int uploadsToday = 0;

    @Column(name = "uploads_today_date")
    private LocalDate uploadsTodayDate;

    @Column(name = "last_activity_at")
    private Instant lastActivityAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
