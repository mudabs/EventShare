package com.eventshare.api.media;

import com.eventshare.api.event.Event;
import com.eventshare.api.event.EventRepository;
import com.eventshare.api.event.EventStatus;
import com.eventshare.api.event.EventType;
import com.eventshare.api.user.Role;
import com.eventshare.api.user.User;
import com.eventshare.api.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test: spins up a real PostgreSQL via Testcontainers, applies the
 * Flyway migrations, validates the JPA mappings against the live schema, and
 * exercises the custom repository queries. Requires Docker (runs in CI via the
 * failsafe plugin on `mvn verify`).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@Testcontainers
class MediaRepositoryIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired UserRepository users;
    @Autowired EventRepository events;
    @Autowired MediaRepository media;

    private Event newEvent() {
        User host = new User();
        host.setClerkUserId("clerk_" + UUID.randomUUID());
        host.setRole(Role.HOST);
        host = users.save(host);

        Event event = new Event();
        event.setHostId(host.getId());
        event.setName("Integration Wedding");
        event.setEventType(EventType.WEDDING);
        event.setInviteCode("IT" + UUID.randomUUID().toString().substring(0, 8));
        event.setStatus(EventStatus.ACTIVE);
        return events.save(event);
    }

    private Media newMedia(UUID eventId, MediaType type, ModerationState state, long bytes, String sha) {
        Media m = new Media();
        m.setEventId(eventId);
        m.setContentType(type == MediaType.VIDEO ? "video/mp4" : "image/jpeg");
        m.setMediaType(type);
        m.setObjectKey("events/" + eventId + "/originals/" + UUID.randomUUID() + "/f.bin");
        m.setSizeBytes(bytes);
        m.setSha256(sha);
        m.setStatus(MediaStatus.UPLOADED);
        m.setModerationState(state);
        return media.save(m);
    }

    @Test
    void detectsEarliestExactDuplicateBySha() throws InterruptedException {
        Event event = newEvent();
        String sha = "c".repeat(64);
        Media first = newMedia(event.getId(), MediaType.PHOTO, ModerationState.VISIBLE, 10, sha);
        Thread.sleep(5);
        newMedia(event.getId(), MediaType.PHOTO, ModerationState.VISIBLE, 10, sha);

        var earliest = media.findFirstByEventIdAndSha256OrderByCreatedAtAscIdAsc(event.getId(), sha);
        assertThat(earliest).isPresent();
        assertThat(earliest.get().getId()).isEqualTo(first.getId());
    }

    @Test
    void computesAnalyticsCountsAndExcludesDeletedBytes() {
        Event event = newEvent();
        newMedia(event.getId(), MediaType.PHOTO, ModerationState.VISIBLE, 100, null);
        newMedia(event.getId(), MediaType.PHOTO, ModerationState.VISIBLE, 200, null);
        newMedia(event.getId(), MediaType.VIDEO, ModerationState.HIDDEN, 300, null);
        newMedia(event.getId(), MediaType.PHOTO, ModerationState.DELETED, 999, null);

        assertThat(media.countByEventId(event.getId())).isEqualTo(4);
        assertThat(media.countByEventIdAndModerationState(event.getId(), ModerationState.VISIBLE)).isEqualTo(2);
        assertThat(media.countByEventIdAndModerationState(event.getId(), ModerationState.HIDDEN)).isEqualTo(1);
        assertThat(media.sumSizeBytesByEvent(event.getId(), ModerationState.DELETED)).isEqualTo(600);
    }

    @Test
    void keysetGalleryPaginatesWithoutOverlap() throws InterruptedException {
        Event event = newEvent();
        for (int i = 0; i < 5; i++) {
            newMedia(event.getId(), MediaType.PHOTO, ModerationState.VISIBLE, 10, null);
            Thread.sleep(2);
        }

        Set<UUID> collected = new HashSet<>();
        List<Media> page = media.findGalleryFirstPage(event.getId(), ModerationState.VISIBLE, PageRequest.of(0, 2));
        while (!page.isEmpty()) {
            page.forEach(m -> collected.add(m.getId()));
            Media last = page.get(page.size() - 1);
            page = media.findGalleryAfter(event.getId(), ModerationState.VISIBLE,
                    last.getCreatedAt(), last.getId(), PageRequest.of(0, 2));
        }

        assertThat(collected).hasSize(5);
        List<Media> firstTwo = media.findGalleryFirstPage(event.getId(), ModerationState.VISIBLE, PageRequest.of(0, 2));
        assertThat(firstTwo).hasSize(2);
        assertThat(new ArrayList<>(collected)).doesNotHaveDuplicates();
    }
}
