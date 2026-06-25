package com.eventshare.api.common.util;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Generates high-entropy, human-shareable invite codes. The alphabet excludes
 * visually ambiguous characters (0/O, 1/I/L) so codes read cleanly off a QR card.
 * A length of 10 over a 30-symbol alphabet yields about 49 bits of entropy, which
 * is not feasibly guessable while remaining short.
 */
@Component
public class InviteCodeGenerator {

    private static final char[] ALPHABET =
            "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int DEFAULT_LENGTH = 10;

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        return generate(DEFAULT_LENGTH);
    }

    public String generate(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }
}
