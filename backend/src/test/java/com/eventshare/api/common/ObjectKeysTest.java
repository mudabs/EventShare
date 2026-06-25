package com.eventshare.api.common;

import com.eventshare.api.common.util.ObjectKeys;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ObjectKeysTest {

    private final UUID eventId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID mediaId = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void buildsOriginalKeyUnderEventPrefix() {
        String key = ObjectKeys.original(eventId, mediaId, "Sunset.JPG");
        assertThat(key).isEqualTo(
                "events/11111111-1111-1111-1111-111111111111/originals/"
                        + "22222222-2222-2222-2222-222222222222/Sunset.JPG");
    }

    @Test
    void stripsPathTraversalAndUnsafeCharacters() {
        String key = ObjectKeys.original(eventId, mediaId, "../../etc/pa ss?wd.png");
        assertThat(key).endsWith("/pa_ss_wd.png");
        assertThat(key).doesNotContain("..");
    }

    @Test
    void fallsBackWhenFilenameBlank() {
        assertThat(ObjectKeys.sanitize("   ")).isEqualTo("file");
        assertThat(ObjectKeys.sanitize(null)).isEqualTo("file");
    }

    @Test
    void thumbnailKeyIsDeterministic() {
        assertThat(ObjectKeys.thumbnail(eventId, mediaId))
                .isEqualTo("events/" + eventId + "/thumbnails/" + mediaId + ".jpg");
    }
}
