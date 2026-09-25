package com.hnscrcpy.device;

import com.hnscrcpy.util.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

/**
 * 定位 hosScrcpy jar（DevEco Testing / Hypium 插件的一部分，纯 Java 全平台通用）。
 * 只使用内置 jar（自用/团队内部使用场景随包分发）：首次运行提取到 ~/.hnscrcpy/lib/，
 * 不扫描本机环境变量、PATH 或 IDE 插件目录——对外部环境零依赖，行为全平台一致。
 *
 * @Author: taxiao
 * WeChat：他晓
 * CSDN:http://blog.csdn.net/yin13753884368/article
 * Github:https://github.com/taxiao213
 */

public final class HosScrcpyLocator {

    private static final Logger log = LoggerFactory.getLogger(HosScrcpyLocator.class);
    /** 内置 jar 版本；升级时同步替换 src/main/resources/lib/ 下的文件。 */
    static final String BUNDLED_JAR = "hosScrcpy-1.0.15-beta.jar";

    private HosScrcpyLocator() {
    }

    public static Optional<Path> locate() {
        return extractBundled();
    }

    /** 把内置 jar 提取到 ~/.hnscrcpy/lib/；已提取则直接复用。 */
    static Optional<Path> extractBundled() {
        Path target = Platform.userHomeDir().resolve("lib").resolve(BUNDLED_JAR);
        if (Files.isRegularFile(target)) {
            log.info("hosScrcpy jar already extracted: {}", target);
            return Optional.of(target);
        }
        try (InputStream in = HosScrcpyLocator.class.getResourceAsStream("/lib/" + BUNDLED_JAR)) {
            if (in == null) {
                log.warn("bundled hosScrcpy jar missing from classpath: /lib/{}", BUNDLED_JAR);
                return Optional.empty();
            }
            Files.createDirectories(target.getParent());
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            log.info("hosScrcpy jar extracted to {}", target);
            return Optional.of(target);
        } catch (IOException e) {
            log.warn("extract bundled hosScrcpy jar failed", e);
            return Optional.empty();
        }
    }

    /** 内置 jar 提取失败时的用户指引（打包异常的提示，正常不会走到）。 */
    public static String guidance() {
        return "内置 hosScrcpy jar 提取失败，安装包可能已损坏，请重新下载安装。";
    }
}
