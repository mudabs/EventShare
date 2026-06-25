package com.eventshare.api.media;

/** Host-controlled visibility. HIDDEN/ARCHIVED stay recoverable; DELETED is terminal. */
public enum ModerationState {
    VISIBLE,
    HIDDEN,
    ARCHIVED,
    DELETED
}
