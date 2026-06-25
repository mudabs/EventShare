package com.eventshare.api.promo.dto;

import com.eventshare.api.promo.PromoCodeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

public record CreatePromoRequest(
        @NotBlank String code,
        @NotNull PromoCodeType type,
        BigDecimal valueNumeric,
        String grantsPlanCode,
        Integer durationDays,
        Integer maxRedemptions,
        Instant expiresAt
) {
}
