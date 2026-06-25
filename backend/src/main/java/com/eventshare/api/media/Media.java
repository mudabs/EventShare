package com.eventshare.api.media;

import com.eventshare.api.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Metadata for one uploaded photo or video. The bytes themselves live in R2;
 * {@code objectKey} points at the original and {@code thumbnailKey} at the
 * worker-generated preview.
 */
@Getter
@Setter
@Entity
@Table(name = "media")
public class Media extends BaseEntity {

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "uploader_membership_id")
    private UUID uploaderMembershipId;

    @Column(name = "uploader_user_id")
    private UUID uploaderUserId;

    @Column(name = "uploader_display_name")
    private String uploaderDisplayName;

    @Column(name = "original_filename")
    private String originalFilename;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false)
    private MediaType mediaType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "object_key", nullable = false)
    private String objectKey;

    @Column(name = "thumbnail_key")
    private String thumbnailKey;

    @Column(name = "sha256")
    private String sha256;

    @Column(name = "width")
    private Integer width;

    @Column(name = "height")
    private Integer height;

    @Column(name = "duration_seconds")
    private BigDecimal durationSeconds;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private MediaStatus status = MediaStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "moderation_state", nullable = false)
    private ModerationState moderationState = ModerationState.VISIBLE;

    @Column(name = "is_duplicate", nullable = false)
    private boolean duplicate = false;

    @Column(name = "duplicate_of_id")
    private UUID duplicateOfId;
}
