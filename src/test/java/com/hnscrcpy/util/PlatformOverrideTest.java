package com.hnscrcpy.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @Author: taxiao
 * WeChat：他晓
 * CSDN:http://blog.csdn.net/yin13753884368/article
 * Github:https://github.com/taxiao213
 */

class PlatformOverrideTest {

    private static void withProps(String osName, String osArch, Runnable body) {
        String origName = System.getProperty("os.name");
        String origArch = System.getProperty("os.arch");
        try {
            System.setProperty("os.name", osName);
            System.setProperty("os.arch", osArch);
            body.run();
        } finally {
            System.setProperty("os.name", origName);
            System.setProperty("os.arch", origArch);
        }
    }

    @Test
    @DisplayName("windows amd64 resolves to windows-x64 with .exe names")
    void windows_amd64() {
        withProps("Windows 10", "amd64", () -> {
            assertThat(Platform.os()).isEqualTo(Platform.Os.WINDOWS);
            assertThat(Platform.platformDir()).isEqualTo("windows-x64");
            assertThat(Platform.hdcExecutableName()).isEqualTo("hdc.exe");
            assertThat(Platform.libusbLibraryName()).isEqualTo("libusb_shared.dll");
        });
    }

    @Test
    @DisplayName("linux aarch64 resolves to linux-64")
    void linux_aarch64() {
        withProps("Linux", "aarch64", () -> {
            assertThat(Platform.os()).isEqualTo(Platform.Os.LINUX);
            assertThat(Platform.platformDir()).isEqualTo("linux-64");
            assertThat(Platform.libusbLibraryName()).isEqualTo("libusb_shared.so");
        });
    }

    @Test
    @DisplayName("mac x86_64 resolves to mac-x64")
    void mac_x86() {
        withProps("Mac OS X", "x86_64", () -> {
            assertThat(Platform.os()).isEqualTo(Platform.Os.MACOS);
            assertThat(Platform.platformDir()).isEqualTo("mac-x64");
            assertThat(Platform.hdcExecutableName()).isEqualTo("hdc");
        });
    }
}
