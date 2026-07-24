package com.eventshare.api.admin.dto;

import java.util.List;

/**
 * Lightweight runtime performance snapshot read from the in-process Micrometer
 * registry (the same source Prometheus scrapes), so admins get a live view
 * without needing Grafana.
 */
public record AdminPerformanceResponse(
        double uptimeSeconds,
        double cpuUsage,          // 0..1, process CPU (0 if unavailable)
        long heapUsedBytes,
        long heapMaxBytes,
        long nonHeapUsedBytes,
        int liveThreads,
        long totalRequests,
        long serverErrors,
        double avgLatencyMs,
        double dbActive,
        double dbIdle,
        double dbMax,
        long mediaProcessed,
        long mediaFailed,
        List<EndpointStat> topEndpoints
) {
    /** Per-endpoint request stats aggregated from http.server.requests timers. */
    public record EndpointStat(String uri, long count, double avgMs, long errors) {}
}
