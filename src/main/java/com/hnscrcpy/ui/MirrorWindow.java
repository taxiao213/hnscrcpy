package com.hnscrcpy.ui;

import com.hnscrcpy.control.InputForwarder;
import com.hnscrcpy.device.DeviceInfo;
import com.hnscrcpy.session.MirrorSession;
import com.hnscrcpy.stream.VideoConfig;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 投屏窗口：深色背景 + 圆角阴影画面（居中）+ 右侧悬浮竖排工具条 + 底部细状态栏。
 * 样式对标 DevEco Testing 远控面板；快捷键见 README（⌘ 在 Windows/Linux 为 Ctrl）。
 */
public final class MirrorWindow {

    private static final Logger log = LoggerFactory.getLogger(MirrorWindow.class);

    private static final Color BG = Color.web("#1b1b1f");
    private static final Color STATUS_BG = Color.web("#121215");
    private static final Color STATUS_FG = Color.web("#9a9aa2");
    private static final double SCREEN_RADIUS = 32;

    private final Stage stage = new Stage();
    private final Label statusBar = new Label("初始化…");
    private MirrorSession session;
    private DeviceInfo device;
    private boolean alwaysOnTop = false;

    public void open(DeviceInfo device, VideoConfig config, boolean controlEnabled) {
        this.device = device;
        session = new MirrorSession(device.serial(), config, msg ->
                javafx.application.Platform.runLater(() -> statusBar.setText(msg)));

        ImageView view = session.getView();
        view.setPreserveRatio(true);
        // 圆角裁剪 + 投影，营造设备悬浮感
        Rectangle clip = new Rectangle();
        clip.setArcWidth(SCREEN_RADIUS);
        clip.setArcHeight(SCREEN_RADIUS);
        clip.widthProperty().bind(view.layoutBoundsProperty()
                .map(b -> b.getWidth()));
        clip.heightProperty().bind(view.layoutBoundsProperty()
                .map(b -> b.getHeight()));
        view.setClip(clip);
        DropShadow shadow = new DropShadow();
        shadow.setRadius(18);
        shadow.setOffsetY(6);
        shadow.setColor(Color.rgb(0, 0, 0, 0.55));
        view.setEffect(shadow);

        StackPane screenHolder = new StackPane(view);
        screenHolder.setPadding(new Insets(18, 10, 14, 18));
        // 关键：钉死 min/pref，切断「ImageView 当前大小 → 父级 pref → 更多空间分配」
        // 正反馈，否则窗口逐帧无限放大（实测每脉冲 +512px）
        screenHolder.setMinSize(0, 0);
        screenHolder.setPrefSize(0, 0);
        view.fitWidthProperty().bind(screenHolder.widthProperty());
        view.fitHeightProperty().bind(screenHolder.heightProperty());

        SideToolbar toolbar = new SideToolbar(buildActions());
        StackPane toolbarHolder = new StackPane(toolbar);
        toolbarHolder.setPadding(new Insets(2, 18, 2, 0));
        toolbarHolder.setMinWidth(64);
        toolbarHolder.setMaxWidth(64);

        // 画面卡片与工具条卡片之间留出间隙：两个独立悬浮框的观感
        HBox content = new HBox(screenHolder, toolbarHolder);
        content.setAlignment(Pos.CENTER);
        content.setSpacing(18);
        HBox.setHgrow(screenHolder, Priority.ALWAYS);
        content.setBackground(new Background(new BackgroundFill(BG, CornerRadii.EMPTY, Insets.EMPTY)));

        statusBar.setTextFill(STATUS_FG);
        statusBar.setPadding(new Insets(5, 12, 5, 12));
        BorderPane root = new BorderPane();
        root.setCenter(content);
        root.setBottom(statusBar);
        statusBar.setBackground(new Background(new BackgroundFill(STATUS_BG, CornerRadii.EMPTY, Insets.EMPTY)));

        Scene scene = new Scene(root, 520, 920);
        scene.getStylesheets().add(toolbarStylesheetUrl());
        stage.setTitle("hnscrcpy — " + device.displayName());
        stage.setMinWidth(360);
        stage.setMinHeight(560);
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

        if (controlEnabled) {
            // 挂在 ImageView 上：事件坐标即画面坐标，映射无 padding/居中偏移
            new InputForwarder(session.controller(), session.mapper(),
                    () -> view.getLayoutBounds().getWidth(),
                    () -> view.getLayoutBounds().getHeight(),
                    session.controlExecutor()).attach(view);
            // 诊断：scene 级过滤器观察事件实际到达的目标节点
            scene.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, e ->
                    log.debug("scene press target={} at ({},{})",
                            e.getTarget().getClass().getSimpleName(), e.getSceneX(), e.getSceneY()));
            log.debug("InputForwarder attached to view");
        } else {
            log.warn("mirror window opened WITHOUT control (InputForwarder not attached)");
        }
        registerAccelerators(scene, toolbar);
        stage.show();
    }

    private SideToolbar.Actions buildActions() {
        return new SideToolbar.Actions(
                stage::close,
                this::onScreenshot,
                this::onRotate,
                () -> run(() -> session.controller().keyPower()),
                () -> run(() -> session.controller().keyVolumeUp()),
                () -> run(() -> session.controller().keyVolumeDown()),
                () -> run(() -> session.controller().keyBack()),
                () -> run(() -> session.controller().keyHome()),
                () -> run(() -> session.controller().keyRecentTasks()),
                this::onToggleTop,
                this::onAbout);
    }

    private void registerAccelerators(Scene scene, SideToolbar toolbar) {
        for (var entry : toolbar.menuEntries()) {
            if (entry.shortcut() != null) {
                scene.getAccelerators().put(entry.shortcut(), entry.action());
            }
        }
    }

    private void run(Runnable action) {
        if (session != null) {
            session.controlExecutor().execute(action);
        }
    }

    private void onRotate() {
        run(() -> {
            if (session.mapper().isHorizontal()) {
                session.controller().setRotationVertical();
                session.mapper().setHorizontal(false);
            } else {
                session.controller().setRotationHorizontal();
                session.mapper().setHorizontal(true);
            }
        });
        // 窗口跟随旋转：按当前画面显示尺寸交换宽高，画面物理大小不变。
        // 多出的空间只会分配给 screenHolder（唯一 hgrow），横竖切换后画面依然填满。
        javafx.application.Platform.runLater(() -> {
            var b = session.getView().getLayoutBounds();
            if (b.getWidth() > 0 && b.getHeight() > 0) {
                double delta = b.getHeight() - b.getWidth();
                stage.setWidth(stage.getWidth() + delta);
                stage.setHeight(stage.getHeight() - delta);
            }
        });
    }

    private void onToggleTop() {
        alwaysOnTop = !alwaysOnTop;
        stage.setAlwaysOnTop(alwaysOnTop);
        statusBar.setText(alwaysOnTop ? "窗口已置顶" : "已取消置顶");
    }

    private void onScreenshot() {
        if (session == null) {
            return;
        }
        session.controlExecutor().execute(() -> {
            String msg;
            try {
                var path = session.saveScreenshot();
                msg = "截图已保存: " + path;
            } catch (Exception ex) {
                msg = "截图失败: " + ex.getMessage();
            }
            String finalMsg = msg;
            javafx.application.Platform.runLater(() -> statusBar.setText(finalMsg));
        });
    }

    private void onAbout() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("关于 hnscrcpy");
        alert.setHeaderText("hnscrcpy " + com.hnscrcpy.App.VERSION);
        // displayName 已包含序列号，不再重复拼接
        String deviceText = device != null ? device.displayName() : "未连接";
        alert.setContentText("鸿蒙 NEXT 投屏远控工具\n设备: " + deviceText
                + "\n\n视频源: hosScrcpy H.264 流 · avcodec 直解渲染"
                + "\n\n作者: 微信公众号「他晓」\nGitHub: https://github.com/taxiao213");
        alert.initOwner(stage);
        // 弹窗样式与主窗口一致（默认 Modena 蓝按钮与整体风格不搭）
        var pane = alert.getDialogPane();
        pane.getStyleClass().add("about-dialog");
        pane.getStylesheets().add(toolbarStylesheetUrl());
        alert.showAndWait();
    }

    /**
     * 工具条样式表的 data: URL。URLEncoder 把空格编成 '+'，而 data: URL 中 '+' 是字面量，
     * 会导致整条 CSS 解析失败（样式静默丢失）——必须再替换回 %20。包可见便于离屏快照验证。
     */
    static String toolbarStylesheetUrl() {
        return "data:text/css," + URLEncoder.encode(toolbarCss(), StandardCharsets.UTF_8)
                .replace("+", "%20");
    }

    /** 工具条 + 菜单样式；包可见便于离屏快照验证。 */
    static String toolbarCss() {
        return """
                .tool-pill {
                    -fx-background-color: #f7f7f9;
                    -fx-background-radius: 18;
                    -fx-padding: 5 7 5 7;
                    -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.45), 16, 0, 0, 4);
                }
                .tool-btn {
                    -fx-background-color: transparent;
                    -fx-background-radius: 9;
                    -fx-cursor: hand;
                    -fx-padding: 6;
                }
                .tool-btn:hover { -fx-background-color: #e8e8ee; }
                .tool-btn:pressed { -fx-background-color: #d9d9e2; }
                .tool-sep { -fx-background-color: #dedee5; }
                .menu-panel {
                    -fx-background-color: #fbfbfc;
                    -fx-background-radius: 12;
                    -fx-padding: 8;
                    -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.28), 18, 0, 0, 4);
                    -fx-border-color: #e4e4ea;
                    -fx-border-radius: 12;
                }
                .menu-row {
                    -fx-padding: 9 14 9 12;
                    -fx-cursor: hand;
                    -fx-min-width: 230;
                    -fx-background-radius: 8;
                }
                .menu-row:hover { -fx-background-color: #ececf2; }
                .menu-label { -fx-text-fill: #2c2c31; -fx-font-size: 13; }
                .menu-shortcut { -fx-text-fill: #9a9aa2; -fx-font-size: 12; }
                .menu-sep { -fx-background-color: #ececf0; }
                .about-dialog { -fx-background-color: #fbfbfc; }
                .about-dialog .button {
                    -fx-background-color: #2c2c31;
                    -fx-text-fill: #f7f7f9;
                    -fx-background-radius: 8;
                    -fx-padding: 6 22 6 22;
                    -fx-cursor: hand;
                }
                .about-dialog .button:hover { -fx-background-color: #45454d; }
                .about-dialog .button:pressed { -fx-background-color: #1e1e22; }
                """;
    }

    public void closeSession() {
        if (session != null) {
            session.close();
            session = null;
        }
    }
}
