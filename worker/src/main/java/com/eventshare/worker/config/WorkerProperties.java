package com.eventshare.worker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "eventshare")
public record WorkerProperties(R2 r2, Processing processing) {

    public record R2(
            String endpoint,
            String region,
            String accessKeyId,
            String secretAccessKey,
            String bucket
    ) {}

    public record Processing(int thumbnailMaxDimension, String videoFrameTimestamp) {}
}
