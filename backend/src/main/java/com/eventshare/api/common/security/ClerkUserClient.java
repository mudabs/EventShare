package com.eventshare.api.common.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

/**
 * Minimal client for the Clerk Backend API. Clerk's default session token omits
 * the email claim, so when a user first authenticates we look up their profile
 * here (once) to obtain the email and name. Requires {@code CLERK_SECRET_KEY};
 * if it is not configured the client is a no-op and returns empty.
 */
@Component
public class ClerkUserClient {

    private static final Logger log = LoggerFactory.getLogger(ClerkUserClient.class);
    private static final String BASE = "https://api.clerk.com/v1/users/";

    private final String secretKey;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    public ClerkUserClient(@Value("${eventshare.auth.clerk-secret-key:}") String secretKey) {
        this.secretKey = secretKey == null ? "" : secretKey.trim();
    }

    public boolean isConfigured() {
        return !secretKey.isBlank();
    }

    /** Looks up a Clerk user by their subject id. Never throws; returns empty on any failure. */
    public Optional<ClerkProfile> fetchProfile(String clerkUserId) {
        if (secretKey.isBlank() || clerkUserId == null || clerkUserId.isBlank()) {
            return Optional.empty();
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(BASE + clerkUserId))
                    .header("Authorization", "Bearer " + secretKey)
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(8))
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                log.warn("Clerk user lookup for {} returned HTTP {}", clerkUserId, response.statusCode());
                return Optional.empty();
            }
            JsonNode root = mapper.readTree(response.body());
            return Optional.of(new ClerkProfile(
                    primaryEmail(root),
                    root.path("first_name").asText(null),
                    root.path("last_name").asText(null),
                    root.path("image_url").asText(null)));
        } catch (Exception e) {
            log.warn("Clerk user lookup for {} failed: {}", clerkUserId, e.getMessage());
            return Optional.empty();
        }
    }

    private static String primaryEmail(JsonNode root) {
        String primaryId = root.path("primary_email_address_id").asText(null);
        String fallback = null;
        for (JsonNode entry : root.path("email_addresses")) {
            String email = entry.path("email_address").asText(null);
            if (email == null) {
                continue;
            }
            if (primaryId != null && primaryId.equals(entry.path("id").asText(null))) {
                return email;
            }
            if (fallback == null) {
                fallback = email;
            }
        }
        return fallback;
    }

    public record ClerkProfile(String email, String firstName, String lastName, String imageUrl) {
        public String fullName() {
            String first = firstName == null ? "" : firstName.trim();
            String last = lastName == null ? "" : lastName.trim();
            String joined = (first + " " + last).trim();
            return joined.isBlank() ? null : joined;
        }
    }
}
