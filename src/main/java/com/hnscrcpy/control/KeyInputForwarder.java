package com.hnscrcpy.control;

import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;

/**
 * 电脑键盘 → 设备输入转发。
 * - 可打印字符（含本机 IME 上屏的中文）进 {@link TextInjector} 攒批注入；
 * - Enter/退格/Tab/方向键等控制键走 uinput（键值表见 DeviceController）；
 * - ⌘/Ctrl 组合键不拦截，交给场景加速器（截图/旋转等既有快捷键不受影响）；
 * - 场景级事件过滤器，无需焦点管理，投屏窗口聚焦即生效。
 *
 * @Author: taxiao
 * WeChat：他晓
 * CSDN:http://blog.csdn.net/yin13753884368/article
 * Github:https://github.com/taxiao213
 */

public final class KeyInputForwarder {

    private static final Logger log = LoggerFactory.getLogger(KeyInputForwarder.class);

    private final DeviceController controller;
    private final TextInjector textInjector;
    private final ExecutorService controlExecutor;
    /** IME 组词中（拼音未上屏）：期间压住原始按键，只等 committed 文本。 */
    private volatile boolean composing;
    /** 输入活动钩子（看门狗"输入预言机"用）；可空。 */
    private Runnable activityHook = () -> { };

    public KeyInputForwarder(DeviceController controller, TextInjector textInjector,
                             ExecutorService controlExecutor) {
        this.controller = controller;
        this.textInjector = textInjector;
        this.controlExecutor = controlExecutor;
    }

    /** 注册输入活动钩子（每次实际下发按键/文本时触发）。 */
    public void setActivityHook(Runnable hook) {
        this.activityHook = hook != null ? hook : () -> { };
    }

    public void attach(Scene scene) {
        scene.addEventFilter(KeyEvent.KEY_TYPED, this::onTyped);
        scene.addEventFilter(KeyEvent.KEY_PRESSED, this::onPressed);
        // IME 上屏文本（中文输入法确认后的整段文字）不走 KEY_TYPED，必须单独监听
        scene.addEventFilter(javafx.scene.input.InputMethodEvent.INPUT_METHOD_TEXT_CHANGED,
                this::onInputMethod);
        log.debug("KeyInputForwarder attached to scene");
    }

    /** 输入法事件：组词中（composed 非空）压住原始按键；上屏（committed 非空）只注入上屏文本。 */
    private void onInputMethod(javafx.scene.input.InputMethodEvent e) {
        StringBuilder sb = new StringBuilder();
        for (javafx.scene.input.InputMethodTextRun run : e.getComposed()) {
            sb.append(run.getText());
        }
        String committed = e.getCommitted();
        if (sb.length() > 0) {
            composing = true;
            return;
        }
        composing = false;
        if (committed != null && !committed.isEmpty()) {
            activityHook.run();
            textInjector.append(committed);
        }
        e.consume();
    }

    /** 打字字符（KEY_TYPED 含 IME 上屏文本）：快通道字符逐键直发，其余攒批注入。 */
    private void onTyped(KeyEvent e) {
        if (e.isShortcutDown() || composing) {
            return; // 加速器处理；IME 组词中的原始字母直接丢弃
        }
        String c = e.getCharacter();
        if (c == null || c.isEmpty()) {
            return;
        }
        boolean hasControl = c.chars().anyMatch(ch -> ch < 0x20);
        if (hasControl) {
            return; // 控制字符由 KEY_PRESSED 处理
        }
        activityHook.run();
        e.consume();
        // ASCII 快通道：~130ms/字符，接近实时。必须先冲刷文本缓冲（同一控制线程串行），
        // 保证「先批量文本、后快通道键」的注入顺序不错乱
        if (c.length() == 1 && com.hnscrcpy.control.KeyCharMap.isFast(c.charAt(0))) {
            char ch = c.charAt(0);
            controlExecutor.execute(() -> {
                try {
                    textInjector.flushSync();
                    controller.typeKeyChar(ch);
                } catch (RuntimeException ex) {
                    log.warn("fast key '{}' forward failed: {}", ch, ex.toString());
                }
            });
            return;
        }
        textInjector.append(c);
    }

    /** 控制键：冲刷文本缓冲后按 uinput 键值注入（同一线程序列化，保证先文后键）。 */
    private void onPressed(KeyEvent e) {
        if (e.isShortcutDown() || composing) {
            return; // 组词中的 Enter/退格属于输入法操作，不转发设备
        }
        KeyCode code = e.getCode();
        Runnable action;
        switch (code) {
            case ENTER -> action = controller::keyEnter;
            case BACK_SPACE -> action = controller::keyBackspace;
            case TAB -> action = controller::keyTab;
            case UP -> action = controller::keyDpadUp;
            case DOWN -> action = controller::keyDpadDown;
            case LEFT -> action = controller::keyDpadLeft;
            case RIGHT -> action = controller::keyDpadRight;
            default -> {
                return;
            }
        }
        final KeyCode keyCode = code;
        final Runnable forward = action;
        e.consume();
        activityHook.run();
        controlExecutor.execute(() -> {
            try {
                textInjector.flushSync();
                forward.run();
            } catch (RuntimeException ex) {
                log.warn("key {} forward failed: {}", keyCode, ex.toString());
            }
        });
    }
}
