package com.eventshare.api.whitelist;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WhitelistedUserRepository extends JpaRepository<WhitelistedUser, UUID> {
    Optional<WhitelistedUser> findByEmailIgnoreCaseAndActiveTrueAndDeletedAtIsNull(String email);
    boolean existsByEmailIgnoreCaseAndActiveTrueAndDeletedAtIsNull(String email);
}
