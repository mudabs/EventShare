package com.eventshare.api.subscription;

import com.eventshare.api.event.Event;
import com.eventshare.api.event.EventRepository;
import com.eventshare.api.media.MediaRepository;
import com.eventshare.api.user.Role;
import com.eventshare.api.user.User;
import com.eventshare.api.user.UserRepository;
import com.eventshare.api.whitelist.WhitelistedUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/** ZIP downloads are a paid-plan feature of the event host. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlanLimitServiceZipTest {

    @Mock PlanRepository plans;
    @Mock SubscriptionRepository subscriptions;
    @Mock WhitelistedUserRepository whitelist;
    @Mock EventRepository events;
    @Mock MediaRepository media;
    @Mock UserRepository users;

    PlanLimitService service;
    User host;
    Event event;

    @BeforeEach
    void setUp() {
        service = new PlanLimitService(plans, subscriptions, whitelist, events, media, users, "");
        host = new User();
        host.setId(UUID.randomUUID());
        host.setEmail("host@example.org");
        host.setRole(Role.HOST);
        event = new Event();
        event.setHostId(host.getId());
        when(users.findById(host.getId())).thenReturn(Optional.of(host));
        when(whitelist.existsByEmailIgnoreCaseAndActiveTrueAndDeletedAtIsNull(anyString())).thenReturn(false);
        when(subscriptions.findByUserIdAndDeletedAtIsNull(host.getId())).thenReturn(Optional.empty());
    }

    private static Plan plan(String code, boolean zip) {
        Plan p = new Plan();
        p.setCode(code);
        p.setZipExport(zip);
        return p;
    }

    @Test
    void freePlanHostHasNoZip() {
        when(plans.findById("FREE")).thenReturn(Optional.of(plan("FREE", false)));
        assertThat(service.hostHasZipExport(event)).isFalse();
    }

    @Test
    void paidPlanHostHasZip() {
        Subscription sub = new Subscription();
        sub.setPlanCode("BASIC");
        sub.setStatus(SubscriptionStatus.ACTIVE);
        when(subscriptions.findByUserIdAndDeletedAtIsNull(host.getId())).thenReturn(Optional.of(sub));
        when(plans.findById("BASIC")).thenReturn(Optional.of(plan("BASIC", true)));
        assertThat(service.hostHasZipExport(event)).isTrue();
    }

    @Test
    void whitelistedHostHasZip() {
        when(whitelist.existsByEmailIgnoreCaseAndActiveTrueAndDeletedAtIsNull("host@example.org")).thenReturn(true);
        assertThat(service.hostHasZipExport(event)).isTrue();
    }
}
