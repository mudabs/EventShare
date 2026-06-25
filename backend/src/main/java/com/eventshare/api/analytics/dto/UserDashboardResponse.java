package com.eventshare.api.analytics.dto;

import com.eventshare.api.event.dto.MyEventCard;

import java.util.List;

public record UserDashboardResponse(
        long eventsOwned,
        long eventsJoined,
        long totalPhotos,
        long totalVideos,
        List<MyEventCard> recentEvents
) {
}
