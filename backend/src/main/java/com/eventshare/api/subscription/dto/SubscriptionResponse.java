package com.eventshare.api.subscription.dto;

import java.time.Instant;

public record SubscriptionResponse(
        String planCode,
        String planName,
        String status,
        String source,
        Instant currentPeriodEnd,
        boolean cancelAtPeriodEnd,
        boolean whitelisted,
        PlanResponse effectivePlan
) {
}
