package com.eventshare.api.media;

import com.eventshare.api.analytics.AnalyticsService;
import com.eventshare.api.common.util.ClientIp;
import com.eventshare.api.common.util.Hashing;
import com.eventshare.api.event.EventRepository;
import com.eventshare.api.media.dto.CompleteUploadRequest;
import com.eventshare.api.media.dto.GalleryPageResponse;
import com.eventshare.api.media.dto.MediaResponse;
import com.eventshare.api.media.dto.UploadUrlRequest;
import com.eventshare.api.media.dto.UploadUrlResponse;
import com.eventshare.api.subscription.PlanLimitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Media")
@RestController
public class MediaController {

    private static final Logger log = LoggerFactory.getLogger(MediaController.class);

    private final MediaService mediaService;
    private final AnalyticsService analyticsService;
    private final PlanLimitService planLimitService;
    private final EventRepository events;

    public MediaController(MediaService mediaService, AnalyticsService analyticsService,
                           PlanLimitService planLimitService, EventRepository events) {
        this.mediaService = mediaService;
        this.analyticsService = analyticsService;
        this.planLimitService = planLimitService;
        this.events = events;
    }

    @Operation(summary = "Request a presigned upload URL (guest, by invite code)")
    @PostMapping("/api/media/upload-url")
    public UploadUrlResponse requestUploadUrl(@Valid @RequestBody UploadUrlRequest request,
                                              HttpServletRequest httpRequest) {
        // Enforce the event owner's plan limits before issuing an upload URL.
        events.findByInviteCodeAndDeletedAtIsNull(request.inviteCode()).ifPresent(event ->
                planLimitService.checkCanUpload(event,
                        MediaType.fromContentType(request.contentType()), request.sizeBytes()));
        return mediaService.requestUploadUrl(request, ClientIp.resolve(httpRequest));
    }

    @Operation(summary = "Confirm an upload completed; triggers async processing")
    @PostMapping("/api/media/{mediaId}/complete")
    public MediaResponse completeUpload(@PathVariable UUID mediaId,
                                        @Valid @RequestBody CompleteUploadRequest request,
                                        HttpServletRequest httpRequest) {
        return mediaService.completeUpload(mediaId, request, ClientIp.resolve(httpRequest));
    }

    @Operation(summary = "Shared gallery for an event (paginated, newest first)")
    @GetMapping("/api/events/code/{code}/media")
    public GalleryPageResponse gallery(@PathVariable String code,
                                       @RequestParam(required = false) String cursor,
                                       @RequestParam(required = false) Integer limit,
                                       HttpServletRequest httpRequest) {
        recordVisitQuietly(code, httpRequest);
        return mediaService.gallery(code, cursor, limit);
    }

    private void recordVisitQuietly(String code, HttpServletRequest httpRequest) {
        try {
            String ip = ClientIp.resolve(httpRequest);
            String userAgent = httpRequest.getHeader("User-Agent");
            String visitorKey = Hashing.sha256Hex(ip + "|" + (userAgent == null ? "" : userAgent));
            analyticsService.recordVisitByCode(code, visitorKey);
        } catch (Exception e) {
            log.debug("Visit tracking skipped for {}: {}", code, e.getMessage());
        }
    }
}
