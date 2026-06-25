package com.eventshare.api.common;

import com.eventshare.api.common.util.InviteCodeGenerator;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class InviteCodeGeneratorTest {

    private final InviteCodeGenerator generator = new InviteCodeGenerator();

    @Test
    void generatesRequestedLength() {
        assertThat(generator.generate()).hasSize(10);
        assertThat(generator.generate(6)).hasSize(6);
    }

    @Test
    void excludesVisuallyAmbiguousCharacters() {
        for (int i = 0; i < 500; i++) {
            assertThat(generator.generate()).doesNotContainAnyWhitespaces()
                    .matches("[ABCDEFGHJKMNPQRSTUVWXYZ23456789]+");
        }
    }

    @Test
    void isReasonablyCollisionFree() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            seen.add(generator.generate());
        }
        // 49 bits of entropy: 10k draws should essentially never collide.
        assertThat(seen).hasSize(10_000);
    }
}
