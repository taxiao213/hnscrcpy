package com.hnscrcpy.device;

import com.hnscrcpy.util.Platform;
import com.hnscrcpy.util.ProcessRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * hdc 定位：环境变量 HDC → PATH → 内置资源提取到 ~/.hnscrcpy/tools/。
 * 解析结果缓存；提取时同时释放 libusb 动态库。
 */
public final class HdcLocator {

    private static final Logger log = LoggerFactory.getLogger(HdcLocator.class);
    private static final String ENV_HDC = "HDC";
    private static volatile Path cached;

    private HdcLocator() {
    }

    public static Path locate() {
        Path hit = cached;
        if (hit != null) {
            return hit;
        }
        hit = findUncached();
        cached = hit;
        return hit;
    }

    private static Path findUncached() {
        Optional<Path> fromEnv = fromEnv();
        if (fromEnv.isPresent()) {
            return fromEnv.get();
        }
        Optional<Path> fromPath = fromPath();
        if (fromPath.isPresent()) {
            return fromPath.get();
        }
        Path extracted = extractBundled();
        if (extracted != null) {
            return extracted;
        }
        throw new IllegalStateException(
                "未找到 hdc。请安装 DevEco Studio / command-line-tools，或设置环境变量 HDC 指向 hdc 可执行文件。");
    }

    private static Optional<Path> fromEnv() {
        String env = System.getenv(ENV_HDC);
        if (env == null || env.isBlank()) {
            return Optional.empty();
        }
        Path p = Path.of(env).toAbsolutePath();
        if (Files.isExecutable(p)) {
            log.info("hdc from env {}: {}", ENV_HDC, p);
            return Optional.of(p);
        }
        log.warn("env {} 指向的文件不可执行: {}", ENV_HDC, p);
        return Optional.empty();
    }

    private static Optional<Path> fromPath() {
        String exe = Platform.hdcExecutableName();
        for (String dir : System.getenv("PATH").split(java.io.File.pathSeparator)) {
            if (dir.isBlank()) {
                continue;
            }
            Path p = Path.of(dir, exe);
            if (Files.isExecutable(p)) {
                log.info("hdc from PATH: {}", p);
                return Optional.of(p.toAbsolutePath());
            }
        }
        return Optional.empty();
    }

    /** 从 classpath 资源提取 hdc + libusb 到 ~/.hnscrcpy/tools/&lt;platform&gt;/。 */
    static Path extractBundled() {
        String platformDir = Platform.platformDir();
        Path targetDir = Platform.userHomeDir().resolve("tools").resolve(platformDir);
        Path hdc = targetDir.resolve(Platform.hdcExecutableName());
        if (Files.isExecutable(hdc)) {
            log.info("hdc already extracted: {}", hdc);
            return hdc;
        }
        try {
            Files.createDirectories(targetDir);
            copyResource("/hdc/" + platformDir + "/" + Platform.hdcExecutableName(), hdc);
            String libusb = Platform.libusbLibraryName();
            copyResource("/hdc/" + platformDir + "/" + libusb, targetDir.resolve(libusb));
            if (!Platform.isWindows()) {
                ProcessRunner.run(List.of("chmod", "+x", hdc.toString()), 10, TimeUnit.SECONDS);
            }
            log.info("hdc extracted to {}", hdc);
            return hdc;
        } catch (IOException e) {
            log.warn("extract bundled hdc failed", e);
            return null;
        }
    }

    private static void copyResource(String resource, Path target) throws IOException {
        try (InputStream in = HdcLocator.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("resource not found: " + resource);
            }
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** macOS 上 hdc 依赖同目录 libusb_shared.dylib；验证可执行性。 */
    public static boolean verify(Path hdc) {
        ProcessRunner.Result r = ProcessRunner.run(List.of(hdc.toString(), "-v"), 15, TimeUnit.SECONDS);
        return r.ok() && !r.output().isBlank();
    }
}
