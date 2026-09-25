package com.hnscrcpy.device;

import com.hnscrcpy.util.ProcessRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * hdc 命令封装。所有设备交互（shell / fport / 文件传输）经过这里。
 * 解析逻辑拆为包可见静态方法，便于单测。
 *
 * @Author: taxiao
 * WeChat：他晓
 * CSDN:http://blog.csdn.net/yin13753884368/article
 * Github:https://github.com/taxiao213
 */

public class HdcClient {

    private static final Logger log = LoggerFactory.getLogger(HdcClient.class);
    private static final Pattern SCREEN_SIZE = Pattern.compile("activeMode:\\s*(\\d+)x(\\d+)");
    private static final Pattern REFRESH_RATE = Pattern.compile("refresh[Rr]ate[=:]\\s*(\\d+)");

    /** 命令执行 seam：测试注入脚本化实现。 */
    @FunctionalInterface
    interface CommandRunner {
        ProcessRunner.Result run(List<String> command, long timeoutSec);
    }

    private final Path hdc;
    private final CommandRunner runner;

    public HdcClient(Path hdc) {
        this(hdc, (cmd, timeoutSec) -> ProcessRunner.run(cmd, timeoutSec, TimeUnit.SECONDS));
    }

    HdcClient(Path hdc, CommandRunner runner) {
        this.hdc = hdc;
        this.runner = runner;
    }

    public HdcClient() {
        this(HdcLocator.locate());
    }

    /** hdc list targets 原始输出 → 序列号列表。 */
    static List<String> parseTargets(String output) {
        List<String> serials = new ArrayList<>();
        for (String line : output.split("\n")) {
            String s = line.trim();
            if (s.isEmpty() || s.contains("[Empty]") || s.equalsIgnoreCase("empty")) {
                continue;
            }
            serials.add(s);
        }
        return serials;
    }

    /** SP_daemon 屏幕输出 → 分辨率。兼容 activeMode 字段缺失时回落到 refreshrate 行的尺寸。 */
    static Optional<int[]> parseScreenSize(String output) {
        Matcher m = SCREEN_SIZE.matcher(output);
        if (m.find()) {
            return Optional.of(new int[]{Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))});
        }
        return Optional.empty();
    }

    static Optional<Integer> parseRefreshRate(String output) {
        Matcher m = REFRESH_RATE.matcher(output);
        if (m.find()) {
            return Optional.of(Integer.parseInt(m.group(1)));
        }
        return Optional.empty();
    }

    public List<String> listTargets() {
        ProcessRunner.Result r = runner.run(List.of(hdc.toString(), "list", "targets"), 30);
        if (!r.ok()) {
            log.warn("hdc list targets failed: {}", r.output().trim());
            return List.of();
        }
        return parseTargets(r.stdout());
    }

    /** 在指定设备执行 shell 命令（命令字符串由设备端 shell 解析，支持管道）。 */
    public String shell(String sn, String command) {
        return shell(sn, command, 30);
    }

    public String shell(String sn, String command, long timeoutSec) {
        ProcessRunner.Result r = runner.run(
                List.of(hdc.toString(), "-t", sn, "shell", command), timeoutSec);
        return r.output().trim();
    }

    public boolean fport(String sn, int localPort, String remote) {
        ProcessRunner.Result r = runner.run(
                List.of(hdc.toString(), "-t", sn, "fport", "tcp:" + localPort, remote), 15);
        if (!r.ok()) {
            log.warn("fport tcp:{} -> {} failed: {}", localPort, remote, r.output().trim());
        }
        return r.ok();
    }

    public boolean fportRemove(String sn, int localPort) {
        ProcessRunner.Result r = runner.run(
                List.of(hdc.toString(), "-t", sn, "fport", "rm", "tcp:" + localPort), 15);
        return r.ok();
    }

    public boolean fileSend(String sn, Path local, String remotePath) {
        ProcessRunner.Result r = runner.run(
                List.of(hdc.toString(), "-t", sn, "file", "send", local.toString(), remotePath), 60);
        if (!r.ok()) {
            log.warn("file send {} -> {} failed: {}", local, remotePath, r.output().trim());
        }
        return r.ok();
    }

    /** 读取设备参数（param get），返回去除换行的值。 */
    public String getParam(String sn, String name) {
        return shell(sn, "param get " + name).trim();
    }

    /** 填充设备详情（型号 / 系统版本）。 */
    public DeviceInfo describe(String sn) {
        String model = getParam(sn, "const.product.name");
        if (model.isBlank() || model.equals("null")) {
            model = getParam(sn, "const.product.model");
        }
        String os = getParam(sn, "const.product.software.version");
        return new DeviceInfo(sn, model, os);
    }

    /**
     * 查询屏幕分辨率与刷新率。SP_daemon 输出格式见 HOS_SCRCPY_PROTOCOL.md。
     * 返回 int[]{width, height, refreshRate}，解析失败返回 empty。
     */
    public Optional<int[]> screenSize(String sn) {
        String out = shell(sn, "SP_daemon -screen");
        Optional<int[]> size = parseScreenSize(out);
        if (size.isEmpty()) {
            log.warn("parse screen size failed for {}: {}", sn, out);
            return Optional.empty();
        }
        int[] wh = size.get();
        int rate = parseRefreshRate(out).orElse(60);
        return Optional.of(new int[]{wh[0], wh[1], rate});
    }

    /** 设备是否在线（带一次重试，规避 hdc 偶发超时）。 */
    public boolean isOnline(String sn) {
        if (listTargets().contains(sn)) {
            return true;
        }
        return listTargets().contains(sn);
    }

    public Path hdcPath() {
        return hdc;
    }
}
