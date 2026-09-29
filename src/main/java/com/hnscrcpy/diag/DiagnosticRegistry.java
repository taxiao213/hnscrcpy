package com.hnscrcpy.diag;

import com.hnscrcpy.diag.DiagnosticEvent.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 诊断注册表：serial → 设备诊断数据，应用生命周期内常驻（内存）。
 * 各组件通过静态方法打点；serial 为 null 时静默忽略（无设备上下文的调用）。
 *
 * @Author: taxiao
 * WeChat：他晓
 * CSDN:http://blog.csdn.net/yin13753884368/article
 * Github:https://github.com/taxiao213
 */

public final class DiagnosticRegistry {

    private static final Logger log = LoggerFactory.getLogger(DiagnosticRegistry.class);

    /** 保持插入序：先连接的设备排前面。 */
    private static final Map<String, DeviceDiagnostics> DEVICES = new LinkedHashMap<>();

    private DiagnosticRegistry() {
    }

    /** 取（或创建）设备诊断；name 可为 null（后续 updateName 补齐）。 */
    public static synchronized DeviceDiagnostics get(String serial, String name) {
        if (serial == null || serial.isBlank()) {
            return null;
        }
        return DEVICES.computeIfAbsent(serial, s -> new DeviceDiagnostics(s, name));
    }

    /** 打点便捷方法；serial 为 null 静默忽略。 */
    public static void record(String serial, Type type, String detail) {
        if (serial == null || serial.isBlank()) {
            return;
        }
        try {
            get(serial, null).add(new DiagnosticEvent(Instant.now(), type, detail));
        } catch (RuntimeException e) {
            // 诊断记录绝不反噬主流程
            log.debug("diagnostic record failed: {}", e.toString());
        }
    }

    public static synchronized List<DeviceDiagnostics> all() {
        return new ArrayList<>(DEVICES.values());
    }

    /** 设备上线（轮询发现）：更新名称与在线状态。 */
    public static void deviceOnline(String serial, String name) {
        DeviceDiagnostics d = get(serial, name);
        if (d == null) {
            return;
        }
        d.updateName(name);
        boolean was = d.isOnline();
        d.setOnline(true);
        if (!was) {
            d.add(new DiagnosticEvent(Instant.now(), Type.DEVICE_ONLINE, name));
        }
    }

    public static void deviceOffline(String serial) {
        DeviceDiagnostics d = get(serial, null);
        if (d == null) {
            return;
        }
        d.setOnline(false);
        d.add(new DiagnosticEvent(Instant.now(), Type.DEVICE_OFFLINE, null));
    }

    /** 清空全部记录；仅测试使用。 */
    static synchronized void resetForTest() {
        DEVICES.clear();
    }
}
