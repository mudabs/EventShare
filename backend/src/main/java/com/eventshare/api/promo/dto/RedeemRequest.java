package com.eventshare.api.promo.dto;

import jakarta.validation.constraints.NotBlank;

public record RedeemRequest(@NotBlank String code) {
}
