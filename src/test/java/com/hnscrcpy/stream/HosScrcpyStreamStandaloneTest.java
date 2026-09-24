package com.hnscrcpy.stream;

import com.hnscrcpy.device.StubJar;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class HosScrcpyStreamStandaloneTest {

    @Test
    @DisplayName("standalone stream discovers the jar from ~/.hnscrcpy/lib and owns the bridge")
    void standalone_lifecycle(@TempDir Path home) throws Exception {
        String origHome = System.getProperty("user.home");
        try {
            System.setProperty("user.home", home.toString());
            Path lib = home.resolve(".hnscrcpy/lib");
            Files.createDirectories(lib);
            Files.copy(StubJar.create(home.resolve("stub")), lib.resolve("hosScrcpy-stub.jar"));

            StubJar.clearLog();
            HosScrcpyStream stream = new HosScrcpyStream("SN99", VideoConfig.defaults());
            stream.start(new HosScrcpyStreamTest.NoopSink());
            assertThat(stream.isRunning()).isTrue();
            stream.requestIDRFrame();
            stream.stop();
            assertThat(stream.isRunning()).isFalse();
            assertThat(StubJar.log()).contains("start").contains("idr").contains("stop");
        } finally {
            System.setProperty("user.home", origHome);
        }
    }
}
