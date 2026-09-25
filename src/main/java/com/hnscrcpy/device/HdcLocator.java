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
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * hdc 定位：只使用内置资源（四平台二进制随包分发），首次运行提取到
 * ~/.hnscrcpy/tools/&lt;platform&gt;/ 并释放 libusb 动态库。
 * 不扫描环境变量或 PATH——对外部环境零依赖，行为全平台一致。
 *
 * @Author: taxiao
 * WeChat：他晓
 * CSDN:http://blog.csdn.net/yin13753884368/article
 * Github:https://github.com/taxiao213
 */

public final class HdcLocator {

    private static final Logger log = LoggerFactory.getLogger(HdcLocator.class);
    private static volatile Path cached;

    private HdcLocator() {
    }

    public static Path locate() {
        Path hit = cached;
        if (hit != null) {
            return hit;
        }
        hit = extractBundled();
        if (hit == null) {
            throw new IllegalStateException("内置 hdc 提取失败，安装包可能已损坏，请重新下载安装。");
        }
        cached = hit;
        return hit;
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
