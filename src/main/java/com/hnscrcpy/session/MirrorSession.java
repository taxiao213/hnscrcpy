package com.hnscrcpy.session;

import com.hnscrcpy.control.CoordinateMapper;
import com.hnscrcpy.control.DeviceController;
import com.hnscrcpy.decode.DecoderPump;
import com.hnscrcpy.device.HdcClient;
import com.hnscrcpy.device.HdcLocator;
import com.hnscrcpy.device.HosScrcpyLocator;
import com.hnscrcpy.render.FrameRenderer;
import com.hnscrcpy.render.RenderScheduler;
import com.hnscrcpy.stream.HosScrcpyBridge;
import com.hnscrcpy.stream.HosScrcpyStream;
import com.hnscrcpy.stream.VideoConfig;
import javafx.scene.image.ImageView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * 一次投屏会话：桥 → 流 → 解码泵 → 渲染调度 + 控制通道。
 * 状态机：IDLE → STREAMING → CLOSED / ERROR。close 幂等，负责全链路清理。
 */
public final class MirrorSession implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(MirrorSession.class);
    private static final int[] FALLBACK_SCREEN = {1080, 2400};
    /** 断流自动重连：指数退避上限与最大次数。 */
    private static final int MAX_RECONNECT_ATTEMPTS = 5;
    private static final long MAX_RECONNECT_DELAY_MS = 10_000;
    /** 设备侧 scrcpy 进程退出需要约 2s，重启前必须等待，防新旧实例冲突。 */
    private static final long RECONNECT_SETTLE_MS = 2_000;

    public enum State { IDLE, STREAMING, ERROR, CLOSED }

    private final HosScrcpyBridge bridge;
    private final HosScrcpyStream stream;
    private final DeviceController controller;
    private final CoordinateMapper mapper;
    private final ExecutorService controlExecutor;
    private final DecoderPump pump = new DecoderPump();
    private final FrameRenderer renderer = new FrameRenderer();
    private final RenderScheduler renderScheduler;
    private final Consumer<String> statusListener;
    private final ScheduledExecutorService reconnectScheduler;
    private final AtomicInteger reconnectAttempts = new AtomicInteger();

    private volatile State state = State.IDLE;

    public MirrorSession(String sn, VideoConfig config, Consumer<String> statusListener) {
        this.statusListener = statusListener;
        Path jar = HosScrcpyLocator.locate()
                .orElseThrow(() -> new SessionException(HosScrcpyLocator.guidance()));
        Path hdcPath = HdcLocator.locate();
        this.bridge = HosScrcpyBridge.open(jar, sn, hdcPath.toString(), config);
        this.stream = new HosScrcpyStream(bridge, sn);
        HdcClient hdc = new HdcClient(hdcPath);
        this.controller = new DeviceController(bridge, hdc, sn);
        int[] screen = hdc.screenSize(sn).orElse(FALLBACK_SCREEN);
        this.mapper = new CoordinateMapper(screen[0], screen[1]);
        this.controlExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "control-channel");
            t.setDaemon(true);
            return t;
        });
        this.renderScheduler = new RenderScheduler(pump, renderer);
        this.reconnectScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "stream-reconnect");
            t.setDaemon(true);
            return t;
        });
        pump.setErrorHandler(this::onStreamFailure);
        renderScheduler.setStatsListener(fps -> status("FPS " + fps
                + " | 解码 " + pump.decodedCount() + " | 丢帧 " + pump.droppedCount()));
        // 旋转自适应：解码帧宽高比变化时同步映射器
        renderScheduler.setFrameHook(() -> {
            var f = pump.latestFrame();
            if (f != null) {
                mapper.setHorizontal(f.width() > f.height());
            }
        });
    }

    /** 保存当前画面截图到 ~/Pictures/hnscrcpy/，返回文件路径。 */
    public java.nio.file.Path saveScreenshot() throws java.io.IOException {
        var f = pump.latestFrame();
        if (f == null) {
            throw new SessionException("尚无画面可截图");
        }
        java.nio.file.Path dir = java.nio.file.Path.of(
                System.getProperty("user.home"), "Pictures", "hnscrcpy");
        java.nio.file.Files.createDirectories(dir);
        String ts = new java.text.SimpleDateFormat("yyyyMMdd-HHmmss").format(new java.util.Date());
        java.nio.file.Path out = dir.resolve("screenshot-" + ts + ".png");
        var img = new java.awt.image.BufferedImage(f.width(), f.height(),
                java.awt.image.BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, f.width(), f.height(), f.pixels(), 0, f.width());
        javax.imageio.ImageIO.write(img, "png", out.toFile());
        log.info("screenshot saved: {}", out);
        return out;
    }

    /** 启动会话；失败抛 SessionException。 */
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
            throw e instanceof SessionException se ? se : new SessionException(e.getMessage(), e);
        }
        renderScheduler.start();
        state = State.STREAMING;
        status("投屏中");
        log.info("mirror session started, state=STREAMING");
    }

    public ImageView getView() {
        return renderer.getView();
    }

    public DeviceController controller() {
        return controller;
    }

    public CoordinateMapper mapper() {
        return mapper;
    }

    public ExecutorService controlExecutor() {
        return controlExecutor;
    }

    public State state() {
        return state;
    }

    /** 累计解码帧数（状态栏/浸泡测试用）。 */
    public long decodedCount() {
        return pump.decodedCount();
    }

    /** 累计丢帧数。 */
    public long droppedCount() {
        return pump.droppedCount();
    }

    private void status(String msg) {
        // 流回调/重连线程上触发时也安全：统一回到 FX 线程更新状态栏
        if (javafx.application.Platform.isFxApplicationThread()) {
            statusListener.accept(msg);
        } else {
            javafx.application.Platform.runLater(() -> statusListener.accept(msg));
        }
    }

    /** 流异常/结束（gRPC 线程）：进入 ERROR 并排队指数退避重连。 */
    private void onStreamFailure(Throwable t) {
        if (state != State.STREAMING && state != State.ERROR) {
            return;
        }
        state = State.ERROR;
        int attempt = reconnectAttempts.incrementAndGet();
        if (attempt > MAX_RECONNECT_ATTEMPTS) {
            status("连接中断，重连失败，请关闭窗口重新打开");
            log.warn("stream lost, reconnect budget exhausted: {}", t.toString());
            return;
        }
        long delay = Math.min(1000L << (attempt - 1), MAX_RECONNECT_DELAY_MS);
        status("连接中断，" + (delay / 1000) + "s 后重连（第 " + attempt + "/" + MAX_RECONNECT_ATTEMPTS + " 次）…");
        log.info("stream failure, reconnect attempt {} in {}ms: {}", attempt, delay, t.toString());
        reconnectScheduler.schedule(this::reconnect, delay, TimeUnit.MILLISECONDS);
    }

    private void reconnect() {
        if (state == State.CLOSED) {
            return;
        }
        try {
            stream.stop();
            Thread.sleep(RECONNECT_SETTLE_MS);
            pump.restart();
            stream.start(pump);
            reconnectAttempts.set(0);
            state = State.STREAMING;
            status("投屏中");
            log.info("stream reconnected");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            log.warn("reconnect attempt failed: {}", e.toString());
            onStreamFailure(e);
        }
    }

    @Override
    public void close() {
        if (state == State.CLOSED) {
            return;
        }
        state = State.CLOSED;
        renderScheduler.stop();
        reconnectScheduler.shutdownNow();
        stream.stop();
        pump.close();
        controlExecutor.shutdownNow();
        bridge.close();
        status("已断开");
        log.info("mirror session closed");
    }
}
