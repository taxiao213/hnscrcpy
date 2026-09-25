package com.hnscrcpy.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @Author: taxiao
 * WeChat：他晓
 * CSDN:http://blog.csdn.net/yin13753884368/article
 * Github:https://github.com/taxiao213
 */

class ProcessRunnerTest {

    @Test
    @DisplayName("captures stdout from echo command")
    void run_echo_capturesStdout() {
        ProcessRunner.Result r = ProcessRunner.run(List.of("echo", "hello hnscrcpy"));
        assertThat(r.ok()).isTrue();
        assertThat(r.stdout()).contains("hello hnscrcpy");
        assertThat(r.stderr()).isBlank();
    }

    @Test
    @DisplayName("captures stderr from failing command")
    void run_failingCommand_capturesStderr() {
        ProcessRunner.Result r = ProcessRunner.run(List.of("ls", "/definitely-not-exist-12345"));
        assertThat(r.ok()).isFalse();
        assertThat(r.exitCode()).isNotZero();
        assertThat(r.stderr()).isNotBlank();
    }

    @Test
    @DisplayName("kills process on timeout and flags timedOut")
    void run_timeout_killsProcess() {
        ProcessRunner.Result r = ProcessRunner.run(List.of("sleep", "10"), 200, TimeUnit.MILLISECONDS);
        assertThat(r.timedOut()).isTrue();
        assertThat(r.ok()).isFalse();
    }

    @Test
    @DisplayName("returns non-zero result for missing binary instead of throwing")
    void run_missingBinary_returnsErrorResult() {
        ProcessRunner.Result r = ProcessRunner.run(List.of("no-such-binary-xyz"));
        assertThat(r.ok()).isFalse();
        assertThat(r.exitCode()).isNotZero();
    }
}
