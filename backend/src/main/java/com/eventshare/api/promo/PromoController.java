package com.eventshare.api.promo;

import com.eventshare.api.common.security.CurrentUser;
import com.eventshare.api.promo.dto.CreatePromoRequest;
import com.eventshare.api.promo.dto.PromoCodeResponse;
import com.eventshare.api.promo.dto.RedeemRequest;
import com.eventshare.api.promo.dto.RedeemResponse;
import com.eventshare.api.user.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Promo codes")
@RestController
public class PromoController {

    private final PromoService promoService;

    public PromoController(PromoService promoService) {
        this.promoService = promoService;
    }

    @Operation(summary = "Redeem a promo code")
    @PostMapping("/api/me/promo/redeem")
    public RedeemResponse redeem(@CurrentUser User user, @Valid @RequestBody RedeemRequest request) {
        return promoService.redeem(user, request.code());
    }

    @Operation(summary = "List promo codes (admin)")
    @GetMapping("/api/admin/promo-codes")
    public List<PromoCodeResponse> list(@CurrentUser User admin) {
        return promoService.list(admin);
    }

    @Operation(summary = "Create a promo code (admin)")
    @PostMapping("/api/admin/promo-codes")
    public PromoCodeResponse create(@CurrentUser User admin, @Valid @RequestBody CreatePromoRequest request) {
        return promoService.create(admin, request);
    }

    @Operation(summary = "Disable a promo code (admin)")
    @PostMapping("/api/admin/promo-codes/{id}/disable")
    public ResponseEntity<Void> disable(@CurrentUser User admin, @PathVariable UUID id) {
        promoService.disable(admin, id);
        return ResponseEntity.noContent().build();
    }
}
