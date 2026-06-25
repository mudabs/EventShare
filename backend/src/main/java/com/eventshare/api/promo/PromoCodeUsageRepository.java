package com.eventshare.api.promo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PromoCodeUsageRepository extends JpaRepository<PromoCodeUsage, UUID> {
    boolean existsByPromoCodeIdAndUserId(UUID promoCodeId, UUID userId);
}
