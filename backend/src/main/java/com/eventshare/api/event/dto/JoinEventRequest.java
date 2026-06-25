package com.eventshare.api.event.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JoinEventRequest(
        @NotBlank @Size(max = 120) String displayName
) {
}
