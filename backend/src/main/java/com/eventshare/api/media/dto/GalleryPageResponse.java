package com.eventshare.api.media.dto;

import java.util.List;

public record GalleryPageResponse(
        List<MediaResponse> items,
        String nextCursor,
        boolean hasMore
) {
}
