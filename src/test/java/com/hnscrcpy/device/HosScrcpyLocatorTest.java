package com.hnscrcpy.device;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class HosScrcpyLocatorTest {

    @Test
    @DisplayName("scanDir finds the newest hosScrcpy jar")
    void scanDir_multipleJars_picksLatestName(@TempDir Path dir) throws IOException {
        Files.createFile(dir.resolve("hosScrcpy-1.0.14-beta.jar"));
        Files.createFile(dir.resolve("hosScrcpy-1.0.15-beta.jar"));
        Files.createFile(dir.resolve("other-1.0.jar"));
        Optional<Path> hit = HosScrcpyLocator.scanDir(dir);
        assertThat(hit).isPresent();
        assertThat(hit.get().getFileName().toString()).isEqualTo("hosScrcpy-1.0.15-beta.jar");
    }

    @Test
    @DisplayName("scanDir returns empty for missing directory")
    void scanDir_missingDir_returnsEmpty(@TempDir Path dir) {
        assertThat(HosScrcpyLocator.scanDir(dir.resolve("nope"))).isEmpty();
    }

    @Test
    @DisplayName("scanRoot walks IDE plugins layout to the Hypium lib dir")
    void scanRoot_jetbrainsLayout_findsJar(@TempDir Path root) throws IOException {
        Path lib = root.resolve("IntelliJIdea2024.1/plugins/DevecoTesting-Hypium/lib");
        Files.createDirectories(lib);
        Files.createFile(lib.resolve("hosScrcpy-1.0.15-beta.jar"));
        Optional<Path> hit = HosScrcpyLocator.scanRoot(root);
        assertThat(hit).isPresent();
    }

    @Test
    @DisplayName("scanRoot returns empty when no IDE dirs exist")
    void scanRoot_emptyRoot_returnsEmpty(@TempDir Path root) {
        assertThat(HosScrcpyLocator.scanRoot(root)).isEmpty();
    }
}
