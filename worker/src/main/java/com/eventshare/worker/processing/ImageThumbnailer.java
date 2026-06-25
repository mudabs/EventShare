package com.eventshare.worker.processing;

import com.eventshare.worker.config.WorkerProperties;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class ImageThumbnailer {

    private final int maxDimension;

    public ImageThumbnailer(WorkerProperties props) {
        this.maxDimension = props.processing().thumbnailMaxDimension();
    }

    /** Reads the source image, writes a bounded JPEG thumbnail, returns original dims. */
    public Dimensions generate(Path source, Path target) throws IOException {
        BufferedImage image = ImageIO.read(source.toFile());
        if (image == null) {
            throw new IOException("Unsupported or unreadable image format");
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
        return new Dimensions(width, height);
    }
}
