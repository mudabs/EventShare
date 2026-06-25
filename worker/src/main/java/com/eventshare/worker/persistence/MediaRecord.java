package com.eventshare.worker.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Slim view of the {@code media} table: only the columns the worker reads or
 * writes. Hibernate runs in validate mode, so these mappings are checked against
 * the schema the API migrated. Unmapped columns are left untouched on update.
 */
@Getter
@Setter
@Entity
@Table(name = "media")
public class MediaRecord {

    @Id
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "object_key")
    private String objectKey;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "media_type")
    private String mediaType;

    @Column(name = "status")
    private String status;

    @Column(name = "thumbnail_key")
    private String thumbnailKey;

    @Column(name = "width")
    private Integer width;

    @Column(name = "height")
    private Integer height;

    @Column(name = "duration_seconds")
    private BigDecimal durationSeconds;
}
