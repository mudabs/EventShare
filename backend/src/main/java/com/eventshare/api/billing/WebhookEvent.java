package com.eventshare.api.billing;

import com.fasterxml.jackson.databind.JsonNode;

/** A verified Stripe webhook event: its type and the data.object payload. */
public record WebhookEvent(String type, JsonNode data) {
}
