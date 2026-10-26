package com.hnscrcpy.session;

import com.hnscrcpy.control.CoordinateMapper;
import com.hnscrcpy.control.DeviceController;
import com.hnscrcpy.decode.DecoderPump;
import com.hnscrcpy.decode.StreamStalledException;
import com.hnscrcpy.diag.DiagnosticEvent.Type;
import com.hnscrcpy.diag.DiagnosticRegistry;
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
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * 一次投屏会话：桥 → 流 → 解码泵 → 渲染调度 + 控制通道。
 * 状态机：IDLE → STREAMING → CLOSED / ERROR。close 幂等，负责全链路清理。
 *
 * @Author: taxiao
 * WeChat：他晓
 * CSDN:http://blog.csdn.net/yin13753884368/article
 * Github:https://github.com/taxiao213
 */

public final class MirrorSession implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(MirrorSession.class);
    private static final int[] FALLBACK_SCREEN = {1080, 2400};
    /** 断流自动重连：指数退避上限与最大次数；耗尽后转 30s 慢速无限重试（不留僵尸窗口）。 */
    private static final int MAX_RECONNECT_ATTEMPTS = 5;
    private static final long MAX_RECONNECT_DELAY_MS = 10_000;
    private static final long SLOW_RETRY_MS = 30_000;
    /** 设备侧 scrcpy 进程退出需要约 2s，重启前必须等待，防新旧实例冲突。 */
    private static final long RECONNECT_SETTLE_MS = 2_000;
    /** 看门狗间隔：每 tick 请求一次 IDR 保活，并检查本代是否有新帧。 */
    private static final long WATCHDOG_INTERVAL_MS = 10_000;
    /** 首帧宽限：设备侧 daemon 冷启动 + 编码器初始化可能超过 10s，期内只催 IDR 不判死。 */
    static final long STARTUP_GRACE_MS = 30_000;
    /** 输入预言机窗口：窗口内有触摸/键盘注入却画面不变 → 流大概率真死，快速重连。 */
    static final long INPUT_ACTIVE_WINDOW_MS = 15_000;
    /**
     * 纯静默兜底（tick 数）：静态画面不推帧是 hosScrcpy 1.0.20 的正常行为，
     * 探活成功的静默不再立刻重连（旧逻辑每 ~25s 误杀健康流，见 v1.0.3 修复）；
     * 仅当静默持续约 5 分钟（覆盖真·静默死亡）才兜底重连一次。
     */
    static final int PASSIVE_RECONNECT_TICKS = 30;
    /**
     * 降采样 hint 量化步长（px）。视图 layoutBounds 有亚像素抖动（±1-2px），
     * 直接 ceil(×2) 会在相邻值间高频翻转，导致 sws 缩放器与环形缓冲逐帧重建
     * （每次 ~18MB 分配）拖垮解码；量化 + 迟滞吸收抖动，尺寸真变了才重建。
     */
    private static final int HINT_QUANTUM = 8;

    public enum State { IDLE, STREAMING, ERROR, CLOSED }

    private final HosScrcpyBridge bridge;
    private final HosScrcpyStream stream;
    private final DeviceController controller;
    private final com.hnscrcpy.control.TextInjector textInjector;
    private final CoordinateMapper mapper;
    private final String serial;
    private final ExecutorService controlExecutor;
    private final DecoderPump pump;
    private final FrameRenderer renderer;
    private final RenderScheduler renderScheduler;
    private final Consumer<String> statusListener;
    private final ScheduledExecutorService reconnectScheduler;
    private final AtomicInteger reconnectAttempts = new AtomicInteger();
    private final AtomicLong watchdogLastDecoded = new AtomicLong(-1);
    private final java.util.concurrent.atomic.AtomicBoolean reconnectPending =
            new java.util.concurrent.atomic.AtomicBoolean();
    private ScheduledFuture<?> watchdogTask;
    /** 当前生效的降采样 hint（量化后）；null 表示尚未初始化。 */
    private volatile int[] currentHint;
    /** 本代流开始时间（看门狗启动宽限用）；仅看门狗线程读写。 */
    private long streamStartedAtMs;
    /** 连续静默 tick 数（无新帧）；仅看门狗线程读写。 */
    private int silentTicks;
    /** 最近一次输入注入时间（nanoTime；0=从未输入）。看门狗"输入预言机"用。 */
    private final AtomicLong lastInputAtNanos = new AtomicLong();

    private volatile State state = State.IDLE;

    public MirrorSession(String sn, VideoConfig config, Consumer<String> statusListener) {
        this(sn, config, statusListener, new FrameRenderer());
    }

    /**
     * @param renderer 调用方预建的渲染器（窗口可先上屏、会话后台异步启动）；
     *                 本会话独占该渲染器。
     */
    public MirrorSession(String sn, VideoConfig config, Consumer<String> statusListener,
                         FrameRenderer renderer) {
        this.statusListener = statusListener;
        this.serial = sn;
        this.renderer = renderer;
        Path jar = HosScrcpyLocator.locate()
                .orElseThrow(() -> new SessionException(HosScrcpyLocator.guidance()));
        Path hdcPath = HdcLocator.locate();
        this.bridge = HosScrcpyBridge.open(jar, sn, hdcPath.toString(), config);
        this.stream = new HosScrcpyStream(bridge, sn);
        HdcClient hdc = new HdcClient(hdcPath);
        this.controller = new DeviceController(bridge, hdc, sn);
        this.textInjector = new com.hnscrcpy.control.TextInjector(
                new com.hnscrcpy.control.DeviceShell(
                        (cmd, timeout) -> bridge.executeShellCommand(cmd, timeout), hdc, sn), sn);
        int[] screen = hdc.screenSize(sn).orElse(FALLBACK_SCREEN);
        this.mapper = new CoordinateMapper(screen[0], screen[1]);
        this.controlExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "control-channel");
            t.setDaemon(true);
            return t;
        });
        DiagnosticRegistry.record(serial, Type.SESSION_STARTED, null);
        this.pump = new DecoderPump(sn);
        this.renderScheduler = new RenderScheduler(pump, renderer);
        this.reconnectScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "stream-reconnect");
            t.setDaemon(true);
            return t;
        });
        pump.setErrorHandler(this::onStreamFailure);
        // 流就绪即催 IDR：hosScrcpy 1.0.20 首帧参数集可能延迟数秒才推，主动催缩短黑屏
        pump.setReadyListener(() -> {
            if (state != State.CLOSED) {
                try {
                    stream.requestIDRFrame();
                } catch (RuntimeException e) {
                    log.debug("IDR request on ready failed: {}", e.toString());
                }
            }
        });
        renderScheduler.setStatsListener(fps -> status("FPS " + fps
                + " | 解码 " + pump.decodedCount() + " | 丢帧 " + pump.droppedCount()));
        // 旋转自适应：解码帧宽高比变化时同步映射器
        renderScheduler.setFrameHook(() -> {
            var f = pump.latestFrame();
            if (f != null) {
                mapper.setHorizontal(f.width() > f.height());
            }
        });
        // 自适应降采样：解码输出尺寸跟随视图（×2 超采样，封顶原生），
        // 窗口较小时 YUV→ARGB+缩放合并在一次 sws 里，像素搬运量降一个数量级。
        // hint 量化到 HINT_QUANTUM 并带迟滞：亚像素抖动不触发缩放器重建
        renderer.getView().layoutBoundsProperty().addListener((obs, o, b) -> {
            int[] hint = renderer.desiredOutputSize();
            if (hint == null) {
                return;
            }
            int w = hint[0] / HINT_QUANTUM * HINT_QUANTUM;
            int h = hint[1] / HINT_QUANTUM * HINT_QUANTUM;
            int[] cur = currentHint;
            if (cur != null && Math.abs(cur[0] - w) < HINT_QUANTUM
                    && Math.abs(cur[1] - h) < HINT_QUANTUM) {
                return;
            }
            currentHint = new int[]{w, h};
            pump.setOutputSizeHint(currentHint);
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
        int[] px = new int[f.width() * f.height()];
        f.pixels().get(px);
        img.setRGB(0, 0, f.width(), f.height(), px, 0, f.width());
        javax.imageio.ImageIO.write(img, "png", out.toFile());
        log.info("screenshot saved: {}", out);
        return out;
    }

    /**
     * 启动会话；失败抛 SessionException。synchronized：异步启动期间窗口被关时
     * close() 会等待本方法完成（最长为 startCaptureScreen 的设备握手时间），
     * 随后由 close() 正常清理，不泄漏会话。
     */
    public synchronized void start() {
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
        if (state == State.CLOSED) {
            // 理论上到不了（close 会等本方法），防御性回滚刚建立的流
            log.info("session closed while starting, rolling back");
            try {
                stream.stop();
            } catch (RuntimeException e) {
                log.debug("rollback stream stop failed: {}", e.toString());
            }
            pump.close();
            return;
        }
        renderScheduler.start();
        state = State.STREAMING;
        status("投屏中");
        startWatchdog();
        log.info("mirror session started, state=STREAMING");
    }

    public ImageView getView() {
        return renderer.getView();
    }

    public DeviceController controller() {
        return controller;
    }

    /** 文本注入器（键盘输入 / 剪贴板粘贴共用）。 */
    public com.hnscrcpy.control.TextInjector textInjector() {
        return textInjector;
    }

    public CoordinateMapper mapper() {
        return mapper;
    }

    /** 记录一次输入注入（触摸/键盘/粘贴）；看门狗据此判断"有输入却画面不变"。 */
    public void noteInputActivity() {
        lastInputAtNanos.set(System.nanoTime());
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

    /** 流异常/结束（gRPC 线程）：进入 ERROR 并排队指数退避重连；并发失败合并为一次重连。 */
    private void onStreamFailure(Throwable t) {
        if (serial != null) {
            org.slf4j.MDC.put("serial", serial);
        }
        if (state != State.STREAMING && state != State.ERROR) {
            return;
        }
        if (!reconnectPending.compareAndSet(false, true)) {
            log.debug("reconnect already scheduled, coalescing: {}", t.toString());
            return;
        }
        state = State.ERROR;
        int attempt = reconnectAttempts.incrementAndGet();
        long delay = attempt <= MAX_RECONNECT_ATTEMPTS
                ? Math.min(1000L << (attempt - 1), MAX_RECONNECT_DELAY_MS)
                : SLOW_RETRY_MS;
        status("连接中断，" + (delay / 1000) + "s 后重连（第 " + attempt + " 次）…");
        log.info("stream failure, reconnect attempt {} in {}ms: {}", attempt, delay, t.toString());
        DiagnosticRegistry.record(serial, Type.STREAM_ERROR, t.toString());
        DiagnosticRegistry.record(serial, Type.RECONNECT_SCHEDULED,
                "第 " + attempt + " 次重连，" + delay + "ms 后");
        try {
            reconnectScheduler.schedule(() -> {
                reconnectPending.set(false);
                reconnect();
            }, delay, TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.RejectedExecutionException e) {
            // close() 已关闭调度器：竞态窗口内的失败回调直接丢弃
            reconnectPending.set(false);
            log.debug("reconnect discarded, scheduler shut down");
        }
    }

    /** synchronized：与 close() 互斥，避免关窗竞态里把流重新拉活成僵尸会话。 */
    private synchronized void reconnect() {
        if (state == State.CLOSED || state == State.STREAMING) {
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
            startWatchdog();
            log.info("stream reconnected");
            DiagnosticRegistry.record(serial, Type.RECONNECTED, null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            log.warn("reconnect attempt failed: {}", e.toString());
            onStreamFailure(e);
        }
    }

    private void startWatchdog() {
        watchdogLastDecoded.set(-1);
        silentTicks = 0;
        streamStartedAtMs = System.currentTimeMillis();
        if (watchdogTask != null) {
            watchdogTask.cancel(false);
        }
        watchdogTask = reconnectScheduler.scheduleWithFixedDelay(this::watchdogTick,
                WATCHDOG_INTERVAL_MS, WATCHDOG_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * 看门狗 tick。hosScrcpy 1.0.20 静态画面不推帧、IDR 请求也不强制推，
     * 「无新帧」≠「流已死」——先探活/看输入活动再决定是否重连：
     * 1. 有新帧 → 清零静默计数，IDR 保活；
     * 2. 首帧未到 → 宽限期内只催 IDR，超宽限重连；
     * 3. 首帧后静默 → {@link #shouldReconnectOnSilence}（探活 + 输入预言机 + 兜底）。
     */
    private void watchdogTick() {
        if (state != State.STREAMING) {
            watchdogLastDecoded.set(-1);
            return;
        }
        long decoded = pump.decodedCount();
        if (decoded != watchdogLastDecoded.get()) {
            watchdogLastDecoded.set(decoded);
            silentTicks = 0;
            requestIdr();
            return;
        }
        silentTicks++;
        if (decoded == 0) {
            if (System.currentTimeMillis() - streamStartedAtMs < STARTUP_GRACE_MS) {
                log.debug("awaiting first frame ({}ms), requesting IDR",
                        System.currentTimeMillis() - streamStartedAtMs);
                requestIdr();
                return;
            }
            log.warn("no frame within startup grace {}ms, reconnecting", STARTUP_GRACE_MS);
            DiagnosticRegistry.record(serial, Type.WATCHDOG_STALL, "启动宽限 " + STARTUP_GRACE_MS + "ms 内零帧");
            onStreamFailure(new StreamStalledException());
            return;
        }
        long lastInput = lastInputAtNanos.get();
        boolean inputActive = lastInput != 0
                && (System.nanoTime() - lastInput) / 1_000_000 < INPUT_ACTIVE_WINDOW_MS;
        boolean probeAlive = probeAlive();
        if (shouldReconnectOnSilence(probeAlive, silentTicks, inputActive)) {
            log.warn("stream silent: probeAlive={}, silentTicks={}, inputActive={} -> reconnect",
                    probeAlive, silentTicks, inputActive);
            DiagnosticRegistry.record(serial, Type.WATCHDOG_STALL,
                    "静默判死：探活=" + probeAlive + " 静默轮=" + silentTicks + " 有输入=" + inputActive);
            onStreamFailure(new StreamStalledException());
            return;
        }
        log.debug("static screen (probe alive, {} silent ticks), keep waiting", silentTicks);
        requestIdr();
    }

    /**
     * 静默判死决策（纯函数，便于单测）：
     * - 有输入活动且连续 2 轮无帧 → 死（输入本应改变画面）；
     * - 探活失败 → 死（守护进程/通道异常）；
     * - 探活正常 = 静态画面，仅静默累计到兜底轮数才重连（覆盖真·静默死亡）。
     */
    static boolean shouldReconnectOnSilence(boolean probeAlive, int silentTicks, boolean inputActive) {
        if (inputActive && silentTicks >= 2) {
            return true;
        }
        if (!probeAlive) {
            return true;
        }
        return silentTicks >= PASSIVE_RECONNECT_TICKS;
    }

    /** 探活：经桥内 shell 执行只读命令，验证设备侧守护进程仍然活着。 */
    private boolean probeAlive() {
        try {
            long t0 = System.nanoTime();
            bridge.executeShellCommand("param get const.product.name", 5);
            log.debug("watchdog probe ok ({}ms)", (System.nanoTime() - t0) / 1_000_000);
            return true;
        } catch (RuntimeException e) {
            log.warn("watchdog probe failed: {}", e.toString());
            return false;
        }
    }

    private void requestIdr() {
        try {
            stream.requestIDRFrame();
        } catch (RuntimeException e) {
            log.warn("requestIDRFrame failed: {}", e.toString());
            onStreamFailure(e);
        }
    }

    /** synchronized：与异步 start() 互斥，启动中关窗会等启动完成后再清理。 */
    @Override
    public synchronized void close() {
        if (state == State.CLOSED) {
            return;
        }
        state = State.CLOSED;
        DiagnosticRegistry.record(serial, Type.SESSION_CLOSED, null);
        renderScheduler.stop();
        if (watchdogTask != null) {
            watchdogTask.cancel(false);
        }
        reconnectScheduler.shutdownNow();
        stream.stop();
        pump.close();
        textInjector.close();
        controlExecutor.shutdownNow();
        bridge.close();
        status("已断开");
        log.info("mirror session closed");
    }
}
