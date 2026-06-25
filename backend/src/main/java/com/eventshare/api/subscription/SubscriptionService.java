package com.eventshare.api.subscription;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Owns the subscription record lifecycle (created/updated via billing webhooks or admin). */
@Service
public class SubscriptionService {

    private final SubscriptionRepository subscriptions;

    public SubscriptionService(SubscriptionRepository subscriptions) {
        this.subscriptions = subscriptions;
    }

    @Transactional(readOnly = true)
    public Optional<Subscription> current(UUID userId) {
        return subscriptions.findByUserIdAndDeletedAtIsNull(userId);
    }

    /** Creates or updates the user's single active subscription. */
    @Transactional
    public Subscription activate(UUID userId, String planCode, SubscriptionSource source,
                                 String stripeSubscriptionId, Instant currentPeriodEnd) {
        Subscription subscription = subscriptions.findByUserIdAndDeletedAtIsNull(userId)
                .orElseGet(() -> {
                    Subscription created = new Subscription();
                    created.setUserId(userId);
                    return created;
                });
        subscription.setPlanCode(planCode);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setSource(source);
        subscription.setStripeSubscriptionId(stripeSubscriptionId);
        subscription.setCurrentPeriodEnd(currentPeriodEnd);
        subscription.setCancelAtPeriodEnd(false);
        return subscriptions.save(subscription);
    }

    @Transactional
    public void updateFromStripe(String stripeSubscriptionId, SubscriptionStatus status,
                                 Instant currentPeriodEnd, boolean cancelAtPeriodEnd) {
        subscriptions.findByStripeSubscriptionId(stripeSubscriptionId).ifPresent(subscription -> {
            subscription.setStatus(status);
            if (currentPeriodEnd != null) {
                subscription.setCurrentPeriodEnd(currentPeriodEnd);
            }
            subscription.setCancelAtPeriodEnd(cancelAtPeriodEnd);
            subscriptions.save(subscription);
        });
    }

    @Transactional
    public void markCanceled(String stripeSubscriptionId) {
        subscriptions.findByStripeSubscriptionId(stripeSubscriptionId).ifPresent(subscription -> {
            subscription.setStatus(SubscriptionStatus.CANCELED);
            subscriptions.save(subscription);
        });
    }
}
