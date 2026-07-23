package com.eventshare.api.media.processing;

/** Dimensions plus optional duration extracted from a video. */
public record VideoMeta(int width, int height, Double durationSeconds) {
}
