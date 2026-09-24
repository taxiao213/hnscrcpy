package com.hnscrcpy.decode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicReference;

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

    @Test
    @DisplayName("onStreamError forwards the throwable to the error handler")
    void onStreamError_invokesErrorHandler() {
        var pump = new DecoderPump();
        var ref = new AtomicReference<Throwable>();
        pump.setErrorHandler(ref::set);
        var failure = new RuntimeException("boom");
        pump.onStreamError(failure);
        assertThat(ref.get()).isSameAs(failure);
    }

    @Test
    @DisplayName("onStreamEnded stops the pump and notifies with StreamEndedException")
    void onStreamEnded_stopsAndNotifies() {
        var pump = new DecoderPump();
        var ref = new AtomicReference<Throwable>();
        pump.setErrorHandler(ref::set);
        pump.onStreamEnded();
        assertThat(ref.get()).isInstanceOf(StreamEndedException.class);
    }

    @Test
    @DisplayName("null error handler falls back to no-op")
    void setErrorHandler_null_isNoOp() {
        var pump = new DecoderPump();
        pump.setErrorHandler(null);
        pump.onStreamError(new RuntimeException("ignored"));
        pump.onStreamEnded();
    }

    @Test
    @DisplayName("restart releases the old decoder and starts a fresh pump thread")
    void restart_replacesDecoderAndKeepsRunning() {
        var pump = new DecoderPump();
        pump.start();
        pump.restart();
        pump.close();
    }
}
