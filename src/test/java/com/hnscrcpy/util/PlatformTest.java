package com.hnscrcpy.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformTest {

    @Test
    @DisplayName("current platform resolves to a concrete dir and names")
    void currentPlatform_concreteValues() {
        assertThat(Platform.os()).isNotEqualTo(Platform.Os.UNKNOWN);
        assertThat(Platform.arch()).isNotEqualTo(Platform.Arch.UNKNOWN);
        assertThat(Platform.platformDir()).isIn("mac-arm64", "mac-x64", "windows-x64", "linux-x64");
        assertThat(Platform.hdcExecutableName()).isIn("hdc", "hdc.exe");
        assertThat(Platform.libusbLibraryName()).endsWith(
                Platform.isWindows() ? ".dll" : Platform.isMac() ? ".dylib" : ".so");
        assertThat(Platform.userHomeDir().toString()).endsWith(".hnscrcpy");
    }
}
