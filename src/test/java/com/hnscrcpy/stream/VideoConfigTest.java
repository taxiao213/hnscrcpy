package com.hnscrcpy.stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VideoConfigTest {

    @Test
    @DisplayName("defaults match the documented baseline")
    void defaults_baseline() {
        VideoConfig cfg = VideoConfig.defaults();
        assertThat(cfg.bitRateMbps()).isEqualTo(30);
        assertThat(cfg.fps()).isEqualTo(60);
        assertThat(cfg.iFrameIntervalMs()).isEqualTo(2000);
    }

    @Test
    @DisplayName("with-methods return modified copies (immutable)")
    void withers_immutable() {
        VideoConfig cfg = VideoConfig.defaults();
        VideoConfig mod = cfg.withBitRate(10).withFps(90);
        assertThat(cfg.bitRateMbps()).isEqualTo(30);
        assertThat(cfg.fps()).isEqualTo(60);
        assertThat(mod.bitRateMbps()).isEqualTo(10);
        assertThat(mod.fps()).isEqualTo(90);
    }
}
