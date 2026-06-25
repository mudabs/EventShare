package com.eventshare.api.subscription;

/** How a subscription was granted. */
public enum SubscriptionSource {
    STRIPE,
    PROMO,
    WHITELIST,
    ADMIN
}
