package com.eventshare.api.promo;

import com.eventshare.api.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "promo_codes")
public class PromoCode extends BaseEntity {

    @Column(name = "code", nullable = false)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private PromoCodeType type;

    @Column(name = "value_numeric")
    private BigDecimal valueNumeric;

    @Column(name = "grants_plan_code")
    private String grantsPlanCode;

    @Column(name = "duration_days")
    private Integer durationDays;

    @Column(name = "max_redemptions")
    private Integer maxRedemptions;

    @Column(name = "redemptions_used", nullable = false)
    private int redemptionsUsed = 0;

    @Column(name = "stripe_coupon_id")
    private String stripeCouponId;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_by")
    private UUID createdBy;

    public boolean isRedeemable() {
        if (!active) {
            return false;
        }
        if (expiresAt != null && expiresAt.isBefore(Instant.now())) {
            return false;
        }
        return maxRedemptions == null || redemptionsUsed < maxRedemptions;
    }
}
