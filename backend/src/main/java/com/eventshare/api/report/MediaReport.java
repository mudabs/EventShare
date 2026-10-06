package com.eventshare.api.report;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** A visitor's report about one photo or video (V12). */
@Getter
@Setter
@Entity
@Table(name = "media_reports")
public class MediaReport {

    @Id
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "media_id", nullable = false)
    private UUID mediaId;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 24)
    private ReportReason reason;

    @Column(name = "details", length = 500)
    private String details;

    @Column(name = "reporter_ip_hash", nullable = false, length = 64)
    private String reporterIpHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "resolved_at")
    private Instant resolvedAt;
}
