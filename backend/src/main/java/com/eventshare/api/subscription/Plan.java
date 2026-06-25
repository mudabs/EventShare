package com.eventshare.api.subscription;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A subscription plan and its limits. Reference data seeded by migration V5 and
 * editable by admins. Nullable numeric limits mean "unlimited".
 */
@Getter
@Setter
@Entity
@Table(name = "plans")
public class Plan {

    @Id
    @Column(name = "code")
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "price_cents", nullable = false)
    private int priceCents;

    @Column(name = "billing_interval", nullable = false)
    private String billingInterval;

    @Column(name = "stripe_price_id")
    private String stripePriceId;

    @Column(name = "max_events")
    private Integer maxEvents;

    @Column(name = "max_guests_per_event")
    private Integer maxGuestsPerEvent;

    @Column(name = "max_photos_per_event")
    private Integer maxPhotosPerEvent;

    @Column(name = "max_videos_per_event")
    private Integer maxVideosPerEvent;

    @Column(name = "storage_bytes")
    private Long storageBytes;

    @Column(name = "zip_export", nullable = false)
    private boolean zipExport;

    @Column(name = "advanced_analytics", nullable = false)
    private boolean advancedAnalytics;

    @Column(name = "priority_processing", nullable = false)
    private boolean priorityProcessing;

    @Column(name = "retention_months")
    private Integer retentionMonths;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
