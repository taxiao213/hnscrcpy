package com.hnscrcpy.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @Author: taxiao
 * WeChat：他晓
 * CSDN:http://blog.csdn.net/yin13753884368/article
 * Github:https://github.com/taxiao213
 */

class CliOptionsTest {

    @Test
    @DisplayName("parses all options together")
    void parse_allOptions_extractsValues() {
        CliOptions o = CliOptions.parse(new String[]{
                "-s", "SN123", "-m", "1600", "-b", "20", "--fps", "90", "--no-control"});
        assertThat(o.serial()).isEqualTo("SN123");
        assertThat(o.maxSize()).isEqualTo(1600);
        assertThat(o.bitRate()).isEqualTo(20);
        assertThat(o.fps()).isEqualTo(90);
        assertThat(o.noControl()).isTrue();
    }

    @Test
    @DisplayName("empty args yield defaults")
    void parse_empty_defaults() {
        CliOptions o = CliOptions.parse(new String[0]);
        assertThat(o.serial()).isNull();
        assertThat(o.noControl()).isFalse();
        assertThat(o.help()).isFalse();
    }

    @Test
    @DisplayName("missing value throws with option name")
    void parse_missingValue_throws() {
        assertThatThrownBy(() -> CliOptions.parse(new String[]{"-s"}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("-s");
    }

    @Test
    @DisplayName("non-numeric value throws")
    void parse_badNumber_throws() {
        assertThatThrownBy(() -> CliOptions.parse(new String[]{"--fps", "abc"}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("--fps");
    }

    @Test
    @DisplayName("unknown option throws")
    void parse_unknown_throws() {
        assertThatThrownBy(() -> CliOptions.parse(new String[]{"--bogus"}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("--bogus");
    }
}
