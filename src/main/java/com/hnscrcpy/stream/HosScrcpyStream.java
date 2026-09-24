package com.hnscrcpy.stream;

import com.hnscrcpy.device.HdcLocator;
import com.hnscrcpy.device.HosScrcpyLocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * 基于 hosScrcpy 的 H.264 流来源。start 为异步：流事件经 FrameSink 回调。
 */
public final class HosScrcpyStream implements StreamProvider {

    private static final Logger log = LoggerFactory.getLogger(HosScrcpyStream.class);

    private final String sn;
    private final HosScrcpyBridge bridge;
    private final boolean ownsBridge;
    private volatile boolean running;

    /** 独立使用：自建桥（设备控制不可用时选这个）。 */
    public HosScrcpyStream(String sn, VideoConfig config) {
        this(openBridge(sn, config), sn, true);
    }

    /** 共享桥：与 DeviceController 共用同一设备会话。 */
    public HosScrcpyStream(HosScrcpyBridge bridge, String sn) {
        this(bridge, sn, false);
    }

    private HosScrcpyStream(HosScrcpyBridge bridge, String sn, boolean ownsBridge) {
        this.bridge = bridge;
        this.sn = sn;
        this.ownsBridge = ownsBridge;
    }

    private static HosScrcpyBridge openBridge(String sn, VideoConfig config) {
        Path jar = HosScrcpyLocator.locate()
                .orElseThrow(() -> new com.hnscrcpy.session.SessionException(HosScrcpyLocator.guidance()));
        Path hdc = HdcLocator.locate();
        return HosScrcpyBridge.open(jar, sn, hdc.toString(), config);
    }

    @Override
    public void start(FrameSink sink) {
        if (running) {
            throw new IllegalStateException("stream already running");
        }
        running = true;
        try {
            bridge.startCaptureScreen(bridge.newCallback(sink));
        } catch (RuntimeException e) {
            running = false;
            if (ownsBridge) {
                bridge.close();
            }
            throw e;
        }
        log.info("stream started for {}", sn);
    }

    @Override
    public void stop() {
        if (!running) {
            return;
        }
        running = false;
        try {
            bridge.stopCaptureScreen();
        } catch (RuntimeException e) {
            log.warn("stopCaptureScreen failed: {}", e.toString());
        } finally {
            if (ownsBridge) {
                bridge.close();
            }
        }
        log.info("stream stopped for {}", sn);
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    public void requestIDRFrame() {
        if (running) {
            bridge.requestIDRFrame();
        }
    }
}
