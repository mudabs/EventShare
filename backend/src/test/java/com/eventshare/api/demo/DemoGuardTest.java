package com.eventshare.api.demo;

import com.eventshare.api.common.error.ForbiddenException;
import com.eventshare.api.common.error.QuotaExceededException;
import com.eventshare.api.event.Event;
import com.eventshare.api.event.EventStatus;
import com.eventshare.api.media.MediaRepository;
import com.eventshare.api.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Public demo policy (DG1 to DG11). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DemoGuardTest {

    static final long MB25 = 25L * 1024 * 1024;

    @Mock MediaRepository media;

    DemoGuard guard;

    private static DemoProperties props(boolean uploadsEnabled, boolean adminEnabled) {
        return new DemoProperties(true, "demo-host", null, "secret", "Demo Host", adminEnabled,
                "demo-admin", null, "admin-secret", "Demo Admin", "EVENTSHARE", "TEAMDAY26X", "PROMO",
                18, "0 0 4 * * *", "America/Chicago", false, uploadsEnabled, 8, MB25, 4);
    }

    @BeforeEach
    void setUp() {
        guard = new DemoGuard(props(true, true), media);
        when(media.countByEventIdAndDemoSeededFalse(any())).thenReturn(0L);
        when(media.countByEventIdAndDemoSeededFalseAndUploaderIpHash(any(), anyString())).thenReturn(0L);
    }

    // ---- demo host account ----

    @Test
    void demoHostCannotCreateEvents() {
        assertThatThrownBy(() -> guard.assertCanCreateEvent(demoHost()))
                .isInstanceOf(ForbiddenException.class);
        assertThatCode(() -> guard.assertCanCreateEvent(realUser())).doesNotThrowAnyException();
    }

    @Test
    void demoHostCanManageOnlySeededEvents() {
        User host = demoHost();
        guard.assertCanManage(host, event("EVENTSHARE"));
        guard.assertCanManage(host, event("TEAMDAY26X"));
        assertThatThrownBy(() -> guard.assertCanManage(host, event("SOMETHINGELSE")))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void demoEventsCannotBeDeletedRenamedOrPurged() {
        Event demo = event("EVENTSHARE");
        assertThatThrownBy(() -> guard.assertCanDeleteEvent(demo)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> guard.assertCanEditIdentity(demo, true, false)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> guard.assertCanEditIdentity(demo, false, true)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> guard.assertCanPermanentlyDelete(demo)).isInstanceOf(ForbiddenException.class);
        // Display toggles (no name or cover change) stay allowed.
        assertThatCode(() -> guard.assertCanEditIdentity(demo, false, false)).doesNotThrowAnyException();
    }

    @Test
    void realEventsAreNotAffectedByDemoEditRules() {
        Event real = event("REALCODE23");
        assertThatCode(() -> guard.assertCanDeleteEvent(real)).doesNotThrowAnyException();
        assertThatCode(() -> guard.assertCanEditIdentity(real, true, true)).doesNotThrowAnyException();
        assertThatCode(() -> guard.assertCanPermanentlyDelete(real)).doesNotThrowAnyException();
    }

    @Test
    void demoAccountsCannotUseBilling() {
        assertThatThrownBy(() -> guard.assertCanUseBilling(demoHost())).isInstanceOf(ForbiddenException.class);
        User admin = new User();
        admin.setEmail("demo-admin@demo.invalid");
        assertThatThrownBy(() -> guard.assertCanUseBilling(admin)).isInstanceOf(ForbiddenException.class);
        assertThatCode(() -> guard.assertCanUseBilling(realUser())).doesNotThrowAnyException();
    }

    // ---- guest uploads ----

    @Test
    void uploadUnderBothCapsIsAllowedAndReturnsIpHash() {
        Event demo = event("EVENTSHARE");
        when(media.countByEventIdAndDemoSeededFalse(demo.getId())).thenReturn(5L);
        when(media.countByEventIdAndDemoSeededFalseAndUploaderIpHash(demo.getId(), DemoGuard.hashIp("203.0.113.7")))
                .thenReturn(3L);

        String hash = guard.assertGuestUploadAllowed(demo, 100L, "203.0.113.7");

        assertThat(hash).isEqualTo(DemoGuard.hashIp("203.0.113.7")).hasSize(64);
        // The stored value is a hash, never the raw address.
        assertThat(hash).doesNotContain("203.0.113.7");
    }

    @Test
    void sharedCapStopsUploads() {
        Event demo = event("EVENTSHARE");
        when(media.countByEventIdAndDemoSeededFalse(demo.getId())).thenReturn(8L);
        assertThatThrownBy(() -> guard.assertGuestUploadAllowed(demo, 100L, "203.0.113.7"))
                .isInstanceOf(QuotaExceededException.class)
                .hasMessageContaining("upload limit has been reached");
    }

    @Test
    void perIpCapStopsOneVisitorButNotAnother() {
        Event demo = event("EVENTSHARE");
        when(media.countByEventIdAndDemoSeededFalse(demo.getId())).thenReturn(4L);
        when(media.countByEventIdAndDemoSeededFalseAndUploaderIpHash(demo.getId(), DemoGuard.hashIp("203.0.113.7")))
                .thenReturn(4L);
        when(media.countByEventIdAndDemoSeededFalseAndUploaderIpHash(demo.getId(), DemoGuard.hashIp("198.51.100.9")))
                .thenReturn(0L);

        assertThatThrownBy(() -> guard.assertGuestUploadAllowed(demo, 100L, "203.0.113.7"))
                .isInstanceOf(QuotaExceededException.class)
                .hasMessageContaining("4 demo uploads");
        assertThatCode(() -> guard.assertGuestUploadAllowed(demo, 100L, "198.51.100.9"))
                .doesNotThrowAnyException();
    }

    @Test
    void oversizeFileIsRejectedWithReadableLimit() {
        assertThatThrownBy(() -> guard.assertGuestUploadAllowed(event("EVENTSHARE"), MB25 + 1, "203.0.113.7"))
                .isInstanceOf(QuotaExceededException.class)
                .hasMessageContaining("25 MB per file");
    }

    @Test
    void disabledDemoRejectsUploads() {
        DemoGuard readOnly = new DemoGuard(props(false, false), media);
        assertThatThrownBy(() -> readOnly.assertGuestUploadAllowed(event("EVENTSHARE"), 100L, "203.0.113.7"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void nonDemoEventsAreNeverLimitedOrTracked() {
        MediaRepository untouched = mock(MediaRepository.class);
        DemoGuard fresh = new DemoGuard(props(true, true), untouched);
        Event real = event("REALCODE23");
        assertThat(fresh.assertGuestUploadAllowed(real, Long.MAX_VALUE, "203.0.113.7")).isNull();
        assertThat(fresh.remainingGuestUploads(real, "203.0.113.7")).isNull();
        assertThat(fresh.maxUploadBytes(real)).isNull();
        verifyNoInteractions(untouched);
    }

    @Test
    void remainingIsTheSmallerOfSharedAndPerIp() {
        Event demo = event("EVENTSHARE");
        when(media.countByEventIdAndDemoSeededFalse(demo.getId())).thenReturn(6L);           // 2 shared left
        when(media.countByEventIdAndDemoSeededFalseAndUploaderIpHash(eq(demo.getId()), anyString()))
                .thenReturn(1L);                                                            // 3 per-IP left
        assertThat(guard.remainingGuestUploads(demo, "203.0.113.7")).isEqualTo(2);
    }

    @Test
    void megabyteFormatting() {
        assertThat(DemoGuard.formatMegabytes(MB25)).isEqualTo("25 MB");
        assertThat(DemoGuard.formatMegabytes(1572864)).isEqualTo("1.5 MB");
    }

    private User demoHost() {
        User host = new User();
        host.setId(UUID.randomUUID());
        host.setEmail("demo-host@demo.invalid");
        return host;
    }

    private User realUser() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("someone@example.org");
        return user;
    }

    private Event event(String inviteCode) {
        Event event = new Event();
        event.setId(UUID.randomUUID());
        event.setInviteCode(inviteCode);
        event.setStatus(EventStatus.ACTIVE);
        return event;
    }
}
