package com.eventshare.api.demo;

import com.eventshare.api.common.security.ClerkUserClient;
import com.eventshare.api.event.Event;
import com.eventshare.api.event.EventRepository;
import com.eventshare.api.event.EventStatus;
import com.eventshare.api.event.EventType;
import com.eventshare.api.media.r2.R2StorageService;
import com.eventshare.api.user.Role;
import com.eventshare.api.user.User;
import com.eventshare.api.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
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

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Runs the real demo seeder against PostgreSQL (Testcontainers) with Clerk and R2
 * mocked. Verifies what a reset creates, that a second reset replaces it, that real
 * users' data is untouched, and that an invite-code clash aborts cleanly.
 * See docs/changes/2026-10-05-interview-demo-mode.md.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "eventshare.demo.enabled=true",
        "eventshare.demo.admin-enabled=true",
        "eventshare.demo.photo-count=10"
})
@Import(DemoSeeder.class)
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DemoSeederIT {

    static final int PHOTOS = 10;

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("eventshare.demo.host-password", () -> "Host-Pass-123");
        registry.add("eventshare.demo.admin-password", () -> "Admin-Pass-123");
    }

    @MockitoBean ClerkUserClient clerk;
    @MockitoBean R2StorageService storage;

    @Autowired DemoSeeder seeder;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired EventRepository events;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM audit_logs");
        jdbc.update("DELETE FROM events");
        jdbc.update("DELETE FROM promo_codes");
        jdbc.update("DELETE FROM whitelisted_users");
        when(clerk.isConfigured()).thenReturn(true);
        when(clerk.ensureUserWithPassword(eq("demo-host"), any(), anyString(), any(), any()))
                .thenReturn("user_demo_host");
        when(clerk.ensureUserWithPassword(eq("demo-admin"), any(), anyString(), any(), any()))
                .thenReturn("user_demo_admin");
    }

    private int count(String sql, Object... args) {
        Integer n = jdbc.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }

    @Test
    void resetSeedsTheShowcase() {
        seeder.reset("test");

        UUID hostId = users.findByClerkUserId("user_demo_host").orElseThrow().getId();
        assertThat(users.findByClerkUserId("user_demo_admin").orElseThrow().getRole()).isEqualTo(Role.ADMIN);
        assertThat(seeder.isSeeded()).isTrue();
        assertThat(count("SELECT count(*) FROM events WHERE host_id = ?", hostId)).isEqualTo(2);

        Event main = events.findByInviteCodeAndDeletedAtIsNull("EVENTSHARE").orElseThrow();
        assertThat(count("SELECT count(*) FROM media WHERE event_id = ?", main.getId())).isEqualTo(PHOTOS);
        assertThat(count("SELECT count(*) FROM media WHERE event_id = ? AND is_duplicate", main.getId())).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM media WHERE event_id = ? AND moderation_state = 'HIDDEN'", main.getId())).isEqualTo(2);
        assertThat(count("SELECT count(*) FROM media WHERE event_id = ? AND status = 'UPLOADED'", main.getId())).isEqualTo(PHOTOS);
        assertThat(count("SELECT count(*) FROM event_memberships WHERE event_id = ? AND role = 'GUEST'", main.getId())).isEqualTo(8);
        assertThat(count("SELECT count(*) FROM event_visits WHERE event_id = ?", main.getId())).isEqualTo(46);
        assertThat(count("SELECT count(*) FROM promo_codes WHERE code = 'INTERVIEW30'")).isEqualTo(1);
        // Username-only demo host: whitelisted under its unroutable local address.
        assertThat(count("SELECT count(*) FROM whitelisted_users WHERE email = 'demo-host@demo.invalid'")).isEqualTo(1);
        assertThat(users.findByClerkUserId("user_demo_host").orElseThrow().getEmail()).isEqualTo("demo-host@demo.invalid");

        Timestamp created = jdbc.queryForObject("SELECT created_at FROM events WHERE id = ?", Timestamp.class, main.getId());
        assertThat(created.toInstant()).isBefore(Instant.now().minus(20, ChronoUnit.DAYS));
        assertThat(events.findByInviteCodeAndDeletedAtIsNull("TEAMDAY26X").orElseThrow().getStatus())
                .isEqualTo(EventStatus.ARCHIVED);

        verify(storage, times(PHOTOS + 4)).uploadBytes(any(), anyString(), eq("image/jpeg"));
    }

    @Test
    void secondResetReplacesDataAndDeletesOldObjects() {
        seeder.reset("first");
        UUID firstMainId = events.findByInviteCodeAndDeletedAtIsNull("EVENTSHARE").orElseThrow().getId();
        clearInvocations(storage);

        seeder.reset("second");

        UUID secondMainId = events.findByInviteCodeAndDeletedAtIsNull("EVENTSHARE").orElseThrow().getId();
        assertThat(secondMainId).isNotEqualTo(firstMainId);
        assertThat(count("SELECT count(*) FROM events")).isEqualTo(2);
        assertThat(count("SELECT count(*) FROM promo_codes WHERE code = 'INTERVIEW30'")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM whitelisted_users")).isEqualTo(1);
        // Every original from the first seed is removed from R2 after the second commits.
        verify(storage, times(PHOTOS + 4)).deleteObject(anyString());
    }

    @Test
    void resetNeverTouchesRealUsersData() {
        User real = new User();
        real.setClerkUserId("user_real_" + UUID.randomUUID());
        real.setEmail("real.person@example.org");
        real.setRole(Role.HOST);
        real = users.save(real);
        Event realEvent = new Event();
        realEvent.setHostId(real.getId());
        realEvent.setName("Real Birthday");
        realEvent.setEventType(EventType.BIRTHDAY);
        realEvent.setInviteCode("REALBDAY23");
        realEvent.setStatus(EventStatus.ACTIVE);
        realEvent = events.save(realEvent);

        seeder.reset("first");
        seeder.reset("second");

        assertThat(events.findById(realEvent.getId())).isPresent();
    }

    @Test
    void inviteCodeOwnedByRealUserAbortsAndCleansUpUploads() {
        User real = new User();
        real.setClerkUserId("user_real_" + UUID.randomUUID());
        real.setRole(Role.HOST);
        real = users.save(real);
        Event clash = new Event();
        clash.setHostId(real.getId());
        clash.setName("Someone else's event");
        clash.setEventType(EventType.OTHER);
        clash.setInviteCode("EVENTSHARE");
        clash.setStatus(EventStatus.ACTIVE);
        events.save(clash);

        assertThatThrownBy(() -> seeder.reset("clash"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DEMO_INVITE_CODE");
        // Photos uploaded before the failed transaction are deleted again.
        verify(storage, atLeast(PHOTOS)).deleteObject(anyString());
        assertThat(count("SELECT count(*) FROM promo_codes")).isZero();
    }

    @Test
    void resetGivesBackPromoRedemptionsMadeByDemoAccounts() {
        seeder.reset("first");
        UUID hostId = users.findByClerkUserId("user_demo_host").orElseThrow().getId();

        // During an interview the demo host redeems a real (non-demo) promo code.
        UUID realPromo = UUID.randomUUID();
        jdbc.update("INSERT INTO promo_codes (id, code, type, grants_plan_code, duration_days, redemptions_used) "
                + "VALUES (?, 'REAL10', 'TEMP_PREMIUM', 'BASIC', 10, 1)", realPromo);
        UUID sub = UUID.randomUUID();
        jdbc.update("INSERT INTO subscriptions (id, user_id, plan_code, status, source) "
                + "VALUES (?, ?, 'BASIC', 'ACTIVE', 'PROMO')", sub, hostId);
        jdbc.update("INSERT INTO promo_code_usage (promo_code_id, user_id, resulting_subscription_id) VALUES (?, ?, ?)",
                realPromo, hostId, sub);

        seeder.reset("second");

        assertThat(count("SELECT count(*) FROM promo_code_usage WHERE user_id = ?", hostId)).isZero();
        assertThat(count("SELECT count(*) FROM subscriptions WHERE user_id = ?", hostId)).isZero();
        // The real code is kept and its redemption is given back.
        assertThat(count("SELECT redemptions_used FROM promo_codes WHERE id = ?", realPromo)).isZero();
        // The demo code is recreated fresh.
        assertThat(count("SELECT redemptions_used FROM promo_codes WHERE code = 'INTERVIEW30'")).isZero();
    }
}
