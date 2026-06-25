package com.eventshare.worker.processing;

import java.util.UUID;

/** Pure helpers for deriving file suffixes and thumbnail keys. */
public final class MediaFiles {

    private MediaFiles() {
    }

    public static String thumbnailKey(UUID eventId, UUID mediaId) {
        return "events/" + eventId + "/thumbnails/" + mediaId + ".jpg";
    }

    /**
     * Best-effort suffix for the temp file holding the downloaded original.
     * Tries the filename extension, then a coarse map from content type.
     */
    public static String suffixFor(String filename, String contentType) {
        if (filename != null) {
            int dot = filename.lastIndexOf('.');
            if (dot >= 0 && dot < filename.length() - 1) {
                String ext = filename.substring(dot + 1).toLowerCase();
                if (ext.matches("[a-z0-9]{1,5}")) {
                    return "." + ext;
                }
            }
        }
        if (contentType != null) {
            return switch (contentType.toLowerCase()) {
                case "image/jpeg" -> ".jpg";
                case "image/png" -> ".png";
                case "image/webp" -> ".webp";
                case "image/gif" -> ".gif";
                case "video/mp4" -> ".mp4";
                case "video/quicktime" -> ".mov";
                case "video/webm" -> ".webm";
                default -> ".bin";
            };
        }
        return ".bin";
    }
}
