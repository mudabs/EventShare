package com.eventshare.worker.processing;

import com.eventshare.worker.messaging.MediaUploadedEvent;
import com.eventshare.worker.persistence.MediaRecord;
import com.eventshare.worker.persistence.MediaRecordRepository;
import com.eventshare.worker.r2.R2ObjectStore;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Orchestrates processing of a single uploaded asset: download the original from
 * R2, generate a thumbnail (and metadata), upload the thumbnail, and update the
 * media row. Failures mark the row FAILED and rethrow so the broker can retry and
 * ultimately dead-letter a poison message.
 */
@Service
public class MediaProcessor {

    private static final Logger log = LoggerFactory.getLogger(MediaProcessor.class);

    private final MediaRecordRepository repository;
    private final R2ObjectStore store;
    private final ImageThumbnailer imageThumbnailer;
    private final VideoThumbnailer videoThumbnailer;
    private final MeterRegistry meterRegistry;

    public MediaProcessor(MediaRecordRepository repository,
                          R2ObjectStore store,
                          ImageThumbnailer imageThumbnailer,
                          VideoThumbnailer videoThumbnailer,
                          MeterRegistry meterRegistry) {
        this.repository = repository;
        this.store = store;
        this.imageThumbnailer = imageThumbnailer;
        this.videoThumbnailer = videoThumbnailer;
        this.meterRegistry = meterRegistry;
    }

    public void process(MediaUploadedEvent event) {
        MediaRecord record = repository.findById(event.mediaId()).orElse(null);
        if (record == null) {
            log.warn("Media {} no longer exists; skipping", event.mediaId());
            return;
        }

        Timer.Sample sample = Timer.start(meterRegistry);
        Path original = null;
        Path thumbnail = null;
        try {
            record.setStatus("PROCESSING");
            repository.save(record);

            original = store.download(event.objectKey(),
                    MediaFiles.suffixFor(event.originalFilename(), event.contentType()));
            thumbnail = Files.createTempFile("es-thumb-", ".jpg");
            Files.deleteIfExists(thumbnail);

            if ("VIDEO".equalsIgnoreCase(event.mediaType())) {
                VideoMeta meta = videoThumbnailer.generate(original, thumbnail);
                record.setWidth(meta.width());
                record.setHeight(meta.height());
                if (meta.durationSeconds() != null) {
                    record.setDurationSeconds(BigDecimal.valueOf(meta.durationSeconds()));
                }
            } else {
                Dimensions dims = imageThumbnailer.generate(original, thumbnail);
                record.setWidth(dims.width());
                record.setHeight(dims.height());
            }

            String thumbnailKey = MediaFiles.thumbnailKey(event.eventId(), event.mediaId());
            store.upload(thumbnail, thumbnailKey, "image/jpeg");
            record.setThumbnailKey(thumbnailKey);
            record.setStatus("PROCESSED");
            repository.save(record);

            meterRegistry.counter("eventshare.worker.processed", "mediaType", event.mediaType()).increment();
            log.info("Processed media {} ({}) -> {}", event.mediaId(), event.mediaType(), thumbnailKey);
        } catch (Exception e) {
            meterRegistry.counter("eventshare.worker.failed", "mediaType", String.valueOf(event.mediaType())).increment();
            markFailed(record);
            throw new RuntimeException("Processing failed for media " + event.mediaId(), e);
        } finally {
            sample.stop(meterRegistry.timer("eventshare.worker.processing.time",
                    "mediaType", String.valueOf(event.mediaType())));
            deleteQuietly(original);
            deleteQuietly(thumbnail);
        }
    }

    private void markFailed(MediaRecord record) {
        try {
            record.setStatus("FAILED");
            repository.save(record);
        } catch (Exception ignored) {
            log.error("Could not mark media {} as FAILED", record.getId());
        }
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // best-effort temp cleanup
        }
    }
}
