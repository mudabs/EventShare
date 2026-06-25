package com.eventshare.api.whitelist;

import com.eventshare.api.audit.AuditService;
import com.eventshare.api.common.error.ConflictException;
import com.eventshare.api.common.security.AdminGuard;
import com.eventshare.api.user.User;
import com.eventshare.api.whitelist.dto.WhitelistEntry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class WhitelistService {

    private final WhitelistedUserRepository whitelist;
    private final AdminGuard adminGuard;
    private final AuditService audit;

    public WhitelistService(WhitelistedUserRepository whitelist, AdminGuard adminGuard, AuditService audit) {
        this.whitelist = whitelist;
        this.adminGuard = adminGuard;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<WhitelistEntry> list(User admin) {
        adminGuard.requireAdmin(admin);
        return whitelist.findAll().stream().map(WhitelistEntry::from).toList();
    }

    @Transactional
    public WhitelistEntry add(User admin, String email, String note) {
        adminGuard.requireAdmin(admin);
        String normalized = email.trim();
        if (whitelist.existsByEmailIgnoreCaseAndActiveTrueAndDeletedAtIsNull(normalized)) {
            throw new ConflictException("This email is already whitelisted");
        }
        WhitelistedUser entry = new WhitelistedUser();
        entry.setEmail(normalized);
        entry.setNote(note);
        entry.setGrantedBy(admin.getId());
        entry.setActive(true);
        WhitelistedUser saved = whitelist.save(entry);
        audit.record(null, admin.getId(), admin.getDisplayName(), "WHITELIST_ADDED",
                "WHITELIST", saved.getId(), null, null);
        return WhitelistEntry.from(saved);
    }

    @Transactional
    public void remove(User admin, UUID id) {
        adminGuard.requireAdmin(admin);
        whitelist.findById(id).ifPresent(entry -> {
            whitelist.delete(entry);
            audit.record(null, admin.getId(), admin.getDisplayName(), "WHITELIST_REMOVED",
                    "WHITELIST", id, null, null);
        });
    }
}
