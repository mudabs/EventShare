package com.eventshare.api.event;

import com.eventshare.api.common.security.CurrentUser;
import com.eventshare.api.common.util.ClientIp;
import com.eventshare.api.event.dto.CreateEventRequest;
import com.eventshare.api.event.dto.EventAnalyticsResponse;
import com.eventshare.api.event.dto.EventResponse;
import com.eventshare.api.event.dto.JoinEventRequest;
import com.eventshare.api.event.dto.JoinEventResponse;
import com.eventshare.api.event.dto.PublicEventResponse;
import com.eventshare.api.subscription.PlanLimitService;
import com.eventshare.api.user.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@Tag(name = "Events")
@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventService eventService;
    private final PlanLimitService planLimitService;

    public EventController(EventService eventService, PlanLimitService planLimitService) {
        this.eventService = eventService;
        this.planLimitService = planLimitService;
    }

    @Operation(summary = "Create an event (host)")
    @PostMapping
    public ResponseEntity<EventResponse> create(@CurrentUser User host,
                                                @Valid @RequestBody CreateEventRequest request) {
        planLimitService.checkCanCreateEvent(host);
        EventResponse response = eventService.createEvent(host, request);
        return ResponseEntity.created(URI.create("/api/events/" + response.id())).body(response);
    }

    @Operation(summary = "Get an event you host")
    @GetMapping("/{id}")
    public EventResponse get(@CurrentUser User host, @PathVariable UUID id) {
        return eventService.getEvent(host, id);
    }

    @Operation(summary = "Event analytics (host)")
    @GetMapping("/{id}/analytics")
    public EventAnalyticsResponse analytics(@CurrentUser User host, @PathVariable UUID id) {
        return eventService.analytics(host, id);
    }

    @Operation(summary = "Public event summary for the join page")
    @GetMapping("/code/{code}")
    public PublicEventResponse publicByCode(@PathVariable String code) {
        return eventService.getPublicByInviteCode(code);
    }

    @Operation(summary = "Join an event as a guest (no account required)")
    @PostMapping("/code/{code}/join")
    public JoinEventResponse join(@PathVariable String code,
                                  @Valid @RequestBody JoinEventRequest request,
                                  HttpServletRequest httpRequest) {
        return eventService.join(code, request, ClientIp.resolve(httpRequest));
    }
}
