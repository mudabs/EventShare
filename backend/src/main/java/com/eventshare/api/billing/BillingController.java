package com.eventshare.api.billing;

import com.eventshare.api.common.security.CurrentUser;
import com.eventshare.api.subscription.dto.CheckoutRequest;
import com.eventshare.api.subscription.dto.CheckoutResponse;
import com.eventshare.api.user.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Billing")
@RestController
@RequestMapping("/api/billing")
public class BillingController {

    private final BillingService billingService;

    public BillingController(BillingService billingService) {
        this.billingService = billingService;
    }

    @Operation(summary = "Create a Stripe Checkout session for a plan")
    @PostMapping("/checkout-session")
    public CheckoutResponse checkout(@CurrentUser User user, @Valid @RequestBody CheckoutRequest request) {
        return new CheckoutResponse(billingService.createCheckout(user, request.planCode()));
    }

    @Operation(summary = "Create a Stripe billing-portal session")
    @PostMapping("/portal-session")
    public CheckoutResponse portal(@CurrentUser User user) {
        return new CheckoutResponse(billingService.createPortal(user));
    }

    @Operation(summary = "Stripe webhook (verified by signature, not Clerk)")
    @PostMapping("/webhook")
    public ResponseEntity<String> webhook(@RequestBody String payload,
                                          @RequestHeader("Stripe-Signature") String signature) {
        billingService.handleWebhook(payload, signature);
        return ResponseEntity.ok("ok");
    }
}
