package com.eventshare.api.billing;

import com.eventshare.api.common.error.ApiException;
import com.eventshare.api.common.error.BadRequestException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Talks to the Stripe REST API directly (JDK HTTP client + form encoding) to
 * avoid coupling the build to a specific stripe-java SDK version. Handles
 * customer/checkout/portal creation and webhook signature verification.
 */
@Service
public class StripeGateway {

    private static final String API = "https://api.stripe.com";

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper mapper;

    private final String secretKey;
    private final String webhookSecret;
    private final String successUrl;
    private final String cancelUrl;
    private final String priceBasic;
    private final String priceWeddingPro;
    private final String priceLifetime;

    public StripeGateway(ObjectMapper mapper,
                         @Value("${eventshare.stripe.secret-key:}") String secretKey,
                         @Value("${eventshare.stripe.webhook-secret:}") String webhookSecret,
                         @Value("${eventshare.stripe.success-url:}") String successUrl,
                         @Value("${eventshare.stripe.cancel-url:}") String cancelUrl,
                         @Value("${eventshare.stripe.price-basic:}") String priceBasic,
                         @Value("${eventshare.stripe.price-wedding-pro:}") String priceWeddingPro,
                         @Value("${eventshare.stripe.price-lifetime:}") String priceLifetime) {
        this.mapper = mapper;
        this.secretKey = secretKey;
        this.webhookSecret = webhookSecret;
        this.successUrl = successUrl;
        this.cancelUrl = cancelUrl;
        this.priceBasic = priceBasic;
        this.priceWeddingPro = priceWeddingPro;
        this.priceLifetime = priceLifetime;
    }

    public boolean isConfigured() {
        return secretKey != null && !secretKey.isBlank();
    }

    public boolean isSubscriptionPlan(String planCode) {
        return !"LIFETIME".equals(planCode);
    }

    public String priceIdFor(String planCode) {
        return switch (planCode) {
            case "BASIC" -> priceBasic;
            case "WEDDING_PRO" -> priceWeddingPro;
            case "LIFETIME" -> priceLifetime;
            default -> null;
        };
    }

    public String createCustomer(String email, UUID userId) {
        Map<String, String> params = new LinkedHashMap<>();
        if (email != null && !email.isBlank()) {
            params.put("email", email);
        }
        params.put("metadata[userId]", userId.toString());
        return post("/v1/customers", params).path("id").asText();
    }

    public String createCheckoutSession(String customerId, String planCode, UUID userId) {
        String priceId = priceIdFor(planCode);
        if (priceId == null || priceId.isBlank()) {
            throw new BadRequestException("No Stripe price configured for plan " + planCode);
        }
        boolean subscription = isSubscriptionPlan(planCode);

        Map<String, String> params = new LinkedHashMap<>();
        params.put("mode", subscription ? "subscription" : "payment");
        params.put("success_url", successUrl);
        params.put("cancel_url", cancelUrl);
        params.put("customer", customerId);
        params.put("line_items[0][price]", priceId);
        params.put("line_items[0][quantity]", "1");
        params.put("metadata[userId]", userId.toString());
        params.put("metadata[planCode]", planCode);
        if (subscription) {
            params.put("subscription_data[metadata][userId]", userId.toString());
            params.put("subscription_data[metadata][planCode]", planCode);
        }
        return post("/v1/checkout/sessions", params).path("url").asText();
    }

    public String createPortalSession(String customerId) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("customer", customerId);
        params.put("return_url", successUrl);
        return post("/v1/billing_portal/sessions", params).path("url").asText();
    }

    /** Verifies the Stripe-Signature header and returns the parsed event. */
    public WebhookEvent verifyAndParse(String payload, String signatureHeader) {
        if (!verifySignature(payload, signatureHeader)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_signature", "Invalid Stripe signature");
        }
        try {
            JsonNode root = mapper.readTree(payload);
            return new WebhookEvent(root.path("type").asText(), root.path("data").path("object"));
        } catch (Exception e) {
            throw new BadRequestException("Malformed webhook payload");
        }
    }

    // ---- internals ----

    private JsonNode post(String path, Map<String, String> params) {
        if (!isConfigured()) {
            throw new BadRequestException("Billing is not configured on the server");
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(API + path))
                    .header("Authorization", "Bearer " + secretKey)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(formEncode(params)))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode node = mapper.readTree(response.body());
            if (response.statusCode() >= 300) {
                String message = node.path("error").path("message").asText("Stripe request failed");
                throw new ApiException(HttpStatus.BAD_GATEWAY, "stripe_error", message);
            }
            return node;
        } catch (java.io.IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.BAD_GATEWAY, "stripe_error", "Could not reach Stripe: " + e.getMessage());
        }
    }

    private String formEncode(Map<String, String> params) {
        return params.entrySet().stream()
                .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .collect(Collectors.joining("&"));
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private boolean verifySignature(String payload, String signatureHeader) {
        if (webhookSecret == null || webhookSecret.isBlank() || signatureHeader == null) {
            return false;
        }
        String timestamp = null;
        String v1 = null;
        for (String part : signatureHeader.split(",")) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2) {
                if (kv[0].equals("t")) {
                    timestamp = kv[1];
                } else if (kv[0].equals("v1")) {
                    v1 = kv[1];
                }
            }
        }
        if (timestamp == null || v1 == null) {
            return false;
        }
        String expected = hmacSha256Hex(webhookSecret, timestamp + "." + payload);
        return constantTimeEquals(expected, v1);
    }

    private String hmacSha256Hex(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] result = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(result.length * 2);
            for (byte b : result) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("HMAC failure", e);
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }
}
