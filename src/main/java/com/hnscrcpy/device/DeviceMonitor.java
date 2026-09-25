package com.hnscrcpy.device;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 设备轮询监视器：每 2 秒刷新一次设备列表，仅在列表变化时回调。
 * 设备详情（型号/系统版本）按序列号缓存，避免每次轮询都查参数。
 *
 * @Author: taxiao
 * WeChat：他晓
 * CSDN:http://blog.csdn.net/yin13753884368/article
 * Github:https://github.com/taxiao213
 */

public final class DeviceMonitor {

    private static final Logger log = LoggerFactory.getLogger(DeviceMonitor.class);
    private static final long POLL_INTERVAL_SEC = 2;

    public interface Listener {
        void onDevicesChanged(List<DeviceInfo> devices);
    }

    private final HdcClient hdc;
    private final Listener listener;
    private final Map<String, DeviceInfo> detailCache = new LinkedHashMap<>();
    private ScheduledExecutorService scheduler;
    private List<String> lastSerials = List.of();

    public DeviceMonitor(HdcClient hdc, Listener listener) {
        this.hdc = hdc;
        this.listener = listener;
    }

    public synchronized void start() {
        if (scheduler != null) {
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "device-monitor");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(this::pollSafely, 0, POLL_INTERVAL_SEC, TimeUnit.SECONDS);
    }

    public synchronized void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    private void pollSafely() {
        try {
            poll();
        } catch (Exception e) {
            log.warn("device poll failed: {}", e.toString());
        }
    }

    /** 一轮轮询；列表无变化时不回调。包可见便于测试。 */
    void poll() {
        List<String> serials = hdc.listTargets();
        if (serials.equals(lastSerials)) {
            return;
        }
        lastSerials = serials;
        List<DeviceInfo> devices = serials.stream().map(this::describeCached).toList();
        listener.onDevicesChanged(devices);
    }

    private DeviceInfo describeCached(String sn) {
        return detailCache.computeIfAbsent(sn, hdc::describe);
    }
}
