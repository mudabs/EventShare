package com.eventshare.api.media.processing;

import com.eventshare.api.config.AppProperties;
import com.eventshare.api.media.Media;
import com.eventshare.api.media.MediaRepository;
import com.eventshare.api.media.MediaStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Polls the media table for assets that need processing and runs them in-process.
 *
 * <p>Claims rows in state UPLOADED, plus rows stuck in PROCESSING past a staleness
 * cutoff (recovery after a crash mid-processing). Uses {@code fixedDelay} so a new
 * poll never overlaps the previous one; combined with a single API instance this
 * removes any need for row locking. Processing is sequential to keep memory bounded
 * on small hosts (a video invokes ffmpeg, which spawns a subprocess); raise
 * {@code batch-size} or introduce an executor if you scale the box up.
 */
@Component
public class MediaProcessingScheduler {

    private static final Logger log = LoggerFactory.getLogger(MediaProcessingScheduler.class);

    private final MediaRepository repository;
    private final MediaProcessingService processor;
    private final boolean enabled;
    private final int batchSize;
    private final Duration staleAfter;

    public MediaProcessingScheduler(MediaRepository repository,
                                    MediaProcessingService processor,
                                    AppProperties props) {
        this.repository = repository;
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
            List<Media> batch = repository.findProcessableBatch(
                    MediaStatus.UPLOADED, MediaStatus.PROCESSING, staleCutoff,
                    PageRequest.of(0, batchSize));
            for (Media media : batch) {
                processor.process(media.getId());
            }
        } catch (Exception e) {
            // Never let a poll failure kill the scheduler; the next tick retries.
            log.error("Media processing poll failed: {}", e.getMessage(), e);
        }
    }
}
