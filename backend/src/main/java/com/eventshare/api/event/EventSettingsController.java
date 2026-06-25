package com.eventshare.api.event;

import com.eventshare.api.common.security.CurrentUser;
import com.eventshare.api.event.dto.EventSettingsResponse;
import com.eventshare.api.event.dto.UpdateEventSettingsRequest;
import com.eventshare.api.user.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Event settings")
@RestController
@RequestMapping("/api/events")
public class EventSettingsController {

    private final EventSettingsService settingsService;

    public EventSettingsController(EventSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @Operation(summary = "Get event settings (owner)")
    @GetMapping("/{eventId}/settings")
    public EventSettingsResponse get(@CurrentUser User host, @PathVariable UUID eventId) {
        return settingsService.getSettings(host, eventId);
    }

    @Operation(summary = "Update event settings (owner)")
    @PatchMapping("/{eventId}/settings")
    public EventSettingsResponse update(@CurrentUser User host,
                                        @PathVariable UUID eventId,
                                        @Valid @RequestBody UpdateEventSettingsRequest request) {
        return settingsService.updateSettings(host, eventId, request);
    }
}
