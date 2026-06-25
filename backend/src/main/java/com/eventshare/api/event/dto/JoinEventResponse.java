package com.eventshare.api.event.dto;

import java.util.UUID;

public record JoinEventResponse(
        UUID membershipId,
        UUID eventId,
        String inviteCode,
        String eventName,
        String displayName
) {
}
