package com.eventshare.api.demo;

import com.eventshare.api.common.security.ClerkUserClient;
import com.eventshare.api.event.Event;
import com.eventshare.api.event.EventMembership;
import com.eventshare.api.event.EventMembershipRepository;
import com.eventshare.api.event.EventRepository;
import com.eventshare.api.event.EventStatus;
import com.eventshare.api.event.EventType;
import com.eventshare.api.event.MembershipRole;
import com.eventshare.api.event.MembershipStatus;
import com.eventshare.api.media.Media;
import com.eventshare.api.media.MediaRepository;
import com.eventshare.api.media.MediaStatus;
import com.eventshare.api.media.MediaType;
import com.eventshare.api.media.ModerationState;
import com.eventshare.api.media.r2.R2StorageService;
import com.eventshare.api.common.util.ObjectKeys;
import com.eventshare.api.promo.PromoCode;
import com.eventshare.api.promo.PromoCodeRepository;
import com.eventshare.api.promo.PromoCodeType;
import com.eventshare.api.user.Role;
import com.eventshare.api.user.User;
import com.eventshare.api.user.UserRepository;
import com.eventshare.api.whitelist.WhitelistedUser;
import com.eventshare.api.whitelist.WhitelistedUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Seeds and resets the interview demo (see docs/DEMO.md).
 *
 * <p>What a reset produces:
 * <ul>
 *   <li>Clerk accounts for the demo host (and optionally a demo admin) with the
 *       passwords from the environment; passwords are re-applied on every reset.</li>
 *   <li>A whitelist entry so the demo host has unlimited plan limits.</li>
 *   <li>"Amara &amp; Kofi's Wedding" (invite code {@code DEMO_INVITE_CODE}): 8 guests,
 *       generated photos uploaded to R2, one exact duplicate, two hidden photos in the
 *       moderation view, and two weeks of visitor analytics.</li>
 *   <li>An archived "Product Team Offsite" event so the dashboard lists several events.</li>
 *   <li>A promo code that grants Wedding Pro for 30 days.</li>
 * </ul>
 *
 * <p>Safety: a reset only deletes events whose host is one of the demo accounts, plus
 * the demo promo code. Real users' data is never touched. The database part runs in
 * one transaction under a PostgreSQL advisory lock, so two API replicas resetting at
 * the same moment cannot interleave. Photos are uploaded to R2 before the transaction
 * and the previous demo's objects are deleted after it commits; if the transaction
 * fails, the freshly uploaded objects are deleted instead.
 */
@Service
public class DemoSeeder {

    private static final Logger log = LoggerFactory.getLogger(DemoSeeder.class);

    /** Arbitrary constant for pg_advisory_xact_lock; identifies "demo reset". */
    private static final long ADVISORY_LOCK_KEY = 0x45564E5444454D4FL; // "EVNTDEMO"

    private static final String[] GUESTS = {
            "Chipo M.", "Daniel K.", "Aisha R.", "Tom W.",
            "Nyasha T.", "Priya S.", "Luis G.", "Grace O."
    };

    private final DemoProperties props;
    private final ClerkUserClient clerk;
    private final UserRepository users;
    private final EventRepository events;
    private final EventMembershipRepository memberships;
    private final MediaRepository media;
    private final WhitelistedUserRepository whitelist;
    private final PromoCodeRepository promos;
    private final R2StorageService storage;
    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public DemoSeeder(DemoProperties props,
                      ClerkUserClient clerk,
                      UserRepository users,
                      EventRepository events,
                      EventMembershipRepository memberships,
                      MediaRepository media,
                      WhitelistedUserRepository whitelist,
                      PromoCodeRepository promos,
                      R2StorageService storage,
                      NamedParameterJdbcTemplate jdbc,
                      PlatformTransactionManager txManager) {
        this.props = props;
        this.clerk = clerk;
        this.users = users;
        this.events = events;
        this.memberships = memberships;
        this.media = media;
        this.whitelist = whitelist;
        this.promos = promos;
        this.storage = storage;
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(txManager);
    }

    /** True when the main showcase event exists. */
    public boolean isSeeded() {
        return events.findByInviteCodeAndDeletedAtIsNull(props.inviteCode()).isPresent();
    }

    /**
     * Re-applies demo account passwords and roles without touching events. Called at
     * startup when the demo is already seeded, so a changed DEMO_*_PASSWORD takes
     * effect after a restart.
     */
    public synchronized void syncAccounts() {
        DemoAccounts accounts = ensureClerkAccounts();
        tx.executeWithoutResult(status -> upsertLocalUsers(accounts));
    }

    /** Deletes all demo-owned data and seeds a fresh showcase. */
    public synchronized DemoResetResult reset(String trigger) {
        long started = System.nanoTime();
        DemoAccounts accounts = ensureClerkAccounts();

        UUID mainEventId = UUID.randomUUID();
        UUID offsiteEventId = UUID.randomUUID();
        List<PlannedPhoto> mainPhotos = planPhotos(mainEventId, props.photoCount(), 1L);
        List<PlannedPhoto> offsitePhotos = planPhotos(offsiteEventId, 4, 991L);

        List<String> uploadedKeys = new ArrayList<>();
        try {
            for (PlannedPhoto p : concat(mainPhotos, offsitePhotos)) {
                storage.uploadBytes(p.bytes(), p.objectKey(), "image/jpeg");
                uploadedKeys.add(p.objectKey());
            }
        } catch (RuntimeException e) {
            deleteQuietly(uploadedKeys);
            throw new IllegalStateException("Demo reset failed while uploading photos to R2: " + e.getMessage(), e);
        }

        List<String> oldKeys;
        try {
            oldKeys = tx.execute(status -> {
                // Serialise concurrent resets (e.g. two replicas hitting the cron at once).
                jdbc.getJdbcTemplate().execute("SELECT pg_advisory_xact_lock(" + ADVISORY_LOCK_KEY + ")");
                List<UUID> demoUserIds = upsertLocalUsers(accounts);
                List<String> keys = deleteDemoData(demoUserIds);
                User host = users.findById(accounts.hostUserIdOrThrow()).orElseThrow();
                seedMainEvent(host, mainEventId, mainPhotos);
                seedOffsiteEvent(host, offsiteEventId, offsitePhotos);
                seedPromo(host);
                return keys;
            });
        } catch (RuntimeException e) {
            deleteQuietly(uploadedKeys);
            throw e;
        }

        deleteQuietly(oldKeys);
        long ms = Duration.ofNanos(System.nanoTime() - started).toMillis();
        log.info("Demo reset ({}) finished in {} ms: {} photos seeded, {} old objects removed",
                trigger, ms, mainPhotos.size() + offsitePhotos.size(), oldKeys == null ? 0 : oldKeys.size());
        return new DemoResetResult(trigger, Instant.now(), mainPhotos.size() + offsitePhotos.size(),
                oldKeys == null ? 0 : oldKeys.size(), ms);
    }

    // ---- accounts ----

    private DemoAccounts ensureClerkAccounts() {
        if (!clerk.isConfigured()) {
            throw new IllegalStateException("Demo mode needs CLERK_SECRET_KEY to create the demo accounts");
        }
        String hostClerkId = clerk.ensureUserWithPassword(props.hostUsername(), props.hostEmail(),
                props.hostPassword(), firstName(props.hostName()), lastName(props.hostName()));
        String adminClerkId = null;
        if (props.adminEnabled()) {
            adminClerkId = clerk.ensureUserWithPassword(props.adminUsername(), props.adminEmail(),
                    props.adminPassword(), firstName(props.adminName()), lastName(props.adminName()));
        }
        return new DemoAccounts(hostClerkId, adminClerkId);
    }

    /** Creates or refreshes the local user rows; returns their ids (host first). */
    private List<UUID> upsertLocalUsers(DemoAccounts accounts) {
        List<UUID> ids = new ArrayList<>();
        User host = upsertUser(accounts.hostClerkId(), props.hostLocalEmail(), props.hostName(), Role.HOST);
        accounts.hostUserId = host.getId();
        ids.add(host.getId());
        if (accounts.adminClerkId() != null) {
            User admin = upsertUser(accounts.adminClerkId(), props.adminLocalEmail(), props.adminName(), Role.ADMIN);
            ids.add(admin.getId());
        }
        if (!whitelist.existsByEmailIgnoreCaseAndActiveTrueAndDeletedAtIsNull(props.hostLocalEmail())) {
            WhitelistedUser entry = new WhitelistedUser();
            entry.setEmail(props.hostLocalEmail());
            entry.setNote("Demo host account (unlimited plan). Managed by DemoSeeder.");
            entry.setActive(true);
            whitelist.save(entry);
        }
        return ids;
    }

    private User upsertUser(String clerkUserId, String email, String name, Role role) {
        User user = users.findByClerkUserId(clerkUserId).orElseGet(User::new);
        user.setClerkUserId(clerkUserId);
        user.setEmail(email);
        user.setDisplayName(name);
        user.setDisabled(false);
        // Never demote: a demo host that is also listed in ADMIN_EMAILS stays ADMIN.
        if (role == Role.ADMIN || user.getRole() == null) {
            user.setRole(role);
        }
        return users.save(user);
    }

    // ---- delete ----

    /**
     * Deletes every event hosted by a demo account (media, memberships, visits and
     * analytics cascade), the audit rows of those events, the demo accounts'
     * subscriptions, and the demo promo code (its redemptions cascade).
     * Returns the R2 keys of the deleted media so they can be removed after commit.
     */
    private List<String> deleteDemoData(List<UUID> demoUserIds) {
        MapSqlParameterSource params = new MapSqlParameterSource("hostIds", demoUserIds);

        Integer foreign = jdbc.queryForObject("""
                SELECT count(*) FROM events
                 WHERE invite_code IN (:codes) AND host_id NOT IN (:hostIds)
                """, params.addValue("codes", List.of(props.inviteCode(), props.secondaryInviteCode())),
                Integer.class);
        if (foreign != null && foreign > 0) {
            throw new IllegalStateException("A non-demo event already uses invite code "
                    + props.inviteCode() + " or " + props.secondaryInviteCode()
                    + ". Set DEMO_INVITE_CODE / DEMO_SECONDARY_INVITE_CODE to unused codes.");
        }

        List<String> keys = new ArrayList<>();
        jdbc.query("""
                SELECT m.object_key, m.thumbnail_key FROM media m
                  JOIN events e ON e.id = m.event_id
                 WHERE e.host_id IN (:hostIds)
                """, params, rs -> {
            keys.add(rs.getString("object_key"));
            String thumb = rs.getString("thumbnail_key");
            if (thumb != null) {
                keys.add(thumb);
            }
        });

        jdbc.update("""
                DELETE FROM audit_logs
                 WHERE event_id IN (SELECT id FROM events WHERE host_id IN (:hostIds))
                """, params);
        int deletedEvents = jdbc.update("DELETE FROM events WHERE host_id IN (:hostIds)", params);
        // Promo redemptions made with the demo accounts during an interview.
        jdbc.update("DELETE FROM subscriptions WHERE user_id IN (:hostIds)", params);
        jdbc.update("DELETE FROM promo_codes WHERE upper(code) = :code",
                new MapSqlParameterSource("code", props.promoCode()));
        log.debug("Demo reset removed {} events and {} media objects", deletedEvents, keys.size());
        return keys;
    }

    // ---- seed ----

    private void seedMainEvent(User host, UUID eventId, List<PlannedPhoto> photos) {
        LocalDate today = LocalDate.now(ZoneId.of(props.resetZone()));
        Event event = new Event();
        event.setId(eventId);
        event.setHostId(host.getId());
        event.setName("Amara & Kofi's Wedding");
        event.setDescription("Demo event. Scan the QR code or open the guest link to add photos. "
                + "Everything here is reset automatically every night.");
        event.setEventType(EventType.WEDDING);
        event.setInviteCode(props.inviteCode());
        event.setStatus(EventStatus.ACTIVE);
        event.setEventDate(today.minusDays(2));
        event.setAutoApprove(true);
        event.setAllowGuestDownloads(true);
        events.save(event);
        backdate("events", eventId, Instant.now().minus(Duration.ofDays(21)));

        hostMembership(host, eventId, Instant.now().minus(Duration.ofDays(21)));
        List<EventMembership> guests = new ArrayList<>();
        Random random = new Random(42);
        for (int i = 0; i < GUESTS.length; i++) {
            EventMembership m = new EventMembership();
            m.setId(UUID.randomUUID());
            m.setEventId(eventId);
            m.setGuestDisplayName(GUESTS[i]);
            m.setRole(MembershipRole.GUEST);
            m.setStatus(MembershipStatus.ACTIVE);
            m.setJoinedAt(Instant.now().minus(Duration.ofHours(30 + random.nextInt(40))));
            guests.add(memberships.save(m));
        }

        UUID firstPhotoId = photos.get(0).mediaId();
        for (int i = 0; i < photos.size(); i++) {
            PlannedPhoto p = photos.get(i);
            EventMembership uploader = guests.get(i % guests.size());
            Media row = baseMedia(p, eventId);
            row.setUploaderMembershipId(uploader.getId());
            row.setUploaderDisplayName(uploader.getGuestDisplayName());
            if (p.duplicate()) {
                row.setDuplicate(true);
                row.setDuplicateOfId(firstPhotoId);
            }
            // Two photos start hidden so the host's moderation view has something in it.
            row.setModerationState(i == 3 || i == 7 ? ModerationState.HIDDEN : ModerationState.VISIBLE);
            media.save(row);
            // Newest-first gallery: spread uploads over the last ~48 hours, a few today.
            backdate("media", p.mediaId(), Instant.now().minus(Duration.ofMinutes(
                    (long) (photos.size() - i) * 160 + random.nextInt(60))));
        }

        seedVisits(eventId, 46, 14, random);
    }

    private void seedOffsiteEvent(User host, UUID eventId, List<PlannedPhoto> photos) {
        LocalDate today = LocalDate.now(ZoneId.of(props.resetZone()));
        Event event = new Event();
        event.setId(eventId);
        event.setHostId(host.getId());
        event.setName("Product Team Offsite");
        event.setDescription("Archived demo event: read-only gallery from a past offsite.");
        event.setEventType(EventType.CONFERENCE);
        event.setInviteCode(props.secondaryInviteCode());
        event.setStatus(EventStatus.ARCHIVED);
        event.setEventDate(today.minusDays(35));
        events.save(event);
        backdate("events", eventId, Instant.now().minus(Duration.ofDays(50)));
        hostMembership(host, eventId, Instant.now().minus(Duration.ofDays(50)));

        for (int i = 0; i < photos.size(); i++) {
            PlannedPhoto p = photos.get(i);
            Media row = baseMedia(p, eventId);
            row.setUploaderDisplayName(host.getDisplayName());
            row.setModerationState(ModerationState.VISIBLE);
            media.save(row);
            backdate("media", p.mediaId(), Instant.now().minus(Duration.ofDays(35)).plus(Duration.ofHours(i)));
        }
        seedVisits(eventId, 12, 40, new Random(7));
    }

    private void seedPromo(User host) {
        PromoCode promo = new PromoCode();
        promo.setCode(props.promoCode());
        promo.setType(PromoCodeType.TEMP_PREMIUM);
        promo.setGrantsPlanCode("WEDDING_PRO");
        promo.setDurationDays(30);
        promo.setMaxRedemptions(1000);
        promo.setActive(true);
        promo.setCreatedBy(host.getId());
        promos.save(promo);
    }

    private void hostMembership(User host, UUID eventId, Instant joinedAt) {
        EventMembership m = new EventMembership();
        m.setId(UUID.randomUUID());
        m.setEventId(eventId);
        m.setUserId(host.getId());
        m.setRole(MembershipRole.HOST);
        m.setStatus(MembershipStatus.ACTIVE);
        m.setJoinedAt(joinedAt);
        memberships.save(m);
    }

    private Media baseMedia(PlannedPhoto p, UUID eventId) {
        Media row = new Media();
        row.setId(p.mediaId());
        row.setEventId(eventId);
        row.setOriginalFilename(p.filename());
        row.setContentType("image/jpeg");
        row.setMediaType(MediaType.PHOTO);
        row.setSizeBytes((long) p.bytes().length);
        row.setObjectKey(p.objectKey());
        row.setSha256(p.sha256());
        // UPLOADED: the regular in-process processor generates thumbnails, exactly as
        // for a real guest upload.
        row.setStatus(MediaStatus.UPLOADED);
        return row;
    }

    private void seedVisits(UUID eventId, int visitors, int overDays, Random random) {
        List<MapSqlParameterSource> batch = new ArrayList<>();
        Instant now = Instant.now();
        for (int i = 0; i < visitors; i++) {
            Instant first = now.minus(Duration.ofMinutes(random.nextInt(overDays * 24 * 60)));
            Instant last = first.plus(Duration.ofMinutes(random.nextInt(600)));
            if (last.isAfter(now)) {
                last = now;
            }
            batch.add(new MapSqlParameterSource()
                    .addValue("id", UUID.randomUUID())
                    .addValue("eventId", eventId)
                    .addValue("key", "demo-visitor-" + i)
                    .addValue("first", Timestamp.from(first))
                    .addValue("last", Timestamp.from(last)));
        }
        jdbc.batchUpdate("""
                INSERT INTO event_visits (id, event_id, visitor_key, first_seen_at, last_seen_at)
                VALUES (:id, :eventId, :key, :first, :last)
                """, batch.toArray(new MapSqlParameterSource[0]));
    }

    /** created_at is set by @CreationTimestamp on insert; demo data rewrites it afterwards. */
    private void backdate(String table, UUID id, Instant createdAt) {
        // Flush pending JPA inserts so the UPDATE below finds the row.
        media.flush();
        String sql = switch (table) {
            case "events" -> "UPDATE events SET created_at = :ts WHERE id = :id";
            case "media" -> "UPDATE media SET created_at = :ts WHERE id = :id";
            default -> throw new IllegalArgumentException(table);
        };
        jdbc.update(sql, new MapSqlParameterSource().addValue("ts", Timestamp.from(createdAt)).addValue("id", id));
    }

    // ---- planning ----

    private List<PlannedPhoto> planPhotos(UUID eventId, int count, long seedBase) {
        List<PlannedPhoto> planned = new ArrayList<>(count);
        byte[] firstBytes = null;
        for (int i = 0; i < count; i++) {
            UUID mediaId = UUID.randomUUID();
            // The 6th photo of the main event re-uses the 1st photo's bytes: an exact duplicate.
            boolean duplicate = seedBase == 1L && i == 5 && firstBytes != null;
            byte[] bytes = duplicate ? firstBytes : DemoImageGenerator.jpeg(seedBase * 7919L + i * 104729L, i);
            if (i == 0) {
                firstBytes = bytes;
            }
            String filename = String.format("IMG_%04d.jpg", 2100 + i);
            planned.add(new PlannedPhoto(mediaId, ObjectKeys.original(eventId, mediaId, filename),
                    filename, bytes, sha256(bytes), duplicate));
        }
        return planned;
    }

    private void deleteQuietly(List<String> keys) {
        if (keys == null) {
            return;
        }
        for (String key : keys) {
            try {
                storage.deleteObject(key);
            } catch (RuntimeException e) {
                log.warn("Demo cleanup could not delete R2 object {}: {}", key, e.getMessage());
            }
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static <T> List<T> concat(List<T> a, List<T> b) {
        List<T> all = new ArrayList<>(a);
        all.addAll(b);
        return all;
    }

    private static String firstName(String full) {
        int space = full.indexOf(' ');
        return space < 0 ? full : full.substring(0, space);
    }

    private static String lastName(String full) {
        int space = full.indexOf(' ');
        return space < 0 ? null : full.substring(space + 1);
    }

    record PlannedPhoto(UUID mediaId, String objectKey, String filename, byte[] bytes,
                        String sha256, boolean duplicate) {
    }

    /** Mutable holder: the local host user id is known only after the upsert. */
    static final class DemoAccounts {
        private final String hostClerkId;
        private final String adminClerkId;
        private UUID hostUserId;

        DemoAccounts(String hostClerkId, String adminClerkId) {
            this.hostClerkId = hostClerkId;
            this.adminClerkId = adminClerkId;
        }

        String hostClerkId() {
            return hostClerkId;
        }

        String adminClerkId() {
            return adminClerkId;
        }

        UUID hostUserIdOrThrow() {
            if (hostUserId == null) {
                throw new IllegalStateException("Demo host user not provisioned");
            }
            return hostUserId;
        }
    }

    public record DemoResetResult(String trigger, Instant finishedAt, int photosSeeded,
                                  int oldObjectsRemoved, long durationMs) {
    }

    /** Used by DemoController; kept here so the logic and the public view stay together. */
    Map<String, Object> publicInfo() {
        return Map.of(
                "inviteCode", props.inviteCode(),
                "secondaryInviteCode", props.secondaryInviteCode(),
                "promoCode", props.promoCode(),
                "resetCron", props.resetCron(),
                "resetZone", props.resetZone());
    }
}
