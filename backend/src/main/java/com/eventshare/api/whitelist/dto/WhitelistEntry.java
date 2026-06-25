package com.eventshare.api.whitelist.dto;

import com.eventshare.api.whitelist.WhitelistedUser;

import java.time.Instant;
import java.util.UUID;

public record WhitelistEntry(UUID id, String email, String note, boolean active, Instant createdAt) {
    public static WhitelistEntry from(WhitelistedUser w) {
        return new WhitelistEntry(w.getId(), w.getEmail(), w.getNote(), w.isActive(), w.getCreatedAt());
    }
}
