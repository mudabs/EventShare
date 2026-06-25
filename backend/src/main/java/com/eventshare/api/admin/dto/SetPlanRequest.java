package com.eventshare.api.admin.dto;

import jakarta.validation.constraints.NotBlank;

public record SetPlanRequest(@NotBlank String planCode) {
}
