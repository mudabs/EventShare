package com.eventshare.api.whitelist;

import com.eventshare.api.common.security.CurrentUser;
import com.eventshare.api.user.User;
import com.eventshare.api.whitelist.dto.AddWhitelistRequest;
import com.eventshare.api.whitelist.dto.WhitelistEntry;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Whitelist")
@RestController
@RequestMapping("/api/admin/whitelist")
public class WhitelistController {

    private final WhitelistService whitelistService;

    public WhitelistController(WhitelistService whitelistService) {
        this.whitelistService = whitelistService;
    }

    @Operation(summary = "List whitelisted emails (admin)")
    @GetMapping
    public List<WhitelistEntry> list(@CurrentUser User admin) {
        return whitelistService.list(admin);
    }

    @Operation(summary = "Whitelist an email for unlimited access (admin)")
    @PostMapping
    public WhitelistEntry add(@CurrentUser User admin, @Valid @RequestBody AddWhitelistRequest request) {
        return whitelistService.add(admin, request.email(), request.note());
    }

    @Operation(summary = "Remove a whitelist entry (admin)")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remove(@CurrentUser User admin, @PathVariable UUID id) {
        whitelistService.remove(admin, id);
        return ResponseEntity.noContent().build();
    }
}
