package com.hnscrcpy.decode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;

import static org.assertj.core.api.Assertions.assertThat;

class DecoderPumpTest {

    @Test
    @DisplayName("offerDropOldest drops head when queue is at capacity")
    void offerDropOldest_fullQueue_dropsOldest() {
        var q = new ArrayDeque<Integer>(3);
        DecoderPump.offerDropOldest(q, 1, 3);
        DecoderPump.offerDropOldest(q, 2, 3);
        DecoderPump.offerDropOldest(q, 3, 3);
        boolean ok = DecoderPump.offerDropOldest(q, 4, 3);
        assertThat(ok).isTrue();
        assertThat(q).containsExactly(2, 3, 4);
    }

    @Test
    @DisplayName("offerDropOldest keeps order below capacity")
    void offerDropOldest_belowCapacity_keepsAll() {
        var q = new ArrayDeque<Integer>(3);
        DecoderPump.offerDropOldest(q, 1, 3);
        DecoderPump.offerDropOldest(q, 2, 3);
        assertThat(q).containsExactly(1, 2);
    }
}
