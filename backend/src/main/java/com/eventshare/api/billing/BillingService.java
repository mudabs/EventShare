package com.eventshare.api.billing;

import com.eventshare.api.common.error.BadRequestException;
import com.eventshare.api.subscription.SubscriptionService;
import com.eventshare.api.subscription.SubscriptionSource;
import com.eventshare.api.subscription.SubscriptionStatus;
import com.eventshare.api.user.User;
import com.eventshare.api.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class BillingService {

    private final StripeGateway gateway;
    private final UserRepository users;
    private final SubscriptionService subscriptions;

    public BillingService(StripeGateway gateway, UserRepository users, SubscriptionService subscriptions) {
        this.gateway = gateway;
        this.users = users;
        this.subscriptions = subscriptions;
    }

    @Transactional
    public String createCheckout(User user, String planCode) {
        if (!gateway.isConfigured()) {
            throw new BadRequestException("Billing is not configured on the server");
        }
        if ("FREE".equals(planCode)) {
            throw new BadRequestException("The Free plan does not require checkout");
        }
        String customerId = ensureCustomer(user);
        return gateway.createCheckoutSession(customerId, planCode, user.getId());
    }

    @Transactional
    public String createPortal(User user) {
        if (user.getStripeCustomerId() == null || user.getStripeCustomerId().isBlank()) {
            throw new BadRequestException("No billing account exists for this user yet");
        }
        return gateway.createPortalSession(user.getStripeCustomerId());
    }

    /** Stripe is the source of truth for activation; the browser redirect is not trusted. */
    @Transactional
    public void handleWebhook(String payload, String signature) {
        WebhookEvent event = gateway.verifyAndParse(payload, signature);
        JsonNode object = event.data();
        switch (event.type()) {
            case "checkout.session.completed" -> {
                String userId = text(object.path("metadata").path("userId"));
                String planCode = text(object.path("metadata").path("planCode"));
                if (userId == null || planCode == null) {
                    return;
                }
                String subscriptionId = "subscription".equals(object.path("mode").asText())
                        ? text(object.path("subscription")) : null;
                subscriptions.activate(UUID.fromString(userId), planCode,
                        SubscriptionSource.STRIPE, subscriptionId, null);
            }
            case "customer.subscription.updated" -> {
                String subscriptionId = object.path("id").asText();
                long periodEnd = object.path("current_period_end").asLong(0);
                boolean cancelAtEnd = object.path("cancel_at_period_end").asBoolean(false);
                subscriptions.updateFromStripe(subscriptionId, mapStatus(object.path("status").asText()),
                        periodEnd > 0 ? Instant.ofEpochSecond(periodEnd) : null, cancelAtEnd);
            }
            case "customer.subscription.deleted" ->
                    subscriptions.markCanceled(object.path("id").asText());
            default -> {
                // other event types are ignored
            }
        }
    }

    private String ensureCustomer(User user) {
        if (user.getStripeCustomerId() != null && !user.getStripeCustomerId().isBlank()) {
            return user.getStripeCustomerId();
        }
        String customerId = gateway.createCustomer(user.getEmail(), user.getId());
        user.setStripeCustomerId(customerId);
        users.save(user);
        return customerId;
    }

    private static String text(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? null : node.asText();
    }

    private static SubscriptionStatus mapStatus(String stripeStatus) {
        return switch (stripeStatus) {
            case "active" -> SubscriptionStatus.ACTIVE;
            case "trialing" -> SubscriptionStatus.TRIALING;
            case "past_due", "unpaid", "incomplete" -> SubscriptionStatus.PAST_DUE;
            case "canceled" -> SubscriptionStatus.CANCELED;
            default -> SubscriptionStatus.ACTIVE;
        };
    }
}
