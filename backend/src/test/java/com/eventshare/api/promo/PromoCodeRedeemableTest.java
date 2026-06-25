package com.eventshare.api.promo;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

class PromoCodeRedeemableTest {

    private PromoCode base() {
        PromoCode p = new PromoCode();
        p.setCode("TEST");
        p.setType(PromoCodeType.TEMP_PREMIUM);
        p.setActive(true);
        return p;
    }

    @Test
    void activeUnboundedCodeIsRedeemable() {
        assertThat(base().isRedeemable()).isTrue();
    }

    @Test
    void inactiveCodeIsNotRedeemable() {
        PromoCode p = base();
        p.setActive(false);
        assertThat(p.isRedeemable()).isFalse();
    }

    @Test
    void expiredCodeIsNotRedeemable() {
        PromoCode p = base();
        p.setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));
        assertThat(p.isRedeemable()).isFalse();
    }

    @Test
    void exhaustedCodeIsNotRedeemable() {
        PromoCode p = base();
        p.setMaxRedemptions(2);
        p.setRedemptionsUsed(2);
        assertThat(p.isRedeemable()).isFalse();
        p.setRedemptionsUsed(1);
        assertThat(p.isRedeemable()).isTrue();
    }
}
