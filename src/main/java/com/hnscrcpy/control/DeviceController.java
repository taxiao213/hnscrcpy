package com.hnscrcpy.control;

import com.hnscrcpy.device.HdcClient;
import com.hnscrcpy.stream.HosScrcpyBridge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 设备远控。触摸/鼠标/滚轮/旋转走 uitest 通道（hosScrcpy 桥）；
 * 按键走 uinput shell（键值表见 docs/HOS_SCRCPY_PROTOCOL.md，真机验证）。
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

    private final HosScrcpyBridge bridge;
    private final HdcClient hdc;
    private final String sn;

    public DeviceController(HosScrcpyBridge bridge, HdcClient hdc, String sn) {
        this.bridge = bridge;
        this.hdc = hdc;
        this.sn = sn;
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

    /** 横屏（设备旋转 90°）。 */
    public void setRotationHorizontal() {
        bridge.invoke("setRotationHorizontal", NONE);
    }

    public void setRotationVertical() {
        bridge.invoke("setRotationVertical", NONE);
    }

    private void pressKey(int code) {
        String out = hdc.shell(sn, "uinput -K -d " + code + " -u " + code);
        log.debug("uinput key {} -> {}", code, out);
    }

    private static final Class<?>[] XY = {int.class, int.class};
    private static final Class<?>[] XY_STR = {String.class, int.class, int.class};
    private static final Class<?>[] NONE = {};
}
