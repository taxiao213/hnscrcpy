package com.hnscrcpy.device;

import com.hnscrcpy.util.Platform;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @Author: taxiao
 * WeChat：他晓
 * CSDN:http://blog.csdn.net/yin13753884368/article
 * Github:https://github.com/taxiao213
 */

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
    @DisplayName("locate returns bundled hdc (no env/PATH lookup)")
    void locate_usesBundled(@TempDir Path home) {
        String origHome = System.getProperty("user.home");
        try {
            System.setProperty("user.home", home.toString());
            resetCache();
            Path hdc = HdcLocator.locate();
            assertThat(hdc.toString()).contains(".hnscrcpy" + java.io.File.separator + "tools");
        } finally {
            System.setProperty("user.home", origHome);
            resetCache();
        }
    }

    private static void resetCache() {
        try {
            var f = HdcLocator.class.getDeclaredField("cached");
            f.setAccessible(true);
            f.set(null, null);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    @DisplayName("verify succeeds against the bundled hdc")
    void verify_realHdc_whenAvailable() {
        Path hdc = HdcLocator.locate();
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.isExecutable(hdc), "hdc 不可用，跳过");
        assertThat(HdcLocator.verify(hdc)).isTrue();
    }
}
