package com.hnscrcpy.util;

import java.util.Locale;

/**
 * 操作系统与架构检测。平台目录名与 resources/hdc/ 下的目录布局一致。
 *
 * @Author: taxiao
 * WeChat：他晓
 * CSDN:http://blog.csdn.net/yin13753884368/article
 * Github:https://github.com/taxiao213
 */

public final class Platform {

    public enum Os { WINDOWS, MACOS, LINUX, UNKNOWN }

    public enum Arch { AARCH64, X86_64, UNKNOWN }

    private Platform() {
    }

    public static Os os() {
        String name = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (name.contains("win")) {
            return Os.WINDOWS;
        }
        if (name.contains("mac") || name.contains("darwin")) {
            return Os.MACOS;
        }
        if (name.contains("nux") || name.contains("nix")) {
            return Os.LINUX;
        }
        return Os.UNKNOWN;
    }

    public static Arch arch() {
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if (arch.equals("aarch64") || arch.equals("arm64")) {
            return Arch.AARCH64;
        }
        if (arch.equals("x86_64") || arch.equals("amd64")) {
            return Arch.X86_64;
        }
        return Arch.UNKNOWN;
    }

    public static boolean isWindows() {
        return os() == Os.WINDOWS;
    }

    public static boolean isMac() {
        return os() == Os.MACOS;
    }

    /**
     * 平台目录名：mac-arm64 / mac-x64 / windows-x64 / linux-64。
     * 未知组合抛异常（不支持的平台不应走到这里）。
     */
    public static String platformDir() {
        return switch (os()) {
            case MACOS -> arch() == Arch.AARCH64 ? "mac-arm64" : "mac-x64";
            case WINDOWS -> "windows-x64";
            case LINUX -> "linux-64";
            default -> throw new IllegalStateException("unsupported OS: " + System.getProperty("os.name"));
        };
    }

    /** hdc 可执行文件名。 */
    public static String hdcExecutableName() {
        return isWindows() ? "hdc.exe" : "hdc";
    }

    /** libusb 动态库文件名（与 resources/hdc/&lt;platform&gt;/ 内一致）。 */
    public static String libusbLibraryName() {
        return switch (os()) {
            case MACOS -> "libusb_shared.dylib";
            case WINDOWS -> "libusb_shared.dll";
            default -> "libusb_shared.so";
        };
    }

    /** hnscrcpy 用户目录（配置、日志、提取的工具）。 */
    public static java.nio.file.Path userHomeDir() {
        return java.nio.file.Path.of(System.getProperty("user.home"), ".hnscrcpy");
    }
}
