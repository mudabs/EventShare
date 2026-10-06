package com.eventshare.api.privacy;

import com.eventshare.api.event.Event;
import com.eventshare.api.event.EventRepository;
import com.eventshare.api.event.EventStatus;
import com.eventshare.api.event.EventType;
import com.eventshare.api.media.Media;
import com.eventshare.api.media.MediaRepository;
import com.eventshare.api.media.MediaStatus;
import com.eventshare.api.media.MediaType;
import com.eventshare.api.media.ModerationState;
import com.eventshare.api.media.r2.R2StorageService;
import com.eventshare.api.user.Role;
import com.eventshare.api.user.User;
import com.eventshare.api.user.UserRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** 30-day purge of deleted media and deleted events (V13, MediaPurgeJob). */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@Import({MediaPurgeJob.class, MediaPurgeJobIT.Config.class})
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MediaPurgeJobIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @TestConfiguration
    static class Config {
        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    @MockitoBean R2StorageService storage;

    @Autowired MediaPurgeJob job;
    @Autowired UserRepository users;
    @Autowired EventRepository events;
    @Autowired MediaRepository media;
    @Autowired JdbcTemplate jdbc;

    private Event event;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM media");
        User host = new User();
        host.setClerkUserId("clerk_" + UUID.randomUUID());
        host.setRole(Role.HOST);
        host = users.save(host);
        event = new Event();
        event.setHostId(host.getId());
        event.setName("Purge test");
        event.setEventType(EventType.WEDDING);
        event.setInviteCode("PG" + UUID.randomUUID().toString().substring(0, 8));
        event.setStatus(EventStatus.ACTIVE);
        event = events.save(event);
    }

    private Media item(ModerationState state) {
        Media m = new Media();
        m.setEventId(event.getId());
        m.setContentType("image/jpeg");
        m.setMediaType(MediaType.PHOTO);
        m.setObjectKey("events/" + event.getId() + "/originals/" + UUID.randomUUID() + "/f.jpg");
        m.setThumbnailKey("events/" + event.getId() + "/thumbnails/" + UUID.randomUUID() + ".jpg");
        m.setStatus(MediaStatus.PROCESSED);
        m.setModerationState(state);
        return media.saveAndFlush(m);
    }

    private void ageDeletion(UUID id, int days) {
        jdbc.update("UPDATE media SET deleted_at = now() - make_interval(days => ?) WHERE id = ?", days, id);
    }

    @Test
    void deletingSetsDeletedAtAndRestoringClearsIt() {
        Media m = item(ModerationState.DELETED);
        assertThat(m.getDeletedAt()).isNotNull();
        m.setModerationState(ModerationState.VISIBLE);
        assertThat(m.getDeletedAt()).isNull();
    }

    @Test
    void purgesOnlyItemsDeletedMoreThan30DaysAgo() {
        Media old = item(ModerationState.DELETED);
        Media recent = item(ModerationState.DELETED);
        Media visible = item(ModerationState.VISIBLE);
        Media hidden = item(ModerationState.HIDDEN);
        ageDeletion(old.getId(), 31);
        ageDeletion(recent.getId(), 5);

        job.run();

        assertThat(media.findById(old.getId())).isEmpty();
        verify(storage).deleteObject(old.getObjectKey());
        verify(storage).deleteObject(old.getThumbnailKey());
        assertThat(media.findById(recent.getId())).isPresent();
        assertThat(media.findById(visible.getId())).isPresent();
        assertThat(media.findById(hidden.getId())).isPresent();
        verify(storage, never()).deleteObject(recent.getObjectKey());
    }

    @Test
    void keepsRowWhenStorageDeleteFailsSoNextRunRetries() {
        Media old = item(ModerationState.DELETED);
        ageDeletion(old.getId(), 40);
        doThrow(new RuntimeException("R2 down")).when(storage).deleteObject(eq(old.getObjectKey()));

        job.run();

        assertThat(media.findById(old.getId())).isPresent();
    }

    @Test
    void purgesEventsDeletedMoreThan30DaysAgoWithAllTheirMedia() {
        Media a = item(ModerationState.VISIBLE);
        Media b = item(ModerationState.HIDDEN);
        jdbc.update("UPDATE events SET deleted_at = now() - interval '45 days' WHERE id = ?", event.getId());

        job.run();

        assertThat(events.findById(event.getId())).isEmpty();
        assertThat(media.findById(a.getId())).isEmpty();
        assertThat(media.findById(b.getId())).isEmpty();
        verify(storage).deleteObject(a.getObjectKey());
    }

    @Test
    void recentlyDeletedEventIsKept() {
        item(ModerationState.VISIBLE);
        jdbc.update("UPDATE events SET deleted_at = now() - interval '3 days' WHERE id = ?", event.getId());

        job.run();

        assertThat(events.findById(event.getId())).isPresent();
        verify(storage, never()).deleteObject(anyString());
    }
}
