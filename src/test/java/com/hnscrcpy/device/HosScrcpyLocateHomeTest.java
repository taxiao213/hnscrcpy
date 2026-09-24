package com.hnscrcpy.device;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class HosScrcpyLocateHomeTest {

    @Test
    @DisplayName("locate finds jar under redirected ~/.hnscrcpy/lib")
    void locate_userLibHit(@TempDir Path home) throws Exception {
        String origHome = System.getProperty("user.home");
        try {
            System.setProperty("user.home", home.toString());
            Path lib = home.resolve(".hnscrcpy/lib");
            Files.createDirectories(lib);
            Files.createFile(lib.resolve("hosScrcpy-1.0.15-beta.jar"));
            assertThat(HosScrcpyLocator.locate()).isPresent();
        } finally {
            System.setProperty("user.home", origHome);
        }
    }

    @Test
    @DisplayName("guidance mentions all three remediation options")
    void guidance_coversOptions() {
        assertThat(HosScrcpyLocator.guidance())
                .contains("DevEco Testing")
                .contains(".hnscrcpy/lib")
                .contains("HOS_SCRCPY_JAR");
    }
}
