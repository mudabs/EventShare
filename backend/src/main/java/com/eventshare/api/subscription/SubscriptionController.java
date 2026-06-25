package com.eventshare.api.subscription;

import com.eventshare.api.common.security.CurrentUser;
import com.eventshare.api.subscription.dto.PlanResponse;
import com.eventshare.api.subscription.dto.SubscriptionResponse;
import com.eventshare.api.user.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Optional;

@Tag(name = "Subscription")
@RestController
public class SubscriptionController {

    private final PlanLimitService planLimitService;
    private final SubscriptionService subscriptionService;

    public SubscriptionController(PlanLimitService planLimitService, SubscriptionService subscriptionService) {
        this.planLimitService = planLimitService;
        this.subscriptionService = subscriptionService;
    }

    @Operation(summary = "Current subscription and effective limits")
    @GetMapping("/api/me/subscription")
    public SubscriptionResponse mySubscription(@CurrentUser User user) {
        Plan effective = planLimitService.effectivePlan(user);
        boolean whitelisted = planLimitService.isWhitelisted(user);
        Optional<Subscription> sub = subscriptionService.current(user.getId());

        String status = whitelisted ? "ACTIVE" : sub.map(s -> s.getStatus().name()).orElse("ACTIVE");
        String source = whitelisted ? "WHITELIST" : sub.map(s -> s.getSource().name()).orElse("FREE");
        Instant periodEnd = sub.map(Subscription::getCurrentPeriodEnd).orElse(null);
        boolean cancelAtEnd = sub.map(Subscription::isCancelAtPeriodEnd).orElse(false);

        return new SubscriptionResponse(effective.getCode(), effective.getName(), status, source,
                periodEnd, cancelAtEnd, whitelisted, PlanResponse.from(effective));
    }
}
