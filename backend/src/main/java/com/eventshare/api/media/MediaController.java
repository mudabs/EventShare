package com.eventshare.api.media;

import com.eventshare.api.analytics.AnalyticsService;
import com.eventshare.api.common.util.ClientIp;
import com.eventshare.api.common.util.Hashing;
import com.eventshare.api.media.dto.CompleteUploadRequest;
import com.eventshare.api.media.dto.DeleteOwnMediaRequest;
import com.eventshare.api.media.dto.GalleryPageResponse;
import com.eventshare.api.media.dto.MediaResponse;
import com.eventshare.api.media.dto.UploadUrlRequest;
import com.eventshare.api.media.dto.UploadUrlResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.DeleteMapping;

import java.util.UUID;

@Tag(name = "Media")
@RestController
public class MediaController {

    private static final Logger log = LoggerFactory.getLogger(MediaController.class);

    private final MediaService mediaService;
    private final AnalyticsService analyticsService;

    public MediaController(MediaService mediaService, AnalyticsService analyticsService) {
        this.mediaService = mediaService;
        this.analyticsService = analyticsService;
    }

    @Operation(summary = "Request a presigned upload URL (guest, by invite code)")
    @PostMapping("/api/media/upload-url")
    public UploadUrlResponse requestUploadUrl(@Valid @RequestBody UploadUrlRequest request,
                                              HttpServletRequest httpRequest) {
        // Plan limits are enforced inside MediaService.requestUploadUrl, in the same
        // transaction as the insert and under a host-row lock (change C3).
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
                                       @RequestHeader(value = "X-Membership-Id", required = false)
                                       UUID membershipId,
                                       HttpServletRequest httpRequest) {
        recordVisitQuietly(code, httpRequest);
        return mediaService.gallery(code, cursor, limit, membershipId);
    }

    @Operation(summary = "Delete your own uploaded media item from the shared gallery")
    @DeleteMapping("/api/events/code/{code}/media/{mediaId}")
    public void deleteOwnMedia(@PathVariable String code,
                               @PathVariable UUID mediaId,
                               @Valid @RequestBody DeleteOwnMediaRequest request,
                               HttpServletRequest httpRequest) {
        mediaService.deleteOwnMedia(code, mediaId, request, ClientIp.resolve(httpRequest));
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
