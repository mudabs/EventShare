package com.eventshare.api.subscription.dto;

import jakarta.validation.constraints.NotBlank;

public record CheckoutRequest(@NotBlank String planCode) {
}
