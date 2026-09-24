package com.hnscrcpy.ui;

import com.hnscrcpy.device.DeviceInfo;
import com.hnscrcpy.session.MirrorSession;
import com.hnscrcpy.stream.VideoConfig;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 投屏窗口（M2 最小版：画面 + 状态栏；M4 增加工具栏/快捷键/旋转适配）。
 */
public final class MirrorWindow {

    private static final Logger log = LoggerFactory.getLogger(MirrorWindow.class);

    private final Stage stage = new Stage();
    private final Label statusBar = new Label("初始化…");
    private MirrorSession session;

    public void open(DeviceInfo device, VideoConfig config) {
        session = new MirrorSession(device.serial(), config, msg ->
                javafx.application.Platform.runLater(() -> statusBar.setText(msg)));
        BorderPane root = new BorderPane();
        root.setCenter(session.getView());
        statusBar.setPadding(new Insets(4, 8, 4, 8));
        root.setBottom(statusBar);

        stage.setTitle("hnscrcpy — " + device.displayName());
        stage.setScene(new Scene(root, 420, 760));
        stage.setOnCloseRequest(e -> closeSession());
        try {
            session.start();
        } catch (RuntimeException e) {
            log.error("session start failed", e);
            statusBar.setText("启动失败: " + e.getMessage());
        }
        stage.show();
    }

    public void closeSession() {
        if (session != null) {
            session.close();
            session = null;
        }
    }
}
