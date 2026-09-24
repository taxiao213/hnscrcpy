package com.hnscrcpy.device;

import com.hnscrcpy.util.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * 定位 hosScrcpy jar（DevEco Testing / Hypium 插件的一部分，无再分发授权，只做运行时发现）。
 * 查找顺序：环境变量 HOS_SCRCPY_JAR → ~/.hnscrcpy/lib/ → JetBrains 插件目录扫描。
 */
public final class HosScrcpyLocator {

    private static final Logger log = LoggerFactory.getLogger(HosScrcpyLocator.class);
    private static final String ENV_JAR = "HOS_SCRCPY_JAR";

    private HosScrcpyLocator() {
    }

    public static Optional<Path> locate() {
        Optional<Path> fromEnv = fromEnv();
        if (fromEnv.isPresent()) {
            return fromEnv;
        }
        Optional<Path> fromUserLib = scanDir(Platform.userHomeDir().resolve("lib"));
        if (fromUserLib.isPresent()) {
            return fromUserLib;
        }
        for (Path root : jetbrainsPluginRoots()) {
            Optional<Path> hit = scanRoot(root);
            if (hit.isPresent()) {
                return hit;
            }
        }
        return Optional.empty();
    }

    private static Optional<Path> fromEnv() {
        String env = System.getenv(ENV_JAR);
        if (env == null || env.isBlank()) {
            return Optional.empty();
        }
        Path p = Path.of(env).toAbsolutePath();
        if (Files.isRegularFile(p)) {
            log.info("hosScrcpy jar from env {}: {}", ENV_JAR, p);
            return Optional.of(p);
        }
        log.warn("env {} 指向的文件不存在: {}", ENV_JAR, p);
        return Optional.empty();
    }

    /** JetBrains 系 IDE 的插件根目录（含 DevEco Studio 的配置目录）。 */
    static List<Path> jetbrainsPluginRoots() {
        Path home = Path.of(System.getProperty("user.home"));
        List<Path> roots = new ArrayList<>();
        switch (Platform.os()) {
            case MACOS -> {
                roots.add(home.resolve("Library/Application Support/JetBrains"));
                roots.add(home.resolve("Library/Application Support/Huawei"));
            }
            case WINDOWS -> roots.add(Path.of(System.getenv("APPDATA"), "JetBrains"));
            default -> roots.add(home.resolve(".config/JetBrains"));
        }
        return roots;
    }

    /** 扫描 &lt;root&gt;/&lt;ide&gt;/plugins/DevecoTesting-Hypium/lib/hosScrcpy-*.jar。 */
    static Optional<Path> scanRoot(Path root) {
        if (!Files.isDirectory(root)) {
            return Optional.empty();
        }
        try (Stream<Path> ides = Files.list(root)) {
            return ides.filter(Files::isDirectory)
                    .map(ide -> ide.resolve("plugins/DevecoTesting-Hypium/lib"))
                    .flatMap(lib -> scanDir(lib).stream())
                    .max(Comparator.comparing(Path::getFileName));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /** 目录下最新的 hosScrcpy-*.jar。 */
    static Optional<Path> scanDir(Path dir) {
        if (!Files.isDirectory(dir)) {
            return Optional.empty();
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(p -> p.getFileName().toString().startsWith("hosScrcpy"))
                    .filter(p -> p.getFileName().toString().endsWith(".jar"))
                    .max(Comparator.comparing(Path::getFileName));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /** 未找到时的用户指引。 */
    public static String guidance() {
        return """
                未找到 hosScrcpy jar。该文件来自华为 DevEco Testing（Hypium）JetBrains 插件，\
                不能随本程序分发。请任选其一：
                1. 在 IDE 中安装 DevEco Testing 插件；
                2. 将 hosScrcpy-*.jar 复制到 ~/.hnscrcpy/lib/；
                3. 设置环境变量 HOS_SCRCPY_JAR 指向该 jar。""";
    }
}
