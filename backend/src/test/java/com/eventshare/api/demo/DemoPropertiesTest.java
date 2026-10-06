package com.eventshare.api.demo;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/** Fast unit checks for demo defaults and the photo generator (no Docker needed). */
class DemoPropertiesTest {

    @Test
    void blankSettingsFallBackToSafeDefaults() {
        DemoProperties p = new DemoProperties(false, "", "", null, " ", false, null, null, null, null,
                "", null, null, 0, null, "", false, true, 8, 0, 0);

        assertThat(p.hostUsername()).isEqualTo("demo-host");
        assertThat(p.adminUsername()).isEqualTo("demo-admin");
        // Username-only login: no email is sent to Clerk...
        assertThat(p.hostEmail()).isNull();
        // ...and the local row gets an unroutable .invalid address for the plan whitelist.
        assertThat(p.hostLocalEmail()).isEqualTo("demo-host@demo.invalid");
        assertThat(p.adminLocalEmail()).isEqualTo("demo-admin@demo.invalid");
        assertThat(p.inviteCode()).isEqualTo("EVENTSHARE");
        assertThat(p.secondaryInviteCode()).isEqualTo("TEAMDAY26X");
        assertThat(p.promoCode()).isEqualTo("INTERVIEW30");
        assertThat(p.photoCount()).isEqualTo(18);
        assertThat(p.resetZone()).isEqualTo("America/Chicago");
        assertThat(p.maxGuestUploads()).isEqualTo(8);
        assertThat(p.maxGuestUploadBytes()).isEqualTo(25L * 1024 * 1024);
        assertThat(p.maxGuestUploadsPerIp()).isEqualTo(4);
        // Passwords are never defaulted: they must come from the environment.
        assertThat(p.hostPassword()).isNull();
    }

    @Test
    void inviteCodesAreUpperCasedAndPhotoCountIsCapped() {
        DemoProperties p = new DemoProperties(true, null, "host@mydomain.dev", "x", null, false, null, null, null, null,
                "mycode2345", "other23456", "promo1", 500, null, null, false, true, 8, 25L * 1024 * 1024, 4);
        assertThat(p.hostLocalEmail()).isEqualTo("host@mydomain.dev");
        assertThat(p.inviteCode()).isEqualTo("MYCODE2345");
        assertThat(p.promoCode()).isEqualTo("PROMO1");
        assertThat(p.photoCount()).isEqualTo(60);
    }

    @Test
    void generatedPhotosAreValidDeterministicJpegs() throws IOException {
        byte[] a = DemoImageGenerator.jpeg(1234L, 0);
        byte[] b = DemoImageGenerator.jpeg(1234L, 0);
        byte[] portrait = DemoImageGenerator.jpeg(99L, 2);

        assertThat(a).isEqualTo(b);
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(a));
        assertThat(img.getWidth()).isGreaterThan(img.getHeight());
        BufferedImage tall = ImageIO.read(new ByteArrayInputStream(portrait));
        assertThat(tall.getHeight()).isGreaterThan(tall.getWidth());
    }
}
