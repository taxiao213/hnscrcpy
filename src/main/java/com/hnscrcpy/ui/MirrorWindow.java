package com.hnscrcpy.ui;

import com.hnscrcpy.control.InputForwarder;
import com.hnscrcpy.device.DeviceInfo;
import com.hnscrcpy.session.MirrorSession;
import com.hnscrcpy.stream.VideoConfig;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 投屏窗口（M3：画面 + 状态栏 + 鼠标/滚轮/按键转发；M4 增加工具栏与设置）。
 */
public final class MirrorWindow {

    private static final Logger log = LoggerFactory.getLogger(MirrorWindow.class);

    private final Stage stage = new Stage();
    private final Label statusBar = new Label("初始化…");
    private MirrorSession session;

    public void open(DeviceInfo device, VideoConfig config) {
        session = new MirrorSession(device.serial(), config, msg ->
                javafx.application.Platform.runLater(() -> statusBar.setText(msg)));

        ImageView view = session.getView();
        view.setPreserveRatio(true);
        StackPane center = new StackPane(view);
        view.fitWidthProperty().bind(center.widthProperty());
        view.fitHeightProperty().bind(center.heightProperty());

        BorderPane root = new BorderPane();
        root.setCenter(center);
        statusBar.setPadding(new Insets(4, 8, 4, 8));
        root.setBottom(statusBar);

        Scene scene = new Scene(root, 420, 760);
        stage.setTitle("hnscrcpy — " + device.displayName());
        stage.setScene(scene);
        stage.setOnCloseRequest(e -> closeSession());

        try {
            session.start();
        } catch (RuntimeException e) {
            log.error("session start failed", e);
            statusBar.setText("启动失败: " + e.getMessage());
            stage.show();
            return;
        }

        new InputForwarder(session.controller(), session.mapper(),
                center::getWidth, center::getHeight, session.controlExecutor()).attach(center);
        scene.setOnKeyPressed(e -> onKey(e.getCode()));
        stage.show();
    }

    private void onKey(KeyCode code) {
        if (session == null) {
            return;
        }
        var exec = session.controlExecutor();
        switch (code) {
            case H -> exec.execute(() -> session.controller().keyHome());
            case B -> exec.execute(() -> session.controller().keyBack());
            case R -> exec.execute(() -> session.controller().keyRecentTasks());
            case P -> exec.execute(() -> session.controller().keyPower());
            default -> {
                // 其他键不处理
            }
        }
    }

    public void closeSession() {
        if (session != null) {
            session.close();
            session = null;
        }
    }
}
