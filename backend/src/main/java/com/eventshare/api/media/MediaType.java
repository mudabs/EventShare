package com.eventshare.api.media;

public enum MediaType {
    PHOTO,
    VIDEO;

    /** Maps a MIME type to a coarse media class. */
    public static MediaType fromContentType(String contentType) {
        if (contentType == null) {
            return PHOTO;
        }
        return contentType.toLowerCase().startsWith("video/") ? VIDEO : PHOTO;
    }
}
