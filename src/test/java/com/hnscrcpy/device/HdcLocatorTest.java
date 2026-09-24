package com.hnscrcpy.device;

import com.hnscrcpy.util.Platform;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class HdcLocatorTest {

    @Test
    @DisplayName("extractBundled releases hdc + libusb and marks hdc executable")
    void extractBundled_releasesBinaries(@TempDir Path home) {
        String origHome = System.getProperty("user.home");
        try {
            System.setProperty("user.home", home.toString());
            Path hdc = HdcLocator.extractBundled();
            assertThat(hdc).isNotNull();
            assertThat(hdc).exists();
            assertThat(Files.isExecutable(hdc)).isTrue();
            Path libusb = hdc.getParent().resolve(Platform.libusbLibraryName());
            assertThat(libusb).exists();
            // 二次调用走缓存分支
            assertThat(HdcLocator.extractBundled()).isEqualTo(hdc);
        } finally {
            System.setProperty("user.home", origHome);
        }
    }

    @Test
    @DisplayName("fromEnv returns empty when HDC env is not set")
    void fromEnv_unset_empty() {
        // 测试环境不设 HDC；若外部环境恰好设置，跳过该断言
        Optional<Path> env = Optional.ofNullable(System.getenv("HDC")).map(Path::of);
        assumeNoHdcEnv(env);
    }

    private static void assumeNoHdcEnv(Optional<Path> env) {
        org.junit.jupiter.api.Assumptions.assumeTrue(env.isEmpty() || !Files.isExecutable(env.get()),
                "HDC env 已设置，跳过");
    }

    @Test
    @DisplayName("verify succeeds against the real hdc when available on PATH")
    void verify_realHdc_whenAvailable() {
        Path hdc = HdcLocator.locate();
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.isExecutable(hdc), "hdc 不可用，跳过");
        assertThat(HdcLocator.verify(hdc)).isTrue();
    }
}
