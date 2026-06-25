package com.eventshare.api.promo;

import com.eventshare.api.audit.AuditService;
import com.eventshare.api.common.error.BadRequestException;
import com.eventshare.api.common.error.NotFoundException;
import com.eventshare.api.common.security.AdminGuard;
import com.eventshare.api.promo.dto.CreatePromoRequest;
import com.eventshare.api.promo.dto.PromoCodeResponse;
import com.eventshare.api.promo.dto.RedeemResponse;
import com.eventshare.api.subscription.SubscriptionService;
import com.eventshare.api.subscription.SubscriptionSource;
import com.eventshare.api.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PromoService {

    private static final int DEFAULT_TEMP_DAYS = 30;

    private final PromoCodeRepository promos;
    private final PromoCodeUsageRepository usages;
    private final SubscriptionService subscriptions;
    private final AuditService audit;
    private final AdminGuard adminGuard;

    public PromoService(PromoCodeRepository promos, PromoCodeUsageRepository usages,
                        SubscriptionService subscriptions, AuditService audit, AdminGuard adminGuard) {
        this.promos = promos;
        this.usages = usages;
        this.subscriptions = subscriptions;
        this.audit = audit;
        this.adminGuard = adminGuard;
    }

    @Transactional
    public RedeemResponse redeem(User user, String code) {
        PromoCode promo = promos.findByCodeIgnoreCaseAndDeletedAtIsNull(code)
                .orElseThrow(() -> new NotFoundException("Promo code not found"));
        if (!promo.isRedeemable()) {
            throw new BadRequestException("This code is no longer valid");
        }
        if (usages.existsByPromoCodeIdAndUserId(promo.getId(), user.getId())) {
            throw new BadRequestException("You have already used this code");
        }

        String message = switch (promo.getType()) {
            case LIFETIME_PREMIUM -> {
                String plan = orDefault(promo.getGrantsPlanCode(), "LIFETIME");
                subscriptions.activate(user.getId(), plan, SubscriptionSource.PROMO, null, null);
                yield "Lifetime premium unlocked (" + plan + ").";
            }
            case TEMP_PREMIUM, FREE_EVENT -> {
                String plan = orDefault(promo.getGrantsPlanCode(), "WEDDING_PRO");
                int days = promo.getDurationDays() != null ? promo.getDurationDays() : DEFAULT_TEMP_DAYS;
                Instant end = Instant.now().plus(days, ChronoUnit.DAYS);
                subscriptions.activate(user.getId(), plan, SubscriptionSource.PROMO, null, end);
                yield "Premium (" + plan + ") granted for " + days + " days.";
            }
            case PERCENT, FIXED -> "Discount accepted. It will be applied at checkout.";
        };

        PromoCodeUsage usage = new PromoCodeUsage();
        usage.setPromoCodeId(promo.getId());
        usage.setUserId(user.getId());
        usage.setRedeemedAt(Instant.now());
        usages.save(usage);

        promo.setRedemptionsUsed(promo.getRedemptionsUsed() + 1);
        promos.save(promo);

        audit.record(null, user.getId(), user.getDisplayName(), "PROMO_REDEEMED",
                "PROMO_CODE", promo.getId(), Map.of("code", promo.getCode()), null);
        return new RedeemResponse(promo.getType().name(), message);
    }

    @Transactional
    public PromoCodeResponse create(User admin, CreatePromoRequest request) {
        adminGuard.requireAdmin(admin);
        PromoCode promo = new PromoCode();
        promo.setCode(request.code().trim());
        promo.setType(request.type());
        promo.setValueNumeric(request.valueNumeric());
        promo.setGrantsPlanCode(request.grantsPlanCode());
        promo.setDurationDays(request.durationDays());
        promo.setMaxRedemptions(request.maxRedemptions());
        promo.setExpiresAt(request.expiresAt());
        promo.setActive(true);
        promo.setCreatedBy(admin.getId());
        return PromoCodeResponse.from(promos.save(promo));
    }

    @Transactional
    public void disable(User admin, UUID id) {
        adminGuard.requireAdmin(admin);
        promos.findById(id).ifPresent(promo -> {
            promo.setActive(false);
            promos.save(promo);
        });
    }

    @Transactional(readOnly = true)
    public List<PromoCodeResponse> list(User admin) {
        adminGuard.requireAdmin(admin);
        return promos.findAll().stream().map(PromoCodeResponse::from).toList();
    }

    private static String orDefault(String value, String fallback) {
        return value != null && !value.isBlank() ? value : fallback;
    }
}
