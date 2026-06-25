package com.eventshare.api.event.dto;

import jakarta.validation.constraints.NotBlank;

public record JoinByCodeRequest(@NotBlank String inviteCode) {
}
