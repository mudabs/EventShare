package com.eventshare.api.admin;

import com.eventshare.api.admin.dto.AdminPerformanceResponse;
import com.eventshare.api.admin.dto.AdminPerformanceResponse.EndpointStat;
import com.eventshare.api.common.error.ForbiddenException;
import com.eventshare.api.user.Role;
import com.eventshare.api.user.User;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Reads a curated set of runtime metrics from the Micrometer registry for the
 * in-app admin performance panel. All reads are cheap in-memory gauge/timer
 * lookups — no external calls.
 */
@Service
public class AdminMetricsService {

    private static final int TOP_ENDPOINTS = 8;

    private final MeterRegistry registry;

    public AdminMetricsService(MeterRegistry registry) {
        this.registry = registry;
    }

    public AdminPerformanceResponse performance(User admin) {
        if (admin.getRole() != Role.ADMIN) {
            throw new ForbiddenException("Administrator access required");
        }

        double cpu = gauge("process.cpu.usage");
        if (Double.isNaN(cpu) || cpu < 0) {
            cpu = 0;
        }

        // --- HTTP requests (aggregate + per-endpoint) ---
        long totalRequests = 0;
        long serverErrors = 0;
        double totalTimeMs = 0;
        Map<String, long[]> byUriCount = new HashMap<>();   // uri -> {count, errors}
        Map<String, Double> byUriTimeMs = new HashMap<>();

        for (Timer timer : registry.find("http.server.requests").timers()) {
            long count = timer.count();
            double timeMs = timer.totalTime(TimeUnit.MILLISECONDS);
            totalRequests += count;
            totalTimeMs += timeMs;

            String uri = tagOr(timer, "uri", "unknown");
            boolean isError = "SERVER_ERROR".equals(timer.getId().getTag("outcome"))
                    || startsWith5(timer.getId().getTag("status"));
            if (isError) {
                serverErrors += count;
            }
            long[] agg = byUriCount.computeIfAbsent(uri, k -> new long[2]);
            agg[0] += count;
            if (isError) {
                agg[1] += count;
            }
            byUriTimeMs.merge(uri, timeMs, Double::sum);
        }

        List<EndpointStat> top = new ArrayList<>();
        for (Map.Entry<String, long[]> e : byUriCount.entrySet()) {
            long count = e.getValue()[0];
            if (count == 0) {
                continue;
            }
            double avg = byUriTimeMs.getOrDefault(e.getKey(), 0.0) / count;
            top.add(new EndpointStat(e.getKey(), count, round(avg), e.getValue()[1]));
        }
        top.sort(Comparator.comparingLong(EndpointStat::count).reversed());
        if (top.size() > TOP_ENDPOINTS) {
            top = top.subList(0, TOP_ENDPOINTS);
        }

        double avgLatency = totalRequests > 0 ? round(totalTimeMs / totalRequests) : 0;

        return new AdminPerformanceResponse(
                gauge("process.uptime"),
                round4(cpu),
                (long) gaugeSum("jvm.memory.used", "area", "heap"),
                (long) gaugeSum("jvm.memory.max", "area", "heap"),
                (long) gaugeSum("jvm.memory.used", "area", "nonheap"),
                (int) gauge("jvm.threads.live"),
                totalRequests,
                serverErrors,
                avgLatency,
                gauge("hikaricp.connections.active"),
                gauge("hikaricp.connections.idle"),
                gauge("hikaricp.connections.max"),
                (long) counterSum("eventshare.media.processed"),
                (long) counterSum("eventshare.media.processing.failed"),
                top);
    }

    // ---- meter helpers ----

    private double gauge(String name) {
        double sum = 0;
        boolean found = false;
        for (Gauge g : registry.find(name).gauges()) {
            double v = g.value();
            if (!Double.isNaN(v)) {
                sum += v;
                found = true;
            }
        }
        return found ? sum : 0;
    }

    private double gaugeSum(String name, String tagKey, String tagValue) {
        double sum = 0;
        for (Gauge g : registry.find(name).tag(tagKey, tagValue).gauges()) {
            double v = g.value();
            if (!Double.isNaN(v)) {
                sum += v;
            }
        }
        return sum;
    }

    private double counterSum(String name) {
        double sum = 0;
        for (var c : registry.find(name).counters()) {
            sum += c.count();
        }
        return sum;
    }

    private static String tagOr(Timer timer, String key, String fallback) {
        String value = timer.getId().getTag(key);
        return value == null ? fallback : value;
    }

    private static boolean startsWith5(String status) {
        return status != null && status.startsWith("5");
    }

    private static double round(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static double round4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}
