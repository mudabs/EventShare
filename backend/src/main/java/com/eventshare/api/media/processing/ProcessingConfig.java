package com.eventshare.api.media.processing;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables Spring's scheduler so {@link MediaProcessingScheduler} can poll the media
 * table for work. Scheduling replaces the RabbitMQ listener the standalone worker
 * used, keeping media processing inside the single API deployable.
 */
@Configuration
@EnableScheduling
public class ProcessingConfig {
}
