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
    private final VideoConfig config;
    private volatile HosScrcpyBridge bridge;
    private volatile boolean running;

    public HosScrcpyStream(String sn, VideoConfig config) {
        this.sn = sn;
        this.config = config;
    }

    @Override
    public void start(FrameSink sink) {
        if (running) {
            throw new IllegalStateException("stream already running");
        }
        Path jar = HosScrcpyLocator.locate()
                .orElseThrow(() -> new com.hnscrcpy.session.SessionException(HosScrcpyLocator.guidance()));
        Path hdc = HdcLocator.locate();
        HosScrcpyBridge b = HosScrcpyBridge.open(jar, sn, hdc.toString(), config);
        bridge = b;
        running = true;
        try {
            b.startCaptureScreen(b.newCallback(sink));
        } catch (RuntimeException e) {
            running = false;
            b.close();
            bridge = null;
            throw e;
        }
        log.info("stream started for {}", sn);
    }

    @Override
    public void stop() {
        HosScrcpyBridge b = bridge;
        bridge = null;
        if (b == null) {
            return;
        }
        running = false;
        try {
            b.stopCaptureScreen();
        } catch (RuntimeException e) {
            log.warn("stopCaptureScreen failed: {}", e.toString());
        } finally {
            b.close();
        }
        log.info("stream stopped for {}", sn);
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    public void requestIDRFrame() {
        HosScrcpyBridge b = bridge;
        if (b != null) {
            b.requestIDRFrame();
        }
    }
}
