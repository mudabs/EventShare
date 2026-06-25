package com.eventshare.api.promo.dto;

import com.eventshare.api.promo.PromoCode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PromoCodeResponse(
        UUID id,
        String code,
        String type,
        BigDecimal valueNumeric,
        String grantsPlanCode,
        Integer durationDays,
        Integer maxRedemptions,
        int redemptionsUsed,
        Instant expiresAt,
        boolean active
) {
    public static PromoCodeResponse from(PromoCode p) {
        return new PromoCodeResponse(p.getId(), p.getCode(), p.getType().name(), p.getValueNumeric(),
                p.getGrantsPlanCode(), p.getDurationDays(), p.getMaxRedemptions(), p.getRedemptionsUsed(),
                p.getExpiresAt(), p.isActive());
    }
}
