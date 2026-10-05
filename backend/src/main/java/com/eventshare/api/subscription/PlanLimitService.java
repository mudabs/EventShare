package com.eventshare.api.subscription;

import com.eventshare.api.common.error.QuotaExceededException;
import com.eventshare.api.event.Event;
import com.eventshare.api.event.EventRepository;
import com.eventshare.api.media.MediaRepository;
import com.eventshare.api.media.MediaType;
import com.eventshare.api.media.ModerationState;
import com.eventshare.api.user.Role;
import com.eventshare.api.user.User;
import com.eventshare.api.user.UserRepository;
import com.eventshare.api.whitelist.WhitelistedUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Resolves the effective plan for a user (whitelist overrides everything) and
 * enforces event/upload/storage quotas. A null limit means "unlimited".
 */
@Service
public class PlanLimitService {

    private final PlanRepository plans;
    private final SubscriptionRepository subscriptions;
    private final WhitelistedUserRepository whitelist;
    private final EventRepository events;
    private final MediaRepository media;
    private final UserRepository users;
    private final Set<String> demoEmails;

    public PlanLimitService(PlanRepository plans, SubscriptionRepository subscriptions,
                            WhitelistedUserRepository whitelist, EventRepository events,
                            MediaRepository media, UserRepository users,
                            @Value("${eventshare.demo-emails:}") String demoEmailsRaw) {
        this.plans = plans;
        this.subscriptions = subscriptions;
        this.whitelist = whitelist;
        this.events = events;
        this.media = media;
        this.users = users;
        this.demoEmails = Arrays.stream(demoEmailsRaw.split(","))
                .map(s -> s.trim().toLowerCase())
                .filter(s -> !s.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    @Transactional(readOnly = true)
    public boolean isWhitelisted(User user) {
        if (user.getRole() == Role.ADMIN) {
            return true;
        }
        if (user.getEmail() != null && demoEmails.contains(user.getEmail().toLowerCase())) {
            return true;
        }
        return user.getEmail() != null
                && whitelist.existsByEmailIgnoreCaseAndActiveTrueAndDeletedAtIsNull(user.getEmail());
    }

    @Transactional(readOnly = true)
    public Plan effectivePlan(User user) {
        if (isWhitelisted(user)) {
            return unlimitedPlan();
        }
        String code = subscriptions.findByUserIdAndDeletedAtIsNull(user.getId())
                .filter(s -> s.getStatus() == SubscriptionStatus.ACTIVE
                        || s.getStatus() == SubscriptionStatus.TRIALING)
                .map(Subscription::getPlanCode)
                .orElse("FREE");
        return plans.findById(code).orElseGet(this::unlimitedPlan);
    }

    @Transactional(readOnly = true)
    public void checkCanCreateEvent(User user) {
        Integer maxEvents = effectivePlan(user).getMaxEvents();
        if (maxEvents != null) {
            long current = events.countByHostIdAndDeletedAtIsNull(user.getId());
            if (current >= maxEvents) {
                throw new QuotaExceededException(
                        "Your plan allows up to " + maxEvents + " event(s). Upgrade to create more.");
            }
        }
    }

    /**
     * Enforces per-event photo/video counts and the host's storage cap.
     *
     * <p>Propagation MANDATORY (change C3): this check is only race-safe when it runs in
     * the caller's read-write transaction, after the host row has been locked with
     * {@code UserRepository.findByIdForUpdate}, and before the PENDING media row is
     * inserted. Calling it outside a transaction is a programming error and fails fast.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void checkCanUpload(Event event, MediaType mediaType, long sizeBytes) {
        User owner = users.findById(event.getHostId()).orElse(null);
        if (owner == null) {
            return;
        }
        Plan plan = effectivePlan(owner);

        if (mediaType == MediaType.PHOTO && plan.getMaxPhotosPerEvent() != null) {
            long count = media.countByEventIdAndMediaTypeAndModerationStateNot(
                    event.getId(), MediaType.PHOTO, ModerationState.DELETED);
            if (count >= plan.getMaxPhotosPerEvent()) {
                throw new QuotaExceededException("This event has reached its photo limit.");
            }
        }
        if (mediaType == MediaType.VIDEO && plan.getMaxVideosPerEvent() != null) {
            long count = media.countByEventIdAndMediaTypeAndModerationStateNot(
                    event.getId(), MediaType.VIDEO, ModerationState.DELETED);
            if (count >= plan.getMaxVideosPerEvent()) {
                throw new QuotaExceededException("This event has reached its video limit.");
            }
        }
        if (plan.getStorageBytes() != null) {
            long used = media.sumStorageForHost(owner.getId(), ModerationState.DELETED);
            if (used + sizeBytes > plan.getStorageBytes()) {
                throw new QuotaExceededException("This event has reached its storage limit.");
            }
        }
    }

    private Plan unlimitedPlan() {
        Plan plan = new Plan();
        plan.setCode("UNLIMITED");
        plan.setName("Unlimited");
        plan.setPriceCents(0);
        plan.setBillingInterval("NONE");
        plan.setZipExport(true);
        plan.setAdvancedAnalytics(true);
        plan.setPriorityProcessing(true);
        plan.setActive(true);
        return plan;
    }
}
