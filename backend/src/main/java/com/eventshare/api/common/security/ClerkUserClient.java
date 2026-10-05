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

    // ---- Demo account management (used only by demo.DemoSeeder) ----

    /**
     * Makes sure a Clerk user with this username (or, if no username is given, this
     * email) exists and that its password is {@code password}, creating the user if
     * needed. Returns the Clerk user id.
     *
     * <p>Used to seed interview demo accounts. With a username, recruiters sign in as
     * "username + password" and no email has to exist (requires Username enabled in the
     * Clerk dashboard and email not marked as required). {@code email} is optional and,
     * when given, is attached as a verified address. {@code skip_password_checks} lets
     * the configured password through Clerk's breach and strength checks so seeding never
     * fails on it. Existing sessions are signed out when the password is reset, so a
     * nightly reset also ends any interviewer's session.
     *
     * @throws IllegalStateException if Clerk is not configured or the API call fails
     */
    public String ensureUserWithPassword(String username, String email, String password,
                                         String firstName, String lastName) {
        boolean hasUsername = username != null && !username.isBlank();
        boolean hasEmail = email != null && !email.isBlank();
        String label = hasUsername ? username : email;
        if (secretKey.isBlank()) {
            throw new IllegalStateException("CLERK_SECRET_KEY is not configured");
        }
        if (!hasUsername && !hasEmail) {
            throw new IllegalStateException("A demo account needs a username or an email");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("No password configured for demo account " + label);
        }
        try {
            Optional<String> existing = hasUsername ? findUserId("username", username) : findUserId("email_address", email);
            if (existing.isPresent()) {
                var body = mapper.createObjectNode()
                        .put("password", password)
                        .put("skip_password_checks", true)
                        .put("sign_out_of_other_sessions", true);
                send("PATCH", BASE + existing.get(), body.toString());
                return existing.get();
            }
            var body = mapper.createObjectNode();
            if (hasUsername) {
                body.put("username", username);
            }
            if (hasEmail) {
                body.putArray("email_address").add(email);
            }
            body.put("password", password);
            body.put("skip_password_checks", true);
            body.put("first_name", firstName);
            body.put("last_name", lastName);
            JsonNode created = mapper.readTree(send("POST", BASE.substring(0, BASE.length() - 1), body.toString()));
            return created.path("id").asText();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Clerk demo account setup failed for " + label + ": " + e.getMessage(), e);
        }
    }

    /** Finds a user id via the List users endpoint filter ({@code username} or {@code email_address}). */
    private Optional<String> findUserId(String filter, String value) throws Exception {
        String url = BASE.substring(0, BASE.length() - 1) + "?" + filter + "="
                + java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
        JsonNode list = mapper.readTree(send("GET", url, null));
        if (list.isArray() && !list.isEmpty()) {
            return Optional.ofNullable(list.get(0).path("id").asText(null));
        }
        return Optional.empty();
    }

    private String send(String method, String url, String jsonBody) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .header("Authorization", "Bearer " + secretKey)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(10));
        if (jsonBody == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(jsonBody));
        }
        HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            // Clerk error bodies describe the problem (e.g. password strategy disabled);
            // they never echo the password back.
            throw new IllegalStateException("Clerk " + method + " returned HTTP " + response.statusCode()
                    + ": " + abbreviate(response.body()));
        }
        return response.body();
    }

    private static String abbreviate(String body) {
        if (body == null) {
            return "";
        }
        return body.length() > 500 ? body.substring(0, 500) + "..." : body;
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
