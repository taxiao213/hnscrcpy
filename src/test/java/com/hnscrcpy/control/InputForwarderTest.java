package com.hnscrcpy.control;

import com.hnscrcpy.device.HdcClient;
import com.hnscrcpy.device.StubJar;
import com.hnscrcpy.stream.HosScrcpyBridge;
import com.hnscrcpy.stream.VideoConfig;
import javafx.application.Platform;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Pane;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @Author: taxiao
 * WeChat：他晓
 * CSDN:http://blog.csdn.net/yin13753884368/article
 * Github:https://github.com/taxiao213
 */

class InputForwarderTest {

    @TempDir
    static Path dir;
    private static HosScrcpyBridge bridge;
    private static ExecutorService controlExecutor;

    @BeforeAll
    static void setUp() throws Exception {
        if (!isToolkitRunning()) {
            Platform.startup(() -> {
            });
        }
        bridge = HosScrcpyBridge.open(StubJar.create(dir), "SN99", "/opt/hdc", VideoConfig.defaults());
        controlExecutor = Executors.newSingleThreadExecutor();
    }

    private static boolean isToolkitRunning() {
        try {
            Platform.runLater(() -> {
            });
            return true;
        } catch (IllegalStateException e) {
            return false;
        }
    }

    @AfterAll
    static void tearDown() {
        controlExecutor.shutdownNow();
        bridge.close();
    }

    @BeforeEach
    void clearLog() {
        StubJar.clearLog();
    }

    private static MouseEvent mouse(javafx.event.EventType<MouseEvent> type,
                                    double x, double y, MouseButton button) {
        return new MouseEvent(type, x, y, x, y, button, 1,
                false, false, false, false,
                button == MouseButton.PRIMARY, false, button == MouseButton.SECONDARY,
                false, false, false, null);
    }

    private static void onFx(javafx.event.Event event, Pane pane) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            pane.fireEvent(event);
            latch.countDown();
        });
        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
    }

    /** 等待控制执行器排空（提交哨兵任务并等待完成）。 */
    private void drainControlExecutor() throws Exception {
        controlExecutor.submit(() -> {
        }).get(5, TimeUnit.SECONDS);
    }

    private Pane attachedPane() {
        Pane pane = new Pane();
        HdcClient hdc = new HdcClient(Path.of("/nonexistent-hdc")) {
            @Override
            public String shell(String sn, String command) {
                return "ok";
            }
        };
        DeviceController controller = new DeviceController(bridge, hdc, "SN99");
        new InputForwarder(controller, new CoordinateMapper(1272, 2860),
                () -> 1272, () -> 2860, controlExecutor).attach(pane);
        return pane;
    }

    @Test
    @DisplayName("press-drag-release forwards touch sequence with mapped coords")
    void pressDragRelease_forwardsTouch() throws Exception {
        Pane pane = attachedPane();
        onFx(mouse(MouseEvent.MOUSE_PRESSED, 100, 200, MouseButton.PRIMARY), pane);
        onFx(mouse(MouseEvent.MOUSE_DRAGGED, 110, 210, MouseButton.PRIMARY), pane);
        onFx(mouse(MouseEvent.MOUSE_RELEASED, 110, 210, MouseButton.PRIMARY), pane);
        drainControlExecutor();
        assertThat(StubJar.log())
                .contains("touchDown:100,200")
                .contains("touchMove:110,210")
                .contains("touchUp:110,210");
    }

    @Test
    @DisplayName("right button forwards mouse events instead of touch")
    void rightButton_forwardsMouse() throws Exception {
        Pane pane = attachedPane();
        onFx(mouse(MouseEvent.MOUSE_PRESSED, 50, 60, MouseButton.SECONDARY), pane);
        onFx(mouse(MouseEvent.MOUSE_RELEASED, 50, 60, MouseButton.SECONDARY), pane);
        drainControlExecutor();
        assertThat(StubJar.log())
                .contains("mouseDown:mouseRight:50,60")
                .contains("mouseUp:mouseRight");
    }

    @Test
    @DisplayName("scroll up/down forwards wheel events")
    void scroll_forwardsWheel() throws Exception {
        Pane pane = attachedPane();
        ScrollEvent up = new ScrollEvent(ScrollEvent.SCROLL, 10, 20, 10, 20,
                false, false, false, false, false, false,
                0, 10, 0, 10, ScrollEvent.HorizontalTextScrollUnits.NONE, 1,
                ScrollEvent.VerticalTextScrollUnits.NONE, 1, 0, null);
        ScrollEvent down = new ScrollEvent(ScrollEvent.SCROLL, 10, 20, 10, 20,
                false, false, false, false, false, false,
                0, -10, 0, -10, ScrollEvent.HorizontalTextScrollUnits.NONE, 1,
                ScrollEvent.VerticalTextScrollUnits.NONE, 1, 0, null);
        onFx(up, pane);
        onFx(down, pane);
        drainControlExecutor();
        assertThat(StubJar.log()).contains("wheelUp").contains("wheelDown");
    }
}
