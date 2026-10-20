package com.hnscrcpy.control;

import com.hnscrcpy.device.HdcClient;
import com.hnscrcpy.diag.DiagnosticEvent.Type;
import com.hnscrcpy.diag.DiagnosticRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 文本注入器：把电脑键盘输入 / 剪贴板文本注入到设备当前输入框。
 *
 * <p>通道：设备自带 {@code uitest uiInput inputText <x> <y> '<text>'}（经 hdc shell），
 * 走设备 IME 输入通道，天然支持中文。坐标为「最后触摸点」——用户点过输入框后
 * 该点即输入焦点位置（见 KeyInputForwarder / InputForwarder 的锚点回填）。
 * uinput 键值表见 DeviceController / docs/HOS_SCRCPY_PROTOCOL.md。</p>
 *
 * <p>设计：单线程执行器串行注入；打字字符攒缓冲，停顿 {@link #IDLE_FLUSH_MS}ms
 * 后批量注入（uitest 一次注入一整段，比逐字符 shell RPC 快一个数量级）；
 * 换行拆段逐段注入并在段间发 Enter；超长文本分片防 shell 参数溢出。</p>
 *
 * @Author: taxiao
 * WeChat：他晓
 * CSDN:http://blog.csdn.net/yin13753884368/article
 * Github:https://github.com/taxiao213
 */

public final class TextInjector implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(TextInjector.class);

    /** 打字停顿该时长后批量注入。 */
    static final long IDLE_FLUSH_MS = 150;
    /** 单次 shell 注入文本分片上限（字符），防命令行参数过长。 */
    static final int MAX_SLICE = 2000;

    private final HdcClient hdc;
    private final String sn;
    private final ExecutorService executor;
    private final ScheduledExecutorService scheduler;
    private final Object lock = new Object();
    private final StringBuilder buffer = new StringBuilder();
    private volatile int[] anchor;
    private ScheduledFuture<?> pending;
    private volatile boolean closed;

    public TextInjector(HdcClient hdc, String sn) {
        this.hdc = hdc;
        this.sn = sn;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "text-inject");
            t.setDaemon(true);
            return t;
        });
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "text-flush");
            t.setDaemon(true);
            return t;
        });
    }

    /** 记录最后触摸的设备坐标（输入焦点锚点）。由 InputForwarder 在触摸按下时回填。 */
    public void setAnchor(int x, int y) {
        this.anchor = new int[]{x, y};
    }

    /** 打字追加（FX 线程高频调用）：入缓冲，停顿后批量注入。 */
    public void append(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        ScheduledFuture<?> toSchedule = null;
        synchronized (lock) {
            if (closed) {
                return;
            }
            buffer.append(text);
            if (pending == null) {
                toSchedule = scheduler.schedule(this::flush,
                        IDLE_FLUSH_MS, TimeUnit.MILLISECONDS);
                pending = toSchedule;
            }
        }
    }

    /** 粘贴/整段注入：先冲刷打字缓冲，再立即注入整段文本。 */
    public void injectNow(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        synchronized (lock) {
            if (closed) {
                return;
            }
        }
        executor.execute(() -> {
            flushLocked();
            injectSegments(text);
        });
    }

    /** 同步冲刷打字缓冲（特殊按键前调用，保证文本先于按键到达）。 */
    public void flushSync() {
        synchronized (lock) {
            if (closed) {
                return;
            }
        }
        try {
            executor.submit(this::flushLocked).get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("flushSync failed: {}", e.toString());
        }
    }

    /** 异步冲刷打字缓冲。 */
    public void flush() {
        synchronized (lock) {
            if (closed) {
                return;
            }
        }
        executor.execute(this::flushLocked);
    }

    /** 取走并清空打字缓冲（须在注入线程/持锁语义下调用）。 */
    private void flushLocked() {
        String text;
        synchronized (lock) {
            if (pending != null) {
                pending.cancel(false);
                pending = null;
            }
            if (buffer.length() == 0) {
                return;
            }
            text = buffer.toString();
            buffer.setLength(0);
        }
        int[] a = anchor;
        if (a == null) {
            DiagnosticRegistry.record(sn, Type.TEXT_INJECT_FAILED,
                    "无锚点：尚未在设备上点击过输入框");
            log.warn("text dropped (no anchor): {} chars", text.length());
            return;
        }
        for (String slice : slices(text)) {
            injectSlice(slice);
        }
    }

    /** 按序注入各分片；"\n" 分片表示换行，注入 Enter 键。 */
    private void injectSegments(String text) {
        for (String slice : slices(text)) {
            injectSlice(slice);
        }
    }

    private void injectSlice(String slice) {
        if ("\n".equals(slice)) {
            String out = hdc.shell(sn, "uinput -K -d " + DeviceController.KEY_ENTER
                    + " -u " + DeviceController.KEY_ENTER, 10);
            log.debug("newline -> uinput ENTER: {}", out.trim());
            return;
        }
        doInject(slice);
    }

    private void doInject(String text) {
        int[] a = anchor;
        if (a == null) {
            DiagnosticRegistry.record(sn, Type.TEXT_INJECT_FAILED, "无锚点");
            return;
        }
        String cmd = buildInputCommand(a[0], a[1], text);
        try {
            String out = hdc.shell(sn, cmd, 15);
            if (out.contains("[Fail]")) {
                DiagnosticRegistry.record(sn, Type.TEXT_INJECT_FAILED, out.trim());
                log.warn("inputText failed: {}", out.trim());
            } else {
                log.debug("inputText ok ({} chars): {}", text.length(),
                        text.length() <= 20 ? text : text.substring(0, 20) + "…");
            }
        } catch (RuntimeException e) {
            DiagnosticRegistry.record(sn, Type.TEXT_INJECT_FAILED, e.toString());
            log.warn("inputText exception: {}", e.toString());
        }
    }

    /** 换行拆段 + 超长分片；"\n" 分片标记换行（调用方段间注入 Enter）。 */
    static List<String> slices(String text) {
        List<String> out = new ArrayList<>();
        String normalized = text.replace("\r\n", "\n").replace("\r", "\n");
        String[] lines = normalized.split("\n", -1);
        for (int li = 0; li < lines.length; li++) {
            String line = lines[li];
            for (int i = 0; i < line.length(); i += MAX_SLICE) {
                out.add(line.substring(i, Math.min(line.length(), i + MAX_SLICE)));
            }
            if (li < lines.length - 1) {
                out.add("\n");
            }
        }
        return out;
    }

    /**
     * 构造 inputText 命令。文本包单引号，内部单引号按 shell 规则转义为 {@code '\''}；
     * 控制字符剔除（\n 已在上层拆段处理）。
     */
    static String buildInputCommand(int x, int y, String text) {
        StringBuilder sb = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n' || c == '\r' || c < 0x20) {
                continue;
            }
            if (c == '\'') {
                sb.append("'\\''");
            } else {
                sb.append(c);
            }
        }
        return "uitest uiInput inputText " + x + " " + y + " '" + sb + "'";
    }

    @Override
    public void close() {
        synchronized (lock) {
            closed = true;
        }
        scheduler.shutdownNow();
        executor.shutdownNow();
    }
}
