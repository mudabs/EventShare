package com.eventshare.api.analytics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/** Per-account storage accounting for fast quota checks. user_id is the primary key. */
@Getter
@Setter
@Entity
@Table(name = "storage_usage")
public class StorageUsage {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "used_bytes", nullable = false)
    private long usedBytes = 0;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
