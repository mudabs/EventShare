package com.eventshare.api.demo;

import com.eventshare.api.event.Event;
import com.eventshare.api.event.EventRepository;
import com.eventshare.api.event.EventStatus;
import com.eventshare.api.event.EventType;
import com.eventshare.api.media.Media;
import com.eventshare.api.media.MediaRepository;
import com.eventshare.api.media.MediaStatus;
import com.eventshare.api.media.MediaType;
import com.eventshare.api.media.ModerationState;
import com.eventshare.api.user.Role;
import com.eventshare.api.user.User;
import com.eventshare.api.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Database-level checks for the demo upload counters (V10 + V11 migrations):
 * deleted uploads still count, seeded photos never count, per-IP counts are isolated,
 * and a row inserted without JPA defaults to demo_seeded = FALSE (so it counts).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@Testcontainers
class DemoUploadCountIT {

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
    @Autowired JdbcTemplate jdbc;

    private Event newEvent() {
        User host = new User();
        host.setClerkUserId("clerk_" + UUID.randomUUID());
        host.setRole(Role.HOST);
        host = users.save(host);
        Event event = new Event();
        event.setHostId(host.getId());
        event.setName("Demo count test");
        event.setEventType(EventType.WEDDING);
        event.setInviteCode("DC" + UUID.randomUUID().toString().substring(0, 8));
        event.setStatus(EventStatus.ACTIVE);
        return events.save(event);
    }

    private void upload(UUID eventId, boolean seeded, ModerationState state, String ipHash) {
        Media m = new Media();
        m.setEventId(eventId);
        m.setContentType("image/jpeg");
        m.setMediaType(MediaType.PHOTO);
        m.setObjectKey("events/" + eventId + "/originals/" + UUID.randomUUID() + "/f.jpg");
        m.setSizeBytes(10L);
        m.setStatus(MediaStatus.UPLOADED);
        m.setModerationState(state);
        m.setDemoSeeded(seeded);
        m.setUploaderIpHash(ipHash);
        media.saveAndFlush(m);
    }

    @Test
    void countsIncludeDeletedUploadsAndExcludeSeededPhotos() {
        Event event = newEvent();
        String ipA = "a".repeat(64);
        String ipB = "b".repeat(64);
        upload(event.getId(), true, ModerationState.VISIBLE, null);      // seeded: never counts
        upload(event.getId(), false, ModerationState.VISIBLE, ipA);
        upload(event.getId(), false, ModerationState.DELETED, ipA);      // deleted: still counts
        upload(event.getId(), false, ModerationState.VISIBLE, ipB);

        assertThat(media.countByEventIdAndDemoSeededFalse(event.getId())).isEqualTo(3);
        assertThat(media.countByEventIdAndDemoSeededFalseAndUploaderIpHash(event.getId(), ipA)).isEqualTo(2);
        assertThat(media.countByEventIdAndDemoSeededFalseAndUploaderIpHash(event.getId(), ipB)).isEqualTo(1);
    }

    @Test
    void rawInsertDefaultsToNotSeededSoItCounts() {
        Event event = newEvent();
        jdbc.update("""
                INSERT INTO media (id, event_id, content_type, media_type, object_key, status, moderation_state)
                VALUES (?, ?, 'image/jpeg', 'PHOTO', ?, 'UPLOADED', 'VISIBLE')
                """, UUID.randomUUID(), event.getId(), "raw/" + UUID.randomUUID());

        assertThat(media.countByEventIdAndDemoSeededFalse(event.getId())).isEqualTo(1);
    }
}
