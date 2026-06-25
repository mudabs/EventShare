package com.eventshare.api.media;

/** Processing lifecycle of an uploaded asset. */
public enum MediaStatus {
    PENDING,
    UPLOADED,
    PROCESSING,
    PROCESSED,
    FAILED
}
