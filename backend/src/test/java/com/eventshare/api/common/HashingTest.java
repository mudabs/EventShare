package com.eventshare.api.common;

import com.eventshare.api.common.util.Hashing;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HashingTest {

    @Test
    void sha256MatchesKnownVector() {
        // SHA-256("abc")
        assertThat(Hashing.sha256Hex("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void differentInputsDiffer() {
        assertThat(Hashing.sha256Hex("a")).isNotEqualTo(Hashing.sha256Hex("b"));
    }
}
