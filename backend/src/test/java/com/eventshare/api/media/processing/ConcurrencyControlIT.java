package com.eventshare.api.media.processing;

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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the row-locking added on 2026-10-05
 * (docs/changes/2026-10-05-security-and-correctness-hardening.md):
 * <ul>
 *   <li>C5: {@link MediaWorkQueue#claim} hands disjoint rows to concurrent claimers.</li>
 *   <li>C3: {@link UserRepository#findByIdForUpdate} blocks a second locker until the
 *       first transaction ends, which is what serialises plan-limit checks.</li>
 * </ul>
 * Runs against real PostgreSQL (Testcontainers), so it needs Docker. Tests are not
 * wrapped in a rollback transaction because the scenarios need committed rows that
 * other threads can see.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@Import(MediaWorkQueue.class)
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ConcurrencyControlIT {

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
    @Autowired MediaWorkQueue workQueue;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager txManager;

    private User host;
    private Event event;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM media");
        host = new User();
        host.setClerkUserId("clerk_" + UUID.randomUUID());
        host.setRole(Role.HOST);
        host = users.save(host);

        event = new Event();
        event.setHostId(host.getId());
        event.setName("Concurrency Event");
        event.setEventType(EventType.WEDDING);
        event.setInviteCode("CC" + UUID.randomUUID().toString().substring(0, 8));
        event.setStatus(EventStatus.ACTIVE);
        event = events.save(event);
    }

    private Media uploaded() {
        Media m = new Media();
        m.setEventId(event.getId());
        m.setContentType("image/jpeg");
        m.setMediaType(MediaType.PHOTO);
        m.setObjectKey("events/" + event.getId() + "/originals/" + UUID.randomUUID() + "/f.jpg");
        m.setSizeBytes(10L);
        m.setStatus(MediaStatus.UPLOADED);
        m.setModerationState(ModerationState.VISIBLE);
        return media.save(m);
    }

    @Test
    void claimMarksRowsProcessingAndDoesNotReturnThemTwice() {
        for (int i = 0; i < 3; i++) {
            uploaded();
        }
        Instant notStale = Instant.now().minusSeconds(3600);

        List<UUID> first = workQueue.claim(2, notStale);
        List<UUID> second = workQueue.claim(2, notStale);
        List<UUID> third = workQueue.claim(2, notStale);

        assertThat(first).hasSize(2);
        assertThat(second).hasSize(1).doesNotContainAnyElementsOf(first);
        assertThat(third).isEmpty();
        assertThat(media.findById(first.get(0)).orElseThrow().getStatus()).isEqualTo(MediaStatus.PROCESSING);
    }

    @Test
    void staleProcessingRowsAreReclaimed() {
        Media m = uploaded();
        workQueue.claim(10, Instant.now().minusSeconds(3600));
        // Cutoff in the future: every PROCESSING row now counts as stale.
        List<UUID> reclaimed = workQueue.claim(10, Instant.now().plusSeconds(60));
        assertThat(reclaimed).containsExactly(m.getId());
    }

    @Test
    void concurrentClaimersReceiveDisjointRows() throws Exception {
        Set<UUID> all = new HashSet<>();
        for (int i = 0; i < 6; i++) {
            all.add(uploaded().getId());
        }
        Instant notStale = Instant.now().minusSeconds(3600);
        TransactionTemplate tx = new TransactionTemplate(txManager);
        CountDownLatch aClaimed = new CountDownLatch(1);
        CountDownLatch bDone = new CountDownLatch(1);

        // Claimer A takes 3 rows and keeps its transaction (and row locks) open.
        CompletableFuture<List<UUID>> a = CompletableFuture.supplyAsync(() -> tx.execute(status -> {
            List<UUID> ids = workQueue.claim(3, notStale);
            aClaimed.countDown();
            try {
                bDone.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return ids;
        }));
        assertThat(aClaimed.await(10, TimeUnit.SECONDS)).isTrue();

        // Claimer B asks for everything while A still holds its locks: SKIP LOCKED
        // must give B only the rows A did not take, without waiting.
        List<UUID> b = workQueue.claim(10, notStale);
        bDone.countDown();
        List<UUID> aIds = a.get(10, TimeUnit.SECONDS);

        assertThat(aIds).hasSize(3);
        assertThat(b).hasSize(3).doesNotContainAnyElementsOf(aIds);
        Set<UUID> union = new HashSet<>(aIds);
        union.addAll(b);
        assertThat(union).isEqualTo(all);
    }

    @Test
    void hostRowLockSerialisesSecondLocker() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        CountDownLatch aLocked = new CountDownLatch(1);
        CountDownLatch releaseA = new CountDownLatch(1);

        CompletableFuture<Void> a = CompletableFuture.runAsync(() -> tx.executeWithoutResult(status -> {
            users.findByIdForUpdate(host.getId());
            aLocked.countDown();
            try {
                releaseA.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
        assertThat(aLocked.await(10, TimeUnit.SECONDS)).isTrue();

        CompletableFuture<Void> b = CompletableFuture.runAsync(() -> tx.executeWithoutResult(status ->
                users.findByIdForUpdate(host.getId())));

        Thread.sleep(500);
        assertThat(b).as("second locker must wait while the first holds the row lock").isNotDone();

        releaseA.countDown();
        a.get(10, TimeUnit.SECONDS);
        b.get(10, TimeUnit.SECONDS);
        assertThat(b).isDone();
    }
}
