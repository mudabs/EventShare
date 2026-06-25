package com.eventshare.api.admin;

import com.eventshare.api.admin.dto.AdminEventView;
import com.eventshare.api.admin.dto.AdminUserView;
import com.eventshare.api.admin.dto.PlatformStats;
import com.eventshare.api.admin.dto.SetPlanRequest;
import com.eventshare.api.admin.dto.TransferRequest;
import com.eventshare.api.common.security.CurrentUser;
import com.eventshare.api.user.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Admin")
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @Operation(summary = "Search users")
    @GetMapping("/users")
    public List<AdminUserView> users(@CurrentUser User admin,
                                     @RequestParam(required = false) String query) {
        return adminService.searchUsers(admin, query);
    }

    @Operation(summary = "Get a user")
    @GetMapping("/users/{id}")
    public AdminUserView user(@CurrentUser User admin, @PathVariable UUID id) {
        return adminService.getUser(admin, id);
    }

    @Operation(summary = "Disable a user")
    @PostMapping("/users/{id}/disable")
    public ResponseEntity<Void> disable(@CurrentUser User admin, @PathVariable UUID id) {
        adminService.setDisabled(admin, id, true);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Enable a user")
    @PostMapping("/users/{id}/enable")
    public ResponseEntity<Void> enable(@CurrentUser User admin, @PathVariable UUID id) {
        adminService.setDisabled(admin, id, false);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Delete a user")
    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(@CurrentUser User admin, @PathVariable UUID id) {
        adminService.deleteUser(admin, id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Set a user's plan (no charge)")
    @PostMapping("/users/{id}/subscription")
    public ResponseEntity<Void> setPlan(@CurrentUser User admin, @PathVariable UUID id,
                                        @Valid @RequestBody SetPlanRequest request) {
        adminService.setUserPlan(admin, id, request.planCode());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Search events")
    @GetMapping("/events")
    public List<AdminEventView> events(@CurrentUser User admin,
                                       @RequestParam(required = false) String query) {
        return adminService.searchEvents(admin, query);
    }

    @Operation(summary = "Archive an event")
    @PostMapping("/events/{id}/archive")
    public ResponseEntity<Void> archive(@CurrentUser User admin, @PathVariable UUID id) {
        adminService.archiveEvent(admin, id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Remove an event")
    @DeleteMapping("/events/{id}")
    public ResponseEntity<Void> removeEvent(@CurrentUser User admin, @PathVariable UUID id) {
        adminService.removeEvent(admin, id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Transfer event ownership")
    @PostMapping("/events/{id}/transfer")
    public ResponseEntity<Void> transfer(@CurrentUser User admin, @PathVariable UUID id,
                                         @Valid @RequestBody TransferRequest request) {
        adminService.transferEvent(admin, id, request.newOwnerUserId());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Platform analytics")
    @GetMapping("/analytics")
    public PlatformStats analytics(@CurrentUser User admin) {
        return adminService.platformStats(admin);
    }
}
