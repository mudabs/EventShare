package com.eventshare.api.admin;

import com.eventshare.api.admin.dto.AdminEventView;
import com.eventshare.api.admin.dto.AdminUserView;
import com.eventshare.api.admin.dto.MonthCount;
import com.eventshare.api.admin.dto.PlatformStats;
import com.eventshare.api.audit.AuditService;
import com.eventshare.api.common.error.NotFoundException;
import com.eventshare.api.common.security.AdminGuard;
import com.eventshare.api.event.Event;
import com.eventshare.api.event.EventRepository;
import com.eventshare.api.event.EventStatus;
import com.eventshare.api.media.MediaRepository;
import com.eventshare.api.media.ModerationState;
import com.eventshare.api.subscription.Subscription;
import com.eventshare.api.subscription.SubscriptionRepository;
import com.eventshare.api.subscription.SubscriptionService;
import com.eventshare.api.subscription.SubscriptionSource;
import com.eventshare.api.user.User;
import com.eventshare.api.user.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AdminService {

    private static final int PAGE = 100;
    private static final int GROWTH_MONTHS = 6;

    private final UserRepository users;
    private final EventRepository events;
    private final MediaRepository media;
    private final SubscriptionRepository subscriptions;
    private final SubscriptionService subscriptionService;
    private final AdminGuard adminGuard;
    private final AuditService audit;

    public AdminService(UserRepository users, EventRepository events, MediaRepository media,
                        SubscriptionRepository subscriptions, SubscriptionService subscriptionService,
                        AdminGuard adminGuard, AuditService audit) {
        this.users = users;
        this.events = events;
        this.media = media;
        this.subscriptions = subscriptions;
        this.subscriptionService = subscriptionService;
        this.adminGuard = adminGuard;
        this.audit = audit;
    }

    // ---- users ----

    @Transactional(readOnly = true)
    public List<AdminUserView> searchUsers(User admin, String query) {
        adminGuard.requireAdmin(admin);
        return users.search(query == null ? "" : query.trim(), PageRequest.of(0, PAGE)).stream()
                .map(u -> AdminUserView.from(u, planCodeOf(u.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminUserView getUser(User admin, UUID id) {
        adminGuard.requireAdmin(admin);
        User u = users.findById(id).orElseThrow(() -> new NotFoundException("User not found"));
        return AdminUserView.from(u, planCodeOf(id));
    }

    @Transactional
    public void setDisabled(User admin, UUID id, boolean disabled) {
        adminGuard.requireAdmin(admin);
        users.findById(id).ifPresent(u -> {
            u.setDisabled(disabled);
            users.save(u);
            audit.record(null, admin.getId(), admin.getDisplayName(),
                    disabled ? "USER_DISABLED" : "USER_ENABLED", "USER", id, null, null);
        });
    }

    @Transactional
    public void deleteUser(User admin, UUID id) {
        adminGuard.requireAdmin(admin);
        users.findById(id).ifPresent(u -> {
            u.setDeletedAt(Instant.now());
            u.setDisabled(true);
            users.save(u);
            audit.record(null, admin.getId(), admin.getDisplayName(), "USER_DELETED", "USER", id, null, null);
        });
    }

    @Transactional
    public void setUserPlan(User admin, UUID id, String planCode) {
        adminGuard.requireAdmin(admin);
        subscriptionService.activate(id, planCode, SubscriptionSource.ADMIN, null, null);
        audit.record(null, admin.getId(), admin.getDisplayName(), "USER_PLAN_SET", "USER", id,
                Map.of("planCode", planCode), null);
    }

    // ---- events ----

    @Transactional(readOnly = true)
    public List<AdminEventView> searchEvents(User admin, String query) {
        adminGuard.requireAdmin(admin);
        return events.searchEvents(query == null ? "" : query.trim(), PageRequest.of(0, PAGE)).stream()
                .map(e -> AdminEventView.from(e, media.countByEventId(e.getId())))
                .toList();
    }

    @Transactional
    public void archiveEvent(User admin, UUID id) {
        adminGuard.requireAdmin(admin);
        events.findByIdAndDeletedAtIsNull(id).ifPresent(e -> {
            e.setStatus(EventStatus.ARCHIVED);
            events.save(e);
            audit.record(id, admin.getId(), admin.getDisplayName(), "EVENT_ARCHIVED", "EVENT", id, null, null);
        });
    }

    @Transactional
    public void removeEvent(User admin, UUID id) {
        adminGuard.requireAdmin(admin);
        events.findByIdAndDeletedAtIsNull(id).ifPresent(e -> {
            e.setDeletedAt(Instant.now());
            events.save(e);
            audit.record(id, admin.getId(), admin.getDisplayName(), "EVENT_REMOVED", "EVENT", id, null, null);
        });
    }

    @Transactional
    public void transferEvent(User admin, UUID id, UUID newOwnerId) {
        adminGuard.requireAdmin(admin);
        if (users.findById(newOwnerId).isEmpty()) {
            throw new NotFoundException("New owner not found");
        }
        Event event = events.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException("Event not found"));
        event.setHostId(newOwnerId);
        events.save(event);
        audit.record(id, admin.getId(), admin.getDisplayName(), "EVENT_TRANSFERRED", "EVENT", id,
                Map.of("newOwner", newOwnerId.toString()), null);
    }

    // ---- analytics ----

    @Transactional(readOnly = true)
    public PlatformStats platformStats(User admin) {
        adminGuard.requireAdmin(admin);
        long totalUsers = users.countByDeletedAtIsNull();
        long totalEvents = events.countByDeletedAtIsNull();
        long totalUploads = media.count();
        long totalStorage = media.sumAllStorage(ModerationState.DELETED);
        return new PlatformStats(totalUsers, totalEvents, totalUploads, totalStorage, monthlyGrowth());
    }

    private String planCodeOf(UUID userId) {
        return subscriptions.findByUserIdAndDeletedAtIsNull(userId)
                .map(Subscription::getPlanCode)
                .orElse("FREE");
    }

    private List<MonthCount> monthlyGrowth() {
        Map<YearMonth, Long> byMonth = users.allCreatedAt().stream()
                .collect(Collectors.groupingBy(
                        ts -> YearMonth.from(ts.atZone(ZoneOffset.UTC)),
                        Collectors.counting()));
        List<MonthCount> growth = new ArrayList<>(GROWTH_MONTHS);
        YearMonth now = YearMonth.now(ZoneOffset.UTC);
        for (int i = GROWTH_MONTHS - 1; i >= 0; i--) {
            YearMonth month = now.minusMonths(i);
            growth.add(new MonthCount(month.toString(), byMonth.getOrDefault(month, 0L)));
        }
        return growth;
    }
}
