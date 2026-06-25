package com.eventshare.api.event;

import com.eventshare.api.common.security.CurrentUser;
import com.eventshare.api.event.dto.MemberView;
import com.eventshare.api.user.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Event members")
@RestController
@RequestMapping("/api/events")
public class MembershipController {

    private final MembershipService membershipService;

    public MembershipController(MembershipService membershipService) {
        this.membershipService = membershipService;
    }

    @Operation(summary = "List members of an event (owner)")
    @GetMapping("/{eventId}/members")
    public List<MemberView> members(@CurrentUser User host, @PathVariable UUID eventId) {
        return membershipService.listMembers(host, eventId);
    }

    @Operation(summary = "Remove a guest from an event (owner)")
    @DeleteMapping("/{eventId}/members/{membershipId}")
    public ResponseEntity<Void> remove(@CurrentUser User host,
                                       @PathVariable UUID eventId,
                                       @PathVariable UUID membershipId) {
        membershipService.removeMember(host, eventId, membershipId);
        return ResponseEntity.noContent().build();
    }
}
