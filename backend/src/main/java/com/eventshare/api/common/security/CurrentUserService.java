package com.eventshare.api.common.security;

import com.eventshare.api.common.error.ForbiddenException;
import com.eventshare.api.user.Role;
import com.eventshare.api.user.User;
import com.eventshare.api.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Just-in-time user provisioning from the Clerk token. Also enforces account
 * suspension, refreshes last-seen (throttled), and promotes seeded admins: any
 * verified email in {@code eventshare.admin-emails} is granted ROLE_ADMIN.
 */
@Service
public class CurrentUserService {

    private static final Duration LAST_SEEN_THROTTLE = Duration.ofMinutes(5);

    private final UserRepository users;
    private final Set<String> adminEmails;

    public CurrentUserService(UserRepository users,
                              @Value("${eventshare.admin-emails:}") String adminEmailsRaw) {
        this.users = users;
        this.adminEmails = Arrays.stream(adminEmailsRaw.split(","))
                .map(s -> s.trim().toLowerCase())
                .filter(s -> !s.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    @Transactional
    public User provisionFromJwt(Jwt jwt) {
        String clerkUserId = jwt.getSubject();
        User user = users.findByClerkUserId(clerkUserId)
                .orElseGet(() -> createFromJwt(clerkUserId, jwt));
        if (user.isDisabled()) {
            throw new ForbiddenException("This account has been disabled");
        }
        reconcile(user);
        return user;
    }

    private User createFromJwt(String clerkUserId, Jwt jwt) {
        User user = new User();
        user.setClerkUserId(clerkUserId);
        String email = stringClaim(jwt, "email");
        user.setEmail(email);
        user.setDisplayName(firstNonBlank(
                stringClaim(jwt, "name"),
                stringClaim(jwt, "username"),
                email));
        user.setAvatarUrl(stringClaim(jwt, "picture"));
        user.setRole(isAdminEmail(email) ? Role.ADMIN : Role.HOST);
        user.setLastSeenAt(Instant.now());
        try {
            return users.save(user);
        } catch (DataIntegrityViolationException raceLost) {
            return users.findByClerkUserId(clerkUserId).orElseThrow(() -> raceLost);
        }
    }

    private void reconcile(User user) {
        boolean dirty = false;
        if (isAdminEmail(user.getEmail()) && user.getRole() != Role.ADMIN) {
            user.setRole(Role.ADMIN);
            dirty = true;
        }
        Instant now = Instant.now();
        if (user.getLastSeenAt() == null
                || Duration.between(user.getLastSeenAt(), now).compareTo(LAST_SEEN_THROTTLE) >= 0) {
            user.setLastSeenAt(now);
            dirty = true;
        }
        if (dirty) {
            users.save(user);
        }
    }

    private boolean isAdminEmail(String email) {
        return email != null && adminEmails.contains(email.toLowerCase());
    }

    private static String stringClaim(Jwt jwt, String name) {
        Object value = jwt.getClaims().get(name);
        return value == null ? null : value.toString();
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }
}
