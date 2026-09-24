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
import java.util.function.Consumer;

/**
 * 一次投屏会话：桥 → 流 → 解码泵 → 渲染调度 + 控制通道。
 * 状态机：IDLE → STREAMING → CLOSED / ERROR。close 幂等，负责全链路清理。
 */
public final class MirrorSession implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(MirrorSession.class);
    private static final int[] FALLBACK_SCREEN = {1080, 2400};

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
        controlExecutor.shutdownNow();
        bridge.close();
        status("已断开");
        log.info("mirror session closed");
    }
}
