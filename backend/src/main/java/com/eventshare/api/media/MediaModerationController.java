package com.eventshare.api.media;

import com.eventshare.api.common.security.CurrentUser;
import com.eventshare.api.media.dto.GalleryPageResponse;
import com.eventshare.api.media.dto.MediaResponse;
import com.eventshare.api.media.dto.ModerationRequest;
import com.eventshare.api.user.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Moderation")
@RestController
public class MediaModerationController {

    private final MediaModerationService moderation;

    public MediaModerationController(MediaModerationService moderation) {
        this.moderation = moderation;
    }

    @Operation(summary = "Owner gallery including non-visible states")
    @GetMapping("/api/events/{eventId}/media")
    public GalleryPageResponse ownerGallery(@CurrentUser User host,
                                            @PathVariable UUID eventId,
                                            @RequestParam(required = false, defaultValue = "VISIBLE") ModerationState state,
                                            @RequestParam(required = false) String cursor,
                                            @RequestParam(required = false) Integer limit) {
        return moderation.ownerGallery(host, eventId, state, cursor, limit);
    }

    @Operation(summary = "Hide / unhide / archive / restore / delete a media item")
    @PatchMapping("/api/events/{eventId}/media/{mediaId}/moderation")
    public MediaResponse moderate(@CurrentUser User host,
                                  @PathVariable UUID eventId,
                                  @PathVariable UUID mediaId,
                                  @Valid @RequestBody ModerationRequest request) {
        return moderation.moderate(host, eventId, mediaId, request.action());
    }

    @Operation(summary = "Permanently delete a media item (removes R2 objects)")
    @DeleteMapping("/api/events/{eventId}/media/{mediaId}/permanent")
    public ResponseEntity<Void> permanentDelete(@CurrentUser User host,
                                                @PathVariable UUID eventId,
                                                @PathVariable UUID mediaId) {
        moderation.permanentDelete(host, eventId, mediaId);
        return ResponseEntity.noContent().build();
    }
}
