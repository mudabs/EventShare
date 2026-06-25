package com.eventshare.api.event.dto;

import com.eventshare.api.event.EventType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreateEventRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 5000) String description,
        @NotNull EventType eventType,
        LocalDate eventDate,
        Boolean allowGuestDownloads,
        Boolean autoApprove
) {
}
