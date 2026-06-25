package com.eventshare.api.common;

import com.eventshare.api.common.util.RateLimiter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterTest {

    private final RateLimiter limiter = new RateLimiter();

    @Test
    void allowsUpToQuotaThenBlocks() {
        String key = "upload:203.0.113.5";
        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryAcquire(key, 5)).isTrue();
        }
        assertThat(limiter.tryAcquire(key, 5)).isFalse();
    }

    @Test
    void quotaIsPerKey() {
        assertThat(limiter.tryAcquire("a", 1)).isTrue();
        assertThat(limiter.tryAcquire("a", 1)).isFalse();
        assertThat(limiter.tryAcquire("b", 1)).isTrue();
    }
}
