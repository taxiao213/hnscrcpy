package com.hnscrcpy.control;

import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.Node;

import java.util.function.DoubleSupplier;

/**
 * 把 JavaFX 鼠标/滚轮事件转发为设备控制。
 * - 左键 = 触摸（touchDown/Move/Up），右键/中键 = 鼠标事件透传；
 * - 移动节流 ≤60Hz；
 * - 所有事件在 FX 线程触发，uitest 通道是同步 RPC，因此控制调用走单线程执行器串行化。
 */
public final class InputForwarder {

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
        controlExecutor.execute(() -> {
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
        controlExecutor.execute(() -> {
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
        MouseButton btn = pressingButton;
        controlExecutor.execute(() -> {
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
        controlExecutor.execute(() -> {
            if (delta > 0) {
                controller.mouseWheelUp(p[0], p[1]);
            } else if (delta < 0) {
                controller.mouseWheelDown(p[0], p[1]);
            } else {
                controller.mouseWheelStop(p[0], p[1]);
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
