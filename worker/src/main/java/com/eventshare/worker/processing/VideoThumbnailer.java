package com.eventshare.worker.processing;

import com.eventshare.worker.config.WorkerProperties;
import net.coobird.thumbnailator.Thumbnails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Extracts a poster frame from a video using ffmpeg, downsizes it to a JPEG
 * thumbnail, and reads the clip duration via ffprobe. Both binaries are installed
 * in the worker container image.
 */
@Component
public class VideoThumbnailer {

    private static final Logger log = LoggerFactory.getLogger(VideoThumbnailer.class);
    private static final long PROCESS_TIMEOUT_SECONDS = 120;

    private final int maxDimension;
    private final String frameTimestamp;

    public VideoThumbnailer(WorkerProperties props) {
        this.maxDimension = props.processing().thumbnailMaxDimension();
        this.frameTimestamp = props.processing().videoFrameTimestamp();
    }

    public VideoMeta generate(Path source, Path target) throws IOException, InterruptedException {
        Path frame = Files.createTempFile("es-frame-", ".jpg");
        Files.deleteIfExists(frame);
        try {
            run(List.of("ffmpeg", "-y", "-ss", frameTimestamp, "-i", source.toString(),
                    "-frames:v", "1", frame.toString()));

            BufferedImage image = ImageIO.read(frame.toFile());
            if (image == null) {
                throw new IOException("Could not read extracted video frame");
            }
            int width = image.getWidth();
            int height = image.getHeight();
            try (OutputStream out = Files.newOutputStream(target)) {
                Thumbnails.of(image)
                        .size(maxDimension, maxDimension)
                        .keepAspectRatio(true)
                        .outputFormat("jpg")
                        .outputQuality(0.85)
                        .toOutputStream(out);
            }
            return new VideoMeta(width, height, probeDuration(source));
        } finally {
            Files.deleteIfExists(frame);
        }
    }

    private Double probeDuration(Path source) {
        try {
            String output = capture(List.of("ffprobe", "-v", "error",
                    "-show_entries", "format=duration",
                    "-of", "default=noprint_wrappers=1:nokey=1", source.toString()));
            String trimmed = output.trim();
            return trimmed.isEmpty() ? null : Double.parseDouble(trimmed);
        } catch (Exception e) {
            log.warn("ffprobe duration extraction failed: {}", e.getMessage());
            return null;
        }
    }

    private void run(List<String> command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("Command timed out: " + command);
        }
        if (process.exitValue() != 0) {
            throw new IOException("Command failed (" + process.exitValue() + "): " + output);
        }
    }

    private String capture(List<String> command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("Command timed out: " + command);
        }
        return output;
    }
}
