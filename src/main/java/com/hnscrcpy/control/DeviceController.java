package com.hnscrcpy.control;

import com.hnscrcpy.device.HdcClient;
import com.hnscrcpy.stream.HosScrcpyBridge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 设备远控。触摸/鼠标/滚轮/旋转走 uitest 通道（hosScrcpy 桥）；
 * 按键走 uinput shell（键值表见 docs/HOS_SCRCPY_PROTOCOL.md，真机验证）。
 *
 * @Author: taxiao
 * WeChat：他晓
 * CSDN:http://blog.csdn.net/yin13753884368/article
 * Github:https://github.com/taxiao213
 */

public final class DeviceController {

    private static final Logger log = LoggerFactory.getLogger(DeviceController.class);

    // HosRemoteDevice 按钮常量
    public static final String MOUSE_LEFT = "mouseLeft";
    public static final String MOUSE_RIGHT = "mouseRight";
    public static final String MOUSE_MIDDLE = "mouseMiddle";

    // uinput 键值（真机验证）
    private static final int KEY_HOME = 1;
    private static final int KEY_BACK = 2;
    private static final int KEY_POWER = 18;
    private static final int KEY_RECENT_TASKS = 2720;
    private static final int KEY_VOLUME_UP = 16;
    private static final int KEY_VOLUME_DOWN = 17;

    // OpenHarmony KeyCode 真机标定值（scripts/spike-keymap.sh，2026-10-26）：
    // TAB=2049 / ENTER=2054 / DEL(退格)=2055 / 方向键=2012-2015（非文档记的 2071-2074！）
    // ESCAPE=2070 与 DPAD_CENTER=2016 未单独验证（编辑器内无可观测效果）
    public static final int KEY_TAB = 2049;
    public static final int KEY_ENTER = 2054;
    public static final int KEY_BACKSPACE = 2055;
    static final int KEY_ESCAPE = 2070;
    static final int KEY_DPAD_UP = 2012;
    static final int KEY_DPAD_DOWN = 2013;
    static final int KEY_DPAD_LEFT = 2014;
    static final int KEY_DPAD_RIGHT = 2015;

    private final HosScrcpyBridge bridge;
    private final DeviceShell shell;

    public DeviceController(HosScrcpyBridge bridge, HdcClient hdc, String sn) {
        this.bridge = bridge;
        // 按键走桥内 shell（~130ms），桥异常回退 hdc 子进程（~270ms）
        this.shell = new DeviceShell(
                bridge == null ? null : (cmd, timeout) -> bridge.executeShellCommand(cmd, timeout),
                hdc, sn);
    }

    public void touchDown(int x, int y) {
        bridge.invoke("onTouchDown", XY, x, y);
    }

    public void touchMove(int x, int y) {
        bridge.invoke("onTouchMove", XY, x, y);
    }

    public void touchUp(int x, int y) {
        bridge.invoke("onTouchUp", XY, x, y);
    }

    public void mouseDown(String button, int x, int y) {
        bridge.invoke("onMouseDown", XY_STR, button, x, y);
    }

    public void mouseMove(String button, int x, int y) {
        bridge.invoke("onMouseMove", XY_STR, button, x, y);
    }

    public void mouseUp(String button, int x, int y) {
        bridge.invoke("onMouseUp", XY_STR, button, x, y);
    }

    public void mouseWheelUp(int x, int y) {
        bridge.invoke("onMouseWheelUp", XY, x, y);
    }

    public void mouseWheelDown(int x, int y) {
        bridge.invoke("onMouseWheelDown", XY, x, y);
    }

    public void mouseWheelStop(int x, int y) {
        bridge.invoke("onMouseWheelStop", XY, x, y);
    }

    public void keyHome() {
        pressKey(KEY_HOME);
    }

    public void keyBack() {
        pressKey(KEY_BACK);
    }

    public void keyRecentTasks() {
        pressKey(KEY_RECENT_TASKS);
    }

    public void keyPower() {
        pressKey(KEY_POWER);
    }

    public void keyVolumeUp() {
        pressKey(KEY_VOLUME_UP);
    }

    public void keyVolumeDown() {
        pressKey(KEY_VOLUME_DOWN);
    }

    // ---- 文本编辑特殊键（键盘输入支持，键值待真机标定） ----

    public void keyEnter() {
        pressKey(KEY_ENTER);
    }

    public void keyBackspace() {
        pressKey(KEY_BACKSPACE);
    }

    public void keyTab() {
        pressKey(KEY_TAB);
    }

    public void keyEscape() {
        pressKey(KEY_ESCAPE);
    }

    public void keyDpadUp() {
        pressKey(KEY_DPAD_UP);
    }

    public void keyDpadDown() {
        pressKey(KEY_DPAD_DOWN);
    }

    public void keyDpadLeft() {
        pressKey(KEY_DPAD_LEFT);
    }

    public void keyDpadRight() {
        pressKey(KEY_DPAD_RIGHT);
    }

    /** 横屏（设备旋转 90°）。 */
    public void setRotationHorizontal() {
        bridge.invoke("setRotationHorizontal", NONE);
    }

    public void setRotationVertical() {
        bridge.invoke("setRotationVertical", NONE);
    }

    private void pressKey(int code) {
        String out = shell.exec("uinput -K -d " + code + " -u " + code, 10);
        log.debug("uinput key {} -> {}", code, out);
    }

    /**
     * ASCII 快通道：字符直接发 uinput 键盘事件（~130ms/字符，远低于 inputText ~900ms）。
     * 无需焦点坐标、不经设备 IME 文本接口。Shift 组合键依赖单命令多段 -d/-u 支持
     * （KeyCharMap.COMBINED_SEQUENCE_OK，spike 标定）。
     *
     * @return false 表示该字符无快通道映射，调用方回退 inputText 批量注入
     */
    public boolean typeKeyChar(char c) {
        if (!KeyCharMap.isFast(c)) {
            return false;
        }
        int code = KeyCharMap.keyCode(c);
        String cmd = KeyCharMap.needsShift(c)
                ? "uinput -K -d " + KeyCharMap.KEY_SHIFT_LEFT + " -d " + code
                        + " -u " + code + " -u " + KeyCharMap.KEY_SHIFT_LEFT
                : "uinput -K -d " + code + " -u " + code;
        String out = shell.exec(cmd, 10);
        log.debug("uinput char '{}' (code={}) -> {}", c, code, out);
        return true;
    }

    private static final Class<?>[] XY = {int.class, int.class};
    private static final Class<?>[] XY_STR = {String.class, int.class, int.class};
    private static final Class<?>[] NONE = {};
}
