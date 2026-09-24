package com.hnscrcpy.session;

import com.hnscrcpy.decode.DecoderPump;
import com.hnscrcpy.render.FrameRenderer;
import com.hnscrcpy.render.RenderScheduler;
import com.hnscrcpy.stream.HosScrcpyStream;
import com.hnscrcpy.stream.VideoConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

/**
 * 一次投屏会话：流 → 解码泵 → 渲染调度。状态机：IDLE → STREAMING → CLOSED / ERROR。
 * close 幂等，负责全链路清理。
 */
public final class MirrorSession implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(MirrorSession.class);

    public enum State { IDLE, STREAMING, ERROR, CLOSED }

    private final HosScrcpyStream stream;
    private final DecoderPump pump = new DecoderPump();
    private final FrameRenderer renderer = new FrameRenderer();
    private final RenderScheduler renderScheduler;
    private final Consumer<String> statusListener;

    private volatile State state = State.IDLE;

    public MirrorSession(String sn, VideoConfig config, Consumer<String> statusListener) {
        this.stream = new HosScrcpyStream(sn, config);
        this.renderScheduler = new RenderScheduler(pump, renderer);
        this.statusListener = statusListener;
        renderScheduler.setStatsListener(fps -> status("FPS " + fps
                + " | 解码 " + pump.decodedCount() + " | 丢帧 " + pump.droppedCount()));
    }

    /** 启动会话；渲染节点由 getView() 提供。失败抛 SessionException。 */
    public void start() {
        if (state != State.IDLE) {
            throw new IllegalStateException("session already started: " + state);
        }
        status("正在连接设备…");
        pump.start();
        try {
            stream.start(pump);
        } catch (RuntimeException e) {
            state = State.ERROR;
            pump.close();
            status("连接失败: " + e.getMessage());
            throw e instanceof SessionException e1 ? e1 : new SessionException(e.getMessage(), e);
        }
        renderScheduler.start();
        state = State.STREAMING;
        status("投屏中");
        log.info("mirror session started, state=STREAMING");
    }

    public javafx.scene.image.ImageView getView() {
        return renderer.getView();
    }

    public State state() {
        return state;
    }

    private void status(String msg) {
        statusListener.accept(msg);
    }

    @Override
    public void close() {
        if (state == State.CLOSED) {
            return;
        }
        state = State.CLOSED;
        renderScheduler.stop();
        stream.stop();
        pump.close();
        status("已断开");
        log.info("mirror session closed");
    }
}
