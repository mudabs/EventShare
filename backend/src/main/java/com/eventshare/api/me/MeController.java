package com.eventshare.api.me;

import com.eventshare.api.common.security.CurrentUser;
import com.eventshare.api.common.util.ClientIp;
import com.eventshare.api.event.MembershipService;
import com.eventshare.api.event.dto.JoinByCodeRequest;
import com.eventshare.api.event.dto.MyEventCard;
import com.eventshare.api.me.dto.ProfileResponse;
import com.eventshare.api.user.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "My Events")
@RestController
@RequestMapping("/api/me")
public class MeController {

    private final MembershipService membershipService;

    public MeController(MembershipService membershipService) {
        this.membershipService = membershipService;
    }

    @Operation(summary = "Join an event as a signed-in user (persistent membership)")
    @PostMapping("/events/join")
    public ResponseEntity<Void> join(@CurrentUser User user,
                                     @Valid @RequestBody JoinByCodeRequest request,
                                     HttpServletRequest httpRequest) {
        membershipService.joinAsUser(user, request.inviteCode(), ClientIp.resolve(httpRequest));
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "List events I own or have joined")
    @GetMapping("/events")
    public List<MyEventCard> myEvents(@CurrentUser User user) {
        return membershipService.myEvents(user);
    }

    @Operation(summary = "Leave an event I joined")
    @DeleteMapping("/events/{eventId}/membership")
    public ResponseEntity<Void> leave(@CurrentUser User user, @PathVariable UUID eventId) {
        membershipService.leave(user, eventId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Current user profile (role, etc.)")
    @GetMapping("/profile")
    public ProfileResponse profile(@CurrentUser User user) {
        return ProfileResponse.from(user);
    }
}
