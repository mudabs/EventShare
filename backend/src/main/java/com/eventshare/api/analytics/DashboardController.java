package com.eventshare.api.analytics;

import com.eventshare.api.analytics.dto.OwnerDashboardResponse;
import com.eventshare.api.analytics.dto.UserDashboardResponse;
import com.eventshare.api.common.security.CurrentUser;
import com.eventshare.api.user.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Dashboards")
@RestController
public class DashboardController {

    private final AnalyticsService analyticsService;

    public DashboardController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @Operation(summary = "Logged-in user dashboard summary")
    @GetMapping("/api/me/dashboard")
    public UserDashboardResponse userDashboard(@CurrentUser User user) {
        return analyticsService.userDashboard(user);
    }

    @Operation(summary = "Owner analytics for an event")
    @GetMapping("/api/events/{eventId}/dashboard")
    public OwnerDashboardResponse ownerDashboard(@CurrentUser User host, @PathVariable UUID eventId) {
        return analyticsService.ownerDashboard(host, eventId);
    }
}
