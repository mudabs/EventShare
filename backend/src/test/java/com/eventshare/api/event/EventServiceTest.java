package com.eventshare.api.event;

import com.eventshare.api.audit.AuditService;
import com.eventshare.api.common.error.ForbiddenException;
import com.eventshare.api.common.error.TooManyRequestsException;
import com.eventshare.api.common.util.InviteCodeGenerator;
import com.eventshare.api.common.util.RateLimiter;
import com.eventshare.api.config.AppProperties;
import com.eventshare.api.event.dto.CreateEventRequest;
import com.eventshare.api.event.dto.EventResponse;
import com.eventshare.api.event.dto.JoinEventRequest;
import com.eventshare.api.event.dto.JoinEventResponse;
import com.eventshare.api.media.MediaRepository;
import com.eventshare.api.media.r2.R2StorageService;
import com.eventshare.api.user.User;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EventServiceTest {

    @Mock EventRepository events;
    @Mock EventMembershipRepository memberships;
    @Mock MediaRepository media;
    @Mock R2StorageService storage;
    @Mock InviteCodeGenerator inviteCodes;
    @Mock AuditService audit;
    @Mock RateLimiter rateLimiter;

    EventService service;
    User host;

    private static AppProperties props() {
        return new AppProperties(
                "http://localhost:3000",
                new AppProperties.Cors("http://localhost:3000"),
                new AppProperties.Auth("", ""),
                new AppProperties.R2("http://r2", "auto", "k", "s", "bucket", 900, 3600),
                new AppProperties.Media(524_288_000L, "image/jpeg,video/mp4"),
                new AppProperties.Processing(true, 600, "00:00:01.000", 3, 300),
                new AppProperties.RateLimit(60, 30));
    }

    @BeforeEach
    void setUp() {
        service = new EventService(events, memberships, media, inviteCodes, audit, rateLimiter, props(), storage);
        host = new User();
        host.setId(UUID.randomUUID());
        host.setClerkUserId("clerk_123");
        host.setDisplayName("Host Person");
        when(memberships.save(any(EventMembership.class))).thenAnswer(i -> i.getArgument(0));
        when(events.save(any(Event.class))).thenAnswer(i -> {
            Event e = i.getArgument(0);
            if (e.getId() == null) {
                e.setId(UUID.randomUUID());
            }
            return e;
        });
    }

    @Test
    void createEventAllocatesUniqueCodeAndHostMembership() {
        when(inviteCodes.generate()).thenReturn("ABCDE12345");
        when(events.existsByInviteCode("ABCDE12345")).thenReturn(false);

        CreateEventRequest request = new CreateEventRequest(
                "Sam & Tari Wedding", "Our big day", EventType.WEDDING, null, null, null);

        EventResponse response = service.createEvent(host, request);

        assertThat(response.inviteCode()).isEqualTo("ABCDE12345");
        assertThat(response.inviteUrl()).isEqualTo("http://localhost:3000/e/ABCDE12345");
        assertThat(response.name()).isEqualTo("Sam & Tari Wedding");
        verify(memberships).save(any(EventMembership.class));
        verify(audit).record(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void getEventRejectsNonOwner() {
        Event event = new Event();
        event.setId(UUID.randomUUID());
        event.setHostId(UUID.randomUUID()); // a different host
        when(events.findByIdAndDeletedAtIsNull(event.getId())).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> service.getEvent(host, event.getId()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void joinCreatesGuestMembership() {
        when(rateLimiter.tryAcquire(any(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(true);
        Event event = new Event();
        event.setId(UUID.randomUUID());
        event.setHostId(host.getId());
        event.setName("Reunion 2026");
        event.setInviteCode("CODE123456");
        event.setStatus(EventStatus.ACTIVE);
        when(events.findByInviteCodeAndDeletedAtIsNull("CODE123456")).thenReturn(Optional.of(event));

        JoinEventResponse response = service.join("CODE123456",
                new JoinEventRequest("Alice"), "203.0.113.9");

        assertThat(response.displayName()).isEqualTo("Alice");
        assertThat(response.eventName()).isEqualTo("Reunion 2026");
        assertThat(response.eventId()).isEqualTo(event.getId());
    }

    @Test
    void joinIsRateLimited() {
        when(rateLimiter.tryAcquire(any(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(false);
        assertThatThrownBy(() -> service.join("CODE123456",
                new JoinEventRequest("Alice"), "203.0.113.9"))
                .isInstanceOf(TooManyRequestsException.class);
    }
}
