package com.eventshare.api.demo;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the interview/demo mode (see docs/DEMO.md).
 *
 * <p>Everything is off unless {@code eventshare.demo.enabled=true}. Passwords are never
 * defaulted in code: they come from the environment ({@code DEMO_HOST_PASSWORD},
 * {@code DEMO_ADMIN_PASSWORD}), which {@code scripts/demo-up.*} generates into the
 * git-ignored {@code .env.demo}.
 *
 * @param enabled          master switch for seeding, nightly reset and the demo endpoints
 * @param hostUsername     demo host sign-in name (default {@code demo-host}); recruiters
 *                         sign in with this and the password, no email needed
 * @param hostEmail        optional email for the demo host's Clerk account; leave blank
 *                         for a username-only login
 * @param hostPassword     password set on the demo host's Clerk account at every reset
 * @param hostName         display name for the demo host
 * @param adminEnabled     also create a demo platform admin. Keep false on a live site
 *                         with real users: the admin panel shows every account
 * @param adminUsername    demo admin sign-in name (default {@code demo-admin})
 * @param adminEmail       optional email for the demo admin's Clerk account
 * @param adminPassword    password set on the demo admin's Clerk account at every reset
 * @param adminName        display name for the demo admin
 * @param inviteCode       fixed invite code of the main showcase event (guest link)
 * @param secondaryInviteCode fixed invite code of the archived second event
 * @param promoCode        demo promo code (grants Wedding Pro for 30 days)
 * @param photoCount       number of generated photos in the main event
 * @param resetCron        Spring cron for the automatic reset
 * @param resetZone        time zone for {@code resetCron}
 * @param showCredentials  expose the demo logins on the public /api/demo/info endpoint
 *                         (shown on the landing page). Only for throwaway demo accounts
 * @param guestUploadsEnabled whether guests may upload to the seeded public event
 * @param maxGuestUploads maximum non-seeded upload reservations per reset for the public event
 * @param maxGuestUploadBytes maximum size of one public-demo upload
 * @param maxGuestUploadsPerIp maximum upload reservations per client IP address per reset
 *                         (default 4), so one visitor cannot use the whole shared allowance
 */
@ConfigurationProperties(prefix = "eventshare.demo")
public record DemoProperties(
        boolean enabled,
        String hostUsername,
        String hostEmail,
        String hostPassword,
        String hostName,
        boolean adminEnabled,
        String adminUsername,
        String adminEmail,
        String adminPassword,
        String adminName,
        String inviteCode,
        String secondaryInviteCode,
        String promoCode,
        int photoCount,
        String resetCron,
        String resetZone,
        boolean showCredentials,
        boolean guestUploadsEnabled,
        int maxGuestUploads,
        long maxGuestUploadBytes,
        int maxGuestUploadsPerIp
) {
    public DemoProperties {
        hostUsername = blankToDefault(hostUsername, "demo-host");
        hostEmail = blankToNull(hostEmail);
        hostName = blankToDefault(hostName, "Demo Host");
        adminUsername = blankToDefault(adminUsername, "demo-admin");
        adminEmail = blankToNull(adminEmail);
        adminName = blankToDefault(adminName, "Demo Admin");
        inviteCode = blankToDefault(inviteCode, "EVENTSHARE").toUpperCase();
        secondaryInviteCode = blankToDefault(secondaryInviteCode, "TEAMDAY26X").toUpperCase();
        promoCode = blankToDefault(promoCode, "INTERVIEW30").toUpperCase();
        photoCount = photoCount <= 0 ? 18 : Math.min(photoCount, 60);
        resetCron = blankToDefault(resetCron, "0 0 4 * * *");
        resetZone = blankToDefault(resetZone, "America/Chicago");
        maxGuestUploads = Math.max(0, maxGuestUploads);
        maxGuestUploadBytes = maxGuestUploadBytes <= 0 ? 25L * 1024 * 1024 : maxGuestUploadBytes;
        maxGuestUploadsPerIp = maxGuestUploadsPerIp <= 0 ? 4 : maxGuestUploadsPerIp;
    }

    /**
     * Email stored on the demo host's local user row. The plan whitelist is keyed by
     * email, so a username-only account gets an address under the reserved
     * {@code .invalid} top-level domain (RFC 2606): it can never receive mail and can
     * never belong to anyone else. It is not sent to Clerk.
     */
    public String hostLocalEmail() {
        return hostEmail != null ? hostEmail : hostUsername + "@demo.invalid";
    }

    /** Local email for the demo admin; see {@link #hostLocalEmail()}. */
    public String adminLocalEmail() {
        return adminEmail != null ? adminEmail : adminUsername + "@demo.invalid";
    }

    /** Masks passwords so the settings can be logged safely. */
    @Override
    public String toString() {
        return "DemoProperties[enabled=" + enabled + ", hostUsername=" + hostUsername
                + ", hostEmail=" + hostEmail + ", hostPassword=" + mask(hostPassword)
                + ", adminEnabled=" + adminEnabled + ", adminUsername=" + adminUsername
                + ", adminEmail=" + adminEmail + ", adminPassword=" + mask(adminPassword)
                + ", inviteCode=" + inviteCode + ", secondaryInviteCode=" + secondaryInviteCode
                + ", promoCode=" + promoCode + ", photoCount=" + photoCount
                + ", resetCron=" + resetCron + ", resetZone=" + resetZone
                + ", showCredentials=" + showCredentials
                + ", guestUploadsEnabled=" + guestUploadsEnabled
                + ", maxGuestUploads=" + maxGuestUploads
                + ", maxGuestUploadBytes=" + maxGuestUploadBytes
                + ", maxGuestUploadsPerIp=" + maxGuestUploadsPerIp + "]";
    }

    private static String mask(String secret) {
        return secret == null || secret.isBlank() ? "<unset>" : "******";
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
