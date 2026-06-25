package com.eventshare.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Strongly-typed view of the {@code eventshare.*} configuration tree.
 * Bound once at startup; injected wherever configuration is needed instead of
 * scattering {@code @Value} lookups across the codebase.
 */
@ConfigurationProperties(prefix = "eventshare")
public record AppProperties(
        String appBaseUrl,
        Cors cors,
        Auth auth,
        R2 r2,
        Media media,
        RateLimit ratelimit
) {
    public record Cors(String allowedOrigins) {}

    public record Auth(String clerkIssuer, String clerkAudience) {}

    public record R2(
            String endpoint,
            String region,
            String accessKeyId,
            String secretAccessKey,
            String bucket,
            long presignUploadTtlSeconds,
            long presignDownloadTtlSeconds
    ) {}

    public record Media(long maxUploadBytes, String allowedContentTypes) {}

    public record RateLimit(int uploadRequestsPerMinute, int joinRequestsPerMinute) {}
}
