package com.hnscrcpy.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @Author: taxiao
 * WeChat：他晓
 * CSDN:http://blog.csdn.net/yin13753884368/article
 * Github:https://github.com/taxiao213
 */

class AppConfigTest {

    @Test
    @DisplayName("round-trips all fields through save/load")
    void saveLoad_roundTrips(@TempDir Path dir) {
        Path file = dir.resolve("config.properties");
        AppConfig cfg = AppConfig.load(file);
        cfg.setLastSerial("SN42");
        cfg.setBitRateMbps(20);
        cfg.setFps(90);
        cfg.setNoControl(true);
        cfg.save();

        AppConfig loaded = AppConfig.load(file);
        assertThat(loaded.lastSerial()).isEqualTo("SN42");
        assertThat(loaded.bitRateMbps()).isEqualTo(20);
        assertThat(loaded.fps()).isEqualTo(90);
        assertThat(loaded.noControl()).isTrue();
    }

    @Test
    @DisplayName("missing file yields defaults")
    void load_missing_defaults(@TempDir Path dir) {
        AppConfig cfg = AppConfig.load(dir.resolve("nope.properties"));
        assertThat(cfg.lastSerial()).isEmpty();
        assertThat(cfg.bitRateMbps()).isEqualTo(30);
        assertThat(cfg.fps()).isEqualTo(60);
        assertThat(cfg.noControl()).isFalse();
    }

    @Test
    @DisplayName("corrupt values fall back to defaults")
    void load_corrupt_defaults(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("config.properties");
        java.nio.file.Files.writeString(file, "bitRateMbps=abc\nfps=\n");
        AppConfig cfg = AppConfig.load(file);
        assertThat(cfg.bitRateMbps()).isEqualTo(30);
        assertThat(cfg.fps()).isEqualTo(60);
    }
}
