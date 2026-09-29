package com.hnscrcpy.diag;

import com.hnscrcpy.App;
import com.hnscrcpy.diag.DiagnosticEvent.Type;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 诊断导出：按设备生成结构化异常时间线报告，连同原始日志、环境信息打包 zip。
 * 纯静态工具；报告渲染独立成方法便于测试。
 *
 * @Author: taxiao
 * WeChat：他晓
 * CSDN:http://blog.csdn.net/yin13753884368/article
 * Github:https://github.com/taxiao213
 */

public final class DiagnosticExportService {

    private static final DateTimeFormatter TS =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private DiagnosticExportService() {
    }

    /**
     * 导出所选设备的诊断 zip 到用户指定文件，返回实际路径。
     *
     * @param serials 要导出的设备序列号集合（空集合 = 无诊断数据，仅导日志与环境信息）
     */
    public static Path export(Set<String> serials, Path target) throws IOException {
        Path logsDir = Paths.get(System.getProperty("user.home"), ".hnscrcpy", "logs");
        try (ZipOutputStream zip = new ZipOutputStream(
                Files.newOutputStream(target), StandardCharsets.UTF_8)) {
            for (String serial : serials) {
                DeviceDiagnostics d = DiagnosticRegistry.get(serial, null);
                if (d != null) {
                    d.markRead();
                    put(zip, "diagnostic-" + safe(serial) + ".md", renderReport(d));
                }
            }
            if (Files.isDirectory(logsDir)) {
                try (Stream<Path> logs = Files.list(logsDir)) {
                    logs.filter(p -> p.getFileName().toString().endsWith(".log"))
                            .sorted()
                            .forEach(p -> copyInto(zip, logsDir.relativize(p), p));
                }
            }
            put(zip, "environment.txt", renderEnvironment(serials));
        }
        return target;
    }

    /** 建议的导出文件名（FileChooser 初始名）。 */
    public static String suggestedFileName() {
        return "hnscrcpy-diag-" + TS.format(LocalDateTime.now()) + ".zip";
    }

    /** 结构化异常时间线报告（Markdown）。包可见便于测试。 */
    static String renderReport(DeviceDiagnostics d) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 诊断报告 — ").append(d.deviceName()).append(" (").append(d.serial()).append(")\n\n");
        sb.append("- 导出时间: ").append(LocalDateTime.now()).append('\n');
        sb.append("- 在线状态: ").append(d.isOnline() ? "在线" : "离线").append('\n');
        sb.append("- 事件总数: ").append(d.events().size())
                .append("（其中异常 ").append(countErrors(d)).append("）\n\n");
        sb.append("## 异常分类汇总\n\n");
        for (Type t : Type.values()) {
            long n = d.errorCount(t);
            if (n > 0) {
                sb.append("- ").append(t.category().label()).append(" / ").append(t)
                        .append(": ").append(n).append(" 次\n");
            }
        }
        sb.append("\n## 事件时间线（时间升序，最多保留最近 ").append(DeviceDiagnostics.CAPACITY)
                .append(" 条）\n\n");
        sb.append("| 时间 | 类别 | 事件 | 详情 |\n|------|------|------|------|\n");
        for (DiagnosticEvent e : d.events()) {
            sb.append("| ").append(e.formattedTime())
                    .append(" | ").append(e.type().category().label())
                    .append(" | ").append(e.type())
                    .append(" | ").append(e.detail() == null ? "" : e.detail().replace("|", "\\|"))
                    .append(" |\n");
        }
        return sb.toString();
    }

    private static long countErrors(DeviceDiagnostics d) {
        return d.events().stream().filter(DiagnosticEvent::isError).count();
    }

    static String renderEnvironment(Set<String> serials) {
        StringBuilder sb = new StringBuilder();
        sb.append("hnscrcpy ").append(App.VERSION).append('\n');
        sb.append("OS: ").append(System.getProperty("os.name")).append(' ')
                .append(System.getProperty("os.version")).append(" (")
                .append(System.getProperty("os.arch")).append(")\n");
        sb.append("Java: ").append(System.getProperty("java.version")).append('\n');
        sb.append("导出时间: ").append(LocalDateTime.now()).append('\n');
        sb.append("导出设备: ").append(String.join(", ", serials)).append('\n');
        return sb.toString();
    }

    private static void put(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static void copyInto(ZipOutputStream zip, Path entryName, Path file) {
        try {
            zip.putNextEntry(new ZipEntry(entryName.toString()));
            Files.copy(file, zip);
            zip.closeEntry();
        } catch (IOException e) {
            // 单个日志文件读取失败不阻断导出
        }
    }

    private static String safe(String serial) {
        return serial.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
