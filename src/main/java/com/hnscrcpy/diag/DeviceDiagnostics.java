package com.hnscrcpy.diag;

import com.hnscrcpy.diag.DiagnosticEvent.Type;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * 单台设备的诊断事件环形缓冲：多设备各自独立实例，事件满则丢最旧。
 * 线程安全：事件从 gRPC / 泵 / 看门狗 / 轮询等多个线程写入。
 *
 * @Author: taxiao
 * WeChat：他晓
 * CSDN:http://blog.csdn.net/yin13753884368/article
 * Github:https://github.com/taxiao213
 */

public final class DeviceDiagnostics {

    /** 环形缓冲容量；按 60fps 会话数小时运行估算，500 条足够覆盖最近异常。 */
    static final int CAPACITY = 500;

    private final String serial;
    private final ArrayDeque<DiagnosticEvent> events = new ArrayDeque<>(CAPACITY);
    private String deviceName;
    private boolean online;
    private int unreadErrors;

    DeviceDiagnostics(String serial, String deviceName) {
        this.serial = serial;
        this.deviceName = deviceName;
    }

    public String serial() {
        return serial;
    }

    public synchronized String deviceName() {
        return deviceName;
    }

    synchronized void updateName(String name) {
        if (name != null && !name.isBlank()) {
            deviceName = name;
        }
    }

    public synchronized boolean isOnline() {
        return online;
    }

    synchronized void setOnline(boolean online) {
        this.online = online;
    }

    /** 记录事件；error 类事件计入未读数。 */
    synchronized void add(DiagnosticEvent event) {
        if (events.size() >= CAPACITY) {
            events.poll();
        }
        events.add(event);
        if (event.isError()) {
            unreadErrors++;
        }
    }

    public synchronized List<DiagnosticEvent> events() {
        return new ArrayList<>(events);
    }

    /** 未查看的异常事件数（导出或查看该设备后清零）。 */
    public synchronized int unreadErrors() {
        return unreadErrors;
    }

    public synchronized void markRead() {
        unreadErrors = 0;
    }

    /** 各类别错误计数（报告汇总用）。 */
    public synchronized long errorCount(Type type) {
        return events.stream().filter(e -> e.type() == type).count();
    }
}
