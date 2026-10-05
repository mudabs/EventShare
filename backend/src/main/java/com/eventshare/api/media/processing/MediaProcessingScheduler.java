package com.eventshare.api.media.processing;

import com.eventshare.api.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Polls the media table for assets that need processing and runs them in-process.
 *
 * <p>Claims rows in state UPLOADED, plus rows stuck in PROCESSING past a staleness
 * cutoff (recovery after a crash mid-processing). Claiming goes through
 * {@link MediaWorkQueue}, which uses {@code FOR UPDATE SKIP LOCKED}, so running more
 * than one API replica no longer processes the same asset twice (change C5).
 * {@code fixedDelay} still prevents overlapping polls within one instance.
 * Processing is sequential to keep memory bounded on small hosts (a video invokes ffmpeg, which spawns a subprocess); raise
 * {@code batch-size} or introduce an executor if you scale the box up.
 */
@Component
public class MediaProcessingScheduler {

    private static final Logger log = LoggerFactory.getLogger(MediaProcessingScheduler.class);

    private final MediaWorkQueue workQueue;
    private final MediaProcessingService processor;
    private final boolean enabled;
    private final int batchSize;
    private final Duration staleAfter;

    public MediaProcessingScheduler(MediaWorkQueue workQueue,
                                    MediaProcessingService processor,
                                    AppProperties props) {
        this.workQueue = workQueue;
        this.processor = processor;
        this.enabled = props.processing().enabled();
        this.batchSize = props.processing().batchSize();
        this.staleAfter = Duration.ofSeconds(props.processing().staleAfterSeconds());
    }

    @Scheduled(
            fixedDelayString = "${eventshare.processing.poll-interval-ms:5000}",
            initialDelayString = "${eventshare.processing.initial-delay-ms:10000}")
    public void poll() {
        if (!enabled) {
            return;
        }
        try {
            Instant staleCutoff = Instant.now().minus(staleAfter);
            List<UUID> claimed = workQueue.claim(batchSize, staleCutoff);
            for (UUID mediaId : claimed) {
                processor.process(mediaId);
            }
        } catch (Exception e) {
            // Never let a poll failure kill the scheduler; the next tick retries.
            log.error("Media processing poll failed: {}", e.getMessage(), e);
        }
    }
}
