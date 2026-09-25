package com.hnscrcpy.control;

import com.hnscrcpy.device.HdcClient;
import com.hnscrcpy.device.StubJar;
import com.hnscrcpy.stream.HosScrcpyBridge;
import com.hnscrcpy.stream.VideoConfig;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
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
 * 回归：MirrorWindow 新布局（clip+阴影+pref=0 钉死）下，
 * 画面上的鼠标事件必须仍然冒泡到 InputForwarder 并转发为设备触摸。
 *
 * @Author: taxiao
 * WeChat：他晓
 * CSDN:http://blog.csdn.net/yin13753884368/article
 * Github:https://github.com/taxiao213
 */

class InputForwarderLayoutTest {

    @TempDir
    static Path dir;
    private static HosScrcpyBridge bridge;
    private static ExecutorService controlExecutor;

    private static final class FakeHdc extends HdcClient {
        FakeHdc() {
            super(Path.of("/nonexistent-hdc"));
        }

        @Override
        public String shell(String sn, String command) {
            return "ok";
        }
    }

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

    @Test
    @DisplayName("mouse press on the mirrored screen reaches the device as touchDown")
    void pressOnVideo_forwardsTouch() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                // —— 与 MirrorWindow 完全一致的画布装配 ——
                ImageView view = new ImageView(new WritableImage(1272, 2860));
                view.setPreserveRatio(true);
                Rectangle clip = new Rectangle();
                clip.setArcWidth(32);
                clip.setArcHeight(32);
                clip.widthProperty().bind(view.layoutBoundsProperty().map(b -> b.getWidth()));
                clip.heightProperty().bind(view.layoutBoundsProperty().map(b -> b.getHeight()));
                view.setClip(clip);
                view.setEffect(new DropShadow());
                StackPane holder = new StackPane(view);
                holder.setPadding(new Insets(18, 10, 14, 18));
                holder.setMinSize(0, 0);
                holder.setPrefSize(0, 0);
                view.fitWidthProperty().bind(holder.widthProperty());
                view.fitHeightProperty().bind(holder.heightProperty());
                StackPane toolbar = new StackPane();
                toolbar.setMinWidth(64);
                toolbar.setMaxWidth(64);
                HBox content = new HBox(holder, toolbar);
                HBox.setHgrow(holder, Priority.ALWAYS);

                Stage stage = new Stage();
                stage.setScene(new Scene(content, 520, 920));
                stage.show();

                DeviceController ctl = new DeviceController(bridge, new FakeHdc(), "SN99");
                CoordinateMapper mapper = new CoordinateMapper(1272, 2860);
                // 与 MirrorWindow 一致：挂在 ImageView 上，坐标无 padding/居中偏移
                new InputForwarder(ctl, mapper,
                        () -> view.getLayoutBounds().getWidth(),
                        () -> view.getLayoutBounds().getHeight(),
                        controlExecutor).attach(view);

                // 等一帧布局完成后，在画面中心按下
                Platform.runLater(() -> {
                    double cx = view.getLayoutBounds().getWidth() / 2;
                    double cy = view.getLayoutBounds().getHeight() / 2;
                    var press = new MouseEvent(MouseEvent.MOUSE_PRESSED, cx, cy, cx, cy,
                            MouseButton.PRIMARY, 1,
                            false, false, false, false,
                            true, false, false, true, false, true, null);
                    view.fireEvent(press);
                    var release = new MouseEvent(MouseEvent.MOUSE_RELEASED, cx, cy, cx, cy,
                            MouseButton.PRIMARY, 1,
                            false, false, false, false,
                            true, false, false, true, false, true, null);
                    view.fireEvent(release);
                });
            } finally {
                done.countDown();
            }
        });
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        // 控制调用走执行器异步串行，稍等再断言
        Thread.sleep(500);
        assertThat(StubJar.log()).contains("touchDown:");
    }
}
