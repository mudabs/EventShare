package com.eventshare.api.common.util;

import java.util.UUID;

/**
 * Builds deterministic R2 object keys. Layout keeps every event self-contained,
 * which makes per-event export and lifecycle rules straightforward:
 *
 * <pre>
 *   events/{eventId}/originals/{mediaId}/{safeFilename}
 *   events/{eventId}/thumbnails/{mediaId}.jpg
 *   events/{eventId}/exports/{downloadId}.zip
 * </pre>
 */
public final class ObjectKeys {

    private ObjectKeys() {
    }

    public static String original(UUID eventId, UUID mediaId, String filename) {
        return "events/" + eventId + "/originals/" + mediaId + "/" + sanitize(filename);
    }

    public static String thumbnail(UUID eventId, UUID mediaId) {
        return "events/" + eventId + "/thumbnails/" + mediaId + ".jpg";
    }

    public static String export(UUID eventId, UUID downloadId) {
        return "events/" + eventId + "/exports/" + downloadId + ".zip";
    }

    /**
     * Reduces an arbitrary client filename to a safe basename: strips any path,
     * keeps a conservative character set, and falls back when nothing remains.
     */
    public static String sanitize(String filename) {
        if (filename == null || filename.isBlank()) {
            return "file";
        }
        String base = filename.replace('\\', '/');
        int slash = base.lastIndexOf('/');
        if (slash >= 0) {
            base = base.substring(slash + 1);
        }
        base = base.replaceAll("[^A-Za-z0-9._-]", "_");
        if (base.length() > 180) {
            base = base.substring(base.length() - 180);
        }
        return base.isBlank() ? "file" : base;
    }
}
