package com.eventshare.api.demo;

import com.eventshare.api.common.error.ForbiddenException;
import com.eventshare.api.common.error.QuotaExceededException;
import com.eventshare.api.common.util.Hashing;
import com.eventshare.api.event.Event;
import com.eventshare.api.media.MediaRepository;
import com.eventshare.api.user.User;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * Central policy for the public interview demo (changes 2026-10-05-DG and DG6 to DG11).
 *
 * <p>The demo host is a real Clerk-backed account with public credentials, so it must not
 * get a second, unauthorised product surface merely because it is whitelisted for plan
 * limits, and it must not be able to break or deface the shared demo for other visitors.
 * Upload allowances are checked before a PENDING media row and presigned URL are created.
 */
@Service
public class DemoGuard {

    private static final String IP_HASH_SALT = "eventshare-demo-upload|";

    private final DemoProperties props;
    private final MediaRepository media;

    public DemoGuard(DemoProperties props, MediaRepository media) {
        this.props = props;
        this.media = media;
    }

    // ---- demo host: what the shared account may do ----

    public void assertCanCreateEvent(User host) {
        if (isDemoHost(host)) {
            throw new ForbiddenException("The demo host cannot create additional events.");
        }
    }

    /** Applies the same restriction to every owner-only operation. */
    public void assertCanManage(User host, Event event) {
        if (isDemoHost(host) && !isDemoEvent(event)) {
            throw new ForbiddenException("The demo host can only manage the two seeded demo events.");
        }
    }

    /** DG7: deleting a seeded event would break the public guest link until the next reset. */
    public void assertCanDeleteEvent(Event event) {
        if (isDemoEvent(event)) {
            throw new ForbiddenException("The demo events cannot be deleted. They reset automatically every night.");
        }
    }

    /**
     * DG7: the event name and cover are shown on the public guest page, so changing them
     * on a demo event would let anyone with the shared login deface it. Display toggles
     * (names, timestamps, stats, anonymity) stay editable; the nightly reset restores them.
     */
    public void assertCanEditIdentity(Event event, boolean changesName, boolean changesCover) {
        if (isDemoEvent(event) && (changesName || changesCover)) {
            throw new ForbiddenException("The demo event's name and cover photo cannot be changed.");
        }
    }

    /** DG7: hiding and restoring photos is allowed on the demo; permanent deletion is not. */
    public void assertCanPermanentlyDelete(Event event) {
        if (isDemoEvent(event)) {
            throw new ForbiddenException(
                    "Photos in the demo cannot be permanently deleted. Hide them instead; the demo resets every night.");
        }
    }

    /** DG8: the shared demo account must not open Stripe checkout or the billing portal. */
    public void assertCanUseBilling(User user) {
        if (isDemoHost(user) || isDemoAdmin(user)) {
            throw new ForbiddenException("Billing is not available for the demo accounts.");
        }
    }

    // ---- guest uploads to the demo event ----

    /**
     * Must run inside the upload reservation transaction, after the host row is locked.
     * Enforces the switch, the per-file size cap, the shared per-reset cap and the per-IP
     * cap (DG6). Returns the hashed client IP to store on the new media row so later
     * requests can count it, or {@code null} for non-demo events (nothing is stored).
     */
    public String assertGuestUploadAllowed(Event event, long sizeBytes, String clientIp) {
        if (!isDemoEvent(event)) {
            return null;
        }
        if (!props.guestUploadsEnabled()) {
            throw new ForbiddenException("Uploads are disabled for the public demo.");
        }
        if (sizeBytes > props.maxGuestUploadBytes()) {
            throw new QuotaExceededException("Public demo uploads are limited to "
                    + formatMegabytes(props.maxGuestUploadBytes()) + " per file.");
        }
        long total = media.countByEventIdAndDemoSeededFalse(event.getId());
        if (total >= props.maxGuestUploads()) {
            throw new QuotaExceededException(
                    "The public demo upload limit has been reached. Try again after the next reset.");
        }
        String ipHash = hashIp(clientIp);
        long fromThisIp = media.countByEventIdAndDemoSeededFalseAndUploaderIpHash(event.getId(), ipHash);
        if (fromThisIp >= props.maxGuestUploadsPerIp()) {
            throw new QuotaExceededException("You have used your " + props.maxGuestUploadsPerIp()
                    + " demo uploads. The demo resets every night.");
        }
        return ipHash;
    }

    /**
     * Uploads this visitor can still make to a demo event: the smaller of the shared and
     * per-IP allowances. {@code null} for non-demo events (no demo limit applies).
     */
    public Integer remainingGuestUploads(Event event, String clientIp) {
        if (!isDemoEvent(event)) {
            return null;
        }
        if (!guestUploadsEnabled(event)) {
            return 0;
        }
        long shared = props.maxGuestUploads() - media.countByEventIdAndDemoSeededFalse(event.getId());
        long perIp = props.maxGuestUploadsPerIp()
                - media.countByEventIdAndDemoSeededFalseAndUploaderIpHash(event.getId(), hashIp(clientIp));
        return (int) Math.max(0, Math.min(shared, perIp));
    }

    /** Per-file size limit for a demo event, or {@code null} for other events. */
    public Long maxUploadBytes(Event event) {
        return isDemoEvent(event) ? props.maxGuestUploadBytes() : null;
    }

    public boolean guestUploadsEnabled(Event event) {
        if (event == null || !event.isActive()) {
            return false;
        }
        return !isDemoEvent(event) || (props.guestUploadsEnabled() && props.maxGuestUploads() > 0);
    }

    public boolean isDemoEvent(Event event) {
        if (!props.enabled() || event == null || event.getInviteCode() == null) {
            return false;
        }
        String code = event.getInviteCode().trim().toUpperCase(Locale.ROOT);
        return code.equals(props.inviteCode()) || code.equals(props.secondaryInviteCode());
    }

    /**
     * Salted SHA-256 of the client IP. Only stored on demo-event uploads, which the nightly
     * reset deletes, so no long-lived IP data is kept.
     */
    static String hashIp(String clientIp) {
        return Hashing.sha256Hex(IP_HASH_SALT + (clientIp == null ? "unknown" : clientIp.trim()));
    }

    static String formatMegabytes(long bytes) {
        long mb = bytes / (1024 * 1024);
        return mb * 1024 * 1024 == bytes ? mb + " MB" : String.format(Locale.ROOT, "%.1f MB", bytes / 1048576.0);
    }

    private boolean isDemoHost(User user) {
        return props.enabled() && user != null && user.getEmail() != null
                && props.hostLocalEmail().equalsIgnoreCase(user.getEmail());
    }

    private boolean isDemoAdmin(User user) {
        return props.enabled() && props.adminEnabled() && user != null && user.getEmail() != null
                && props.adminLocalEmail().equalsIgnoreCase(user.getEmail());
    }
}
