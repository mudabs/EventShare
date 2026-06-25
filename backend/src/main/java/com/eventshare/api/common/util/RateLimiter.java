package com.eventshare.api.common.util;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fixed-window, in-process rate limiter keyed by an arbitrary string (typically
 * "action:ip"). Adequate for a single-VPS deployment. For horizontal scale-out,
 * replace the backing store with Redis so the window is shared across instances.
 */
@Component
public class RateLimiter {

    private static final int MAX_TRACKED_KEYS = 100_000;

    private record Window(long minute, AtomicInteger count) {
    }

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    /**
     * @return true if the call is allowed; false if the per-minute quota is exceeded.
     */
    public boolean tryAcquire(String key, int maxPerMinute) {
        long minute = Instant.now().getEpochSecond() / 60;
        if (windows.size() > MAX_TRACKED_KEYS) {
            windows.entrySet().removeIf(e -> e.getValue().minute() != minute);
        }
        Window window = windows.compute(key, (k, current) ->
                (current == null || current.minute() != minute)
                        ? new Window(minute, new AtomicInteger(0))
                        : current);
        return window.count().incrementAndGet() <= maxPerMinute;
    }
}
