package com.hnscrcpy.decode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @Author: taxiao
 * WeChat：他晓
 * CSDN:http://blog.csdn.net/yin13753884368/article
 * Github:https://github.com/taxiao213
 */

class DecoderPumpMiscTest {

    @Test
    @DisplayName("error and ended callbacks are accepted without a running pump")
    void callbacks_noPump_noCrash() {
        DecoderPump pump = new DecoderPump();
        pump.onStreamError(new RuntimeException("boom"));
        pump.onStreamEnded();
        assertThat(pump.latestFrame()).isNull();
        assertThat(pump.decodedCount()).isZero();
        pump.close();
    }

    @Test
    @DisplayName("stop is idempotent and close before start is safe")
    void stopClose_idempotent() {
        DecoderPump pump = new DecoderPump();
        pump.stop();
        pump.stop();
        pump.close();
        assertThat(pump.latestFrame()).isNull();
    }

    @Test
    @DisplayName("latestFrame is null before any decode")
    void latestFrame_initiallyNull() {
        assertThat(new DecoderPump().latestFrame()).isNull();
    }
}
