package com.eventshare.api.media.processing;

import com.eventshare.api.media.Media;
import com.eventshare.api.media.MediaRepository;
import com.eventshare.api.media.MediaStatus;
import com.eventshare.api.media.MediaType;
import com.eventshare.api.media.r2.R2StorageService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Processes a single uploaded asset in-process: download the original from R2,
 * generate a thumbnail (and metadata), upload the thumbnail, and update the media
 * row. This replaces the former standalone worker service; the DB status column
 * ({@code UPLOADED -> PROCESSING -> PROCESSED/FAILED}) is the work queue, polled by
 * {@link MediaProcessingScheduler}.
 *
 * <p>Not annotated {@code @Transactional}: each {@code save} commits independently so
 * the PROCESSING marker is durable immediately (and survives a crash for recovery),
 * mirroring the at-least-once semantics the broker previously provided.
 */
@Service
public class MediaProcessingService {

    private static final Logger log = LoggerFactory.getLogger(MediaProcessingService.class);

    private final MediaRepository repository;
    private final R2StorageService storage;
    private final ImageThumbnailer imageThumbnailer;
    private final VideoThumbnailer videoThumbnailer;
    private final MeterRegistry meterRegistry;

    public MediaProcessingService(MediaRepository repository,
                                  R2StorageService storage,
                                  ImageThumbnailer imageThumbnailer,
                                  VideoThumbnailer videoThumbnailer,
                                  MeterRegistry meterRegistry) {
        this.repository = repository;
        this.storage = storage;
        this.imageThumbnailer = imageThumbnailer;
        this.videoThumbnailer = videoThumbnailer;
        this.meterRegistry = meterRegistry;
    }

    public void process(UUID mediaId) {
        Media media = repository.findById(mediaId).orElse(null);
        if (media == null) {
            log.warn("Media {} no longer exists; skipping", mediaId);
            return;
        }

        String mediaTypeTag = media.getMediaType() == null ? "UNKNOWN" : media.getMediaType().name();
        Timer.Sample sample = Timer.start(meterRegistry);
        Path original = null;
        Path thumbnail = null;
        try {
            media.setStatus(MediaStatus.PROCESSING);
            repository.save(media);

            original = storage.downloadToTempFile(media.getObjectKey(),
                    MediaFiles.suffixFor(media.getOriginalFilename(), media.getContentType()));
            thumbnail = Files.createTempFile("es-thumb-", ".jpg");
            Files.deleteIfExists(thumbnail);

            if (media.getMediaType() == MediaType.VIDEO) {
                VideoMeta meta = videoThumbnailer.generate(original, thumbnail);
                media.setWidth(meta.width());
                media.setHeight(meta.height());
                if (meta.durationSeconds() != null) {
                    media.setDurationSeconds(BigDecimal.valueOf(meta.durationSeconds()));
                }
            } else {
                Dimensions dims = imageThumbnailer.generate(original, thumbnail);
                media.setWidth(dims.width());
                media.setHeight(dims.height());
            }

            String thumbnailKey = MediaFiles.thumbnailKey(media.getEventId(), media.getId());
            storage.uploadFile(thumbnail, thumbnailKey, "image/jpeg");
            media.setThumbnailKey(thumbnailKey);
            media.setStatus(MediaStatus.PROCESSED);
            repository.save(media);

            meterRegistry.counter("eventshare.media.processed", "mediaType", mediaTypeTag).increment();
            log.info("Processed media {} ({}) -> {}", media.getId(), mediaTypeTag, thumbnailKey);
        } catch (Exception e) {
            meterRegistry.counter("eventshare.media.processing.failed", "mediaType", mediaTypeTag).increment();
            markFailed(media);
            log.error("Processing failed for media {}: {}", mediaId, e.getMessage(), e);
        } finally {
            sample.stop(meterRegistry.timer("eventshare.media.processing.time", "mediaType", mediaTypeTag));
            deleteQuietly(original);
            deleteQuietly(thumbnail);
        }
    }

    private void markFailed(Media media) {
        try {
            media.setStatus(MediaStatus.FAILED);
            repository.save(media);
        } catch (Exception ignored) {
            log.error("Could not mark media {} as FAILED", media.getId());
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
