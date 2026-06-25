package com.eventshare.worker.processing;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MediaFilesTest {

    @Test
    void prefersFilenameExtension() {
        assertThat(MediaFiles.suffixFor("clip.MP4", "application/octet-stream")).isEqualTo(".mp4");
    }

    @Test
    void fallsBackToContentType() {
        assertThat(MediaFiles.suffixFor(null, "image/png")).isEqualTo(".png");
        assertThat(MediaFiles.suffixFor("noext", "video/quicktime")).isEqualTo(".mov");
    }

    @Test
    void fallsBackToBinForUnknown() {
        assertThat(MediaFiles.suffixFor(null, "application/x-weird")).isEqualTo(".bin");
    }

    @Test
    void buildsThumbnailKey() {
        UUID e = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID m = UUID.fromString("22222222-2222-2222-2222-222222222222");
        assertThat(MediaFiles.thumbnailKey(e, m))
                .isEqualTo("events/" + e + "/thumbnails/" + m + ".jpg");
    }
}
