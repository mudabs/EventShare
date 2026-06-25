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
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "events")
public class Event extends BaseEntity {

    @Column(name = "host_id", nullable = false)
    private UUID hostId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private EventType eventType;

    @Column(name = "invite_code", nullable = false)
    private String inviteCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private EventStatus status = EventStatus.ACTIVE;

    @Column(name = "event_date")
    private LocalDate eventDate;

    @Column(name = "allow_guest_downloads", nullable = false)
    private boolean allowGuestDownloads = true;

    @Column(name = "auto_approve", nullable = false)
    private boolean autoApprove = true;

    @Column(name = "max_upload_bytes")
    private Long maxUploadBytes;

    // ---- V2: cover, retention, and privacy settings ----

    @Column(name = "cover_media_id")
    private UUID coverMediaId;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "uploader_visibility", nullable = false)
    private UploaderVisibility uploaderVisibility = UploaderVisibility.NAMED;

    @Column(name = "show_upload_timestamps", nullable = false)
    private boolean showUploadTimestamps = true;

    @Column(name = "show_uploader_names", nullable = false)
    private boolean showUploaderNames = true;

    @Column(name = "show_upload_stats", nullable = false)
    private boolean showUploadStats = true;

    public boolean isActive() {
        return status == EventStatus.ACTIVE && !isDeleted();
    }
}
