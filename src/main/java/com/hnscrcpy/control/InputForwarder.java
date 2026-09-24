package com.hnscrcpy.control;

import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.DoubleSupplier;

/**
 * 把 JavaFX 鼠标/滚轮事件转发为设备控制。
 * - 左键 = 触摸（touchDown/Move/Up），右键/中键 = 鼠标事件透传；
 * - 移动节流 ≤60Hz；
 * - 所有事件在 FX 线程触发，uitest 通道是同步 RPC，因此控制调用走单线程执行器串行化。
 */
public final class InputForwarder {

    private static final Logger log = LoggerFactory.getLogger(InputForwarder.class);

    private static final long MOVE_MIN_INTERVAL_MS = 16;

    private final DeviceController controller;
    private final CoordinateMapper mapper;
    private final DoubleSupplier viewWidth;
    private final DoubleSupplier viewHeight;
    private final java.util.concurrent.ExecutorService controlExecutor;

    private boolean pressing;
    private MouseButton pressingButton;
    private long lastMoveMs;

    public InputForwarder(DeviceController controller, CoordinateMapper mapper,
                          DoubleSupplier viewWidth, DoubleSupplier viewHeight,
                          java.util.concurrent.ExecutorService controlExecutor) {
        this.controller = controller;
        this.mapper = mapper;
        this.viewWidth = viewWidth;
        this.viewHeight = viewHeight;
        this.controlExecutor = controlExecutor;
    }

    public void attach(Node node) {
        node.setOnMousePressed(this::onPressed);
        node.setOnMouseDragged(this::onDragged);
        node.setOnMouseReleased(this::onReleased);
        node.setOnScroll(this::onScroll);
    }

    private int[] map(MouseEvent e) {
        return mapper.toDevice(e.getX(), e.getY(), viewWidth.getAsDouble(), viewHeight.getAsDouble());
    }

    private void onPressed(MouseEvent e) {
        pressing = true;
        pressingButton = e.getButton();
        int[] p = map(e);
        log.debug("mouse {} pressed scene=({},{}) -> device=({},{})",
                e.getButton(), e.getX(), e.getY(), p[0], p[1]);
        submit(p, () -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                controller.touchDown(p[0], p[1]);
            } else {
                controller.mouseDown(buttonName(e.getButton()), p[0], p[1]);
            }
        });
    }

    private void onDragged(MouseEvent e) {
        if (!pressing) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastMoveMs < MOVE_MIN_INTERVAL_MS) {
            return;
        }
        lastMoveMs = now;
        int[] p = map(e);
        submit(p, () -> {
            if (pressingButton == MouseButton.PRIMARY) {
                controller.touchMove(p[0], p[1]);
            } else {
                controller.mouseMove(buttonName(pressingButton), p[0], p[1]);
            }
        });
    }

    private void onReleased(MouseEvent e) {
        if (!pressing) {
            return;
        }
        pressing = false;
        int[] p = map(e);
        log.debug("mouse {} released scene=({},{}) -> device=({},{})",
                e.getButton(), e.getX(), e.getY(), p[0], p[1]);
        MouseButton btn = pressingButton;
        submit(p, () -> {
            if (btn == MouseButton.PRIMARY) {
                controller.touchUp(p[0], p[1]);
            } else {
                controller.mouseUp(buttonName(btn), p[0], p[1]);
            }
        });
    }

    private void onScroll(ScrollEvent e) {
        int[] p = mapper.toDevice(e.getX(), e.getY(), viewWidth.getAsDouble(), viewHeight.getAsDouble());
        double delta = e.getDeltaY();
        log.debug("scroll delta={} scene=({},{}) -> device=({},{})", delta, e.getX(), e.getY(), p[0], p[1]);
        submit(p, () -> {
            if (delta > 0) {
                controller.mouseWheelUp(p[0], p[1]);
            } else if (delta < 0) {
                controller.mouseWheelDown(p[0], p[1]);
            } else {
                controller.mouseWheelStop(p[0], p[1]);
            }
        });
    }

    /** 控制调用串行化提交；异常必须接住——执行器线程的未捕获异常会静默消失。 */
    private void submit(int[] p, Runnable call) {
        controlExecutor.execute(() -> {
            try {
                call.run();
            } catch (RuntimeException ex) {
                log.warn("control call ({},{}) failed: {}", p[0], p[1], ex.toString());
            }
        });
    }

    private static String buttonName(MouseButton b) {
        return switch (b) {
            case SECONDARY -> DeviceController.MOUSE_RIGHT;
            case MIDDLE -> DeviceController.MOUSE_MIDDLE;
            default -> DeviceController.MOUSE_LEFT;
        };
    }
}
