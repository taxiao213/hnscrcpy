package com.hnscrcpy.device;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** 内置 jar 提取：只走内置资源，不依赖本机任何外部环境。 */
class HosScrcpyLocatorTest {

    @Test
    @DisplayName("extractBundled releases jar to redirected home and reuses it")
    void extractBundled_releasesJar(@TempDir Path home) throws java.io.IOException {
        String origHome = System.getProperty("user.home");
        try {
            System.setProperty("user.home", home.toString());
            Optional<Path> jar = HosScrcpyLocator.extractBundled();
            assertThat(jar).isPresent();
            assertThat(jar.get()).exists();
            assertThat(jar.get().getFileName().toString()).isEqualTo(HosScrcpyLocator.BUNDLED_JAR);
            assertThat(Files.size(jar.get())).isGreaterThan(1_000_000);
            // 二次调用走"已提取"分支
            assertThat(HosScrcpyLocator.extractBundled()).contains(jar.get());
            // locate 即内置提取（不再扫描外部环境）
            assertThat(HosScrcpyLocator.locate()).contains(jar.get());
        } finally {
            System.setProperty("user.home", origHome);
        }
    }

    @Test
    @DisplayName("locate ignores external jars planted in ~/.hnscrcpy/lib")
    void locate_ignoresExternalJars(@TempDir Path home) throws java.io.IOException {
        String origHome = System.getProperty("user.home");
        try {
            System.setProperty("user.home", home.toString());
            // 放一个"外部"诱饵 jar：旧行为会发现它，新行为必须无视
            Path lib = home.resolve(".hnscrcpy/lib");
            Files.createDirectories(lib);
            Path decoy = lib.resolve("hosScrcpy-9.9.9-decoy.jar");
            Files.write(decoy, new byte[]{1, 2, 3});
            Optional<Path> hit = HosScrcpyLocator.locate();
            assertThat(hit).isPresent();
            assertThat(hit.get().getFileName().toString()).isEqualTo(HosScrcpyLocator.BUNDLED_JAR);
            assertThat(hit.get()).isNotEqualTo(decoy);
        } finally {
            System.setProperty("user.home", origHome);
        }
    }

    @Test
    @DisplayName("guidance only mentions reinstall (no external setup steps)")
    void guidance_noExternalDeps() {
        assertThat(HosScrcpyLocator.guidance()).doesNotContain("DevEco", "环境变量", "插件");
    }
}
