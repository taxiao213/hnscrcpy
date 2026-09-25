package com.hnscrcpy.stream;

import com.hnscrcpy.device.StubJar;
import org.junit.jupiter.api.BeforeAll;
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

class HosScrcpyStreamTest {

    @TempDir
    static Path dir;
    private static Path stubJar;

    @BeforeAll
    static void createStub() throws Exception {
        stubJar = StubJar.create(dir);
    }

    static final class NoopSink implements FrameSink {
        @Override
        public void onStreamReady() {
        }

        @Override
        public void onH264Frame(byte[] data) {
        }

        @Override
        public void onStreamError(Throwable t) {
        }

        @Override
        public void onStreamEnded() {
        }
    }

    @Test
    @DisplayName("start/stop lifecycle drives the bridge and running flag")
    void lifecycle_startStop() {
        StubJar.clearLog();
        HosScrcpyBridge bridge = HosScrcpyBridge.open(stubJar, "SN99", "/opt/hdc", VideoConfig.defaults());
        HosScrcpyStream stream = new HosScrcpyStream(bridge, "SN99");

        assertThat(stream.isRunning()).isFalse();
        stream.start(new NoopSink());
        assertThat(stream.isRunning()).isTrue();
        stream.stop();
        assertThat(stream.isRunning()).isFalse();
        String log = StubJar.log();
        assertThat(log).contains("start").contains("stop");
        bridge.close();
    }

    @Test
    @DisplayName("requestIDRFrame is forwarded while running")
    void requestIdr_whileRunning() {
        StubJar.clearLog();
        HosScrcpyBridge bridge = HosScrcpyBridge.open(stubJar, "SN99", "/opt/hdc", VideoConfig.defaults());
        HosScrcpyStream stream = new HosScrcpyStream(bridge, "SN99");
        stream.start(new NoopSink());
        stream.requestIDRFrame();
        assertThat(StubJar.log()).contains("idr");
        stream.stop();
        bridge.close();
    }
}
