package com.hnscrcpy.ui;

import com.hnscrcpy.diag.DiagnosticExportService;
import com.hnscrcpy.diag.DiagnosticRegistry;
import com.hnscrcpy.device.DeviceInfo;
import com.hnscrcpy.stream.VideoConfig;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.Duration;

import java.awt.Desktop;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 设备列表面板：设备列表（行尾诊断徽标）+ 码率/帧率设置 + 诊断导出栏。
 *
 * @Author: taxiao
 * WeChat：他晓
 * CSDN:http://blog.csdn.net/yin13753884368/article
 * Github:https://github.com/taxiao213
 */

public class MainView {

    /** 设备列表样式：选中项深色胶囊（替代默认亮蓝），与投屏窗口同一套设计语言。 */
    private static final String LIST_CSS = """
            .device-list {
                -fx-background-color: #fbfbfc;
            }
            .device-list .list-cell {
                -fx-padding: 9 14 9 12;
                -fx-background-color: transparent;
                -fx-text-fill: #2c2c31;
            }
            .device-list .list-cell:filled:hover {
                -fx-background-color: #ececf2;
                -fx-background-radius: 8;
            }
            .device-list .list-cell:filled:selected {
                -fx-background-color: #2c2c31;
                -fx-text-fill: #f7f7f9;
                -fx-background-radius: 8;
            }
            /* 设备名在 graphic 的 Label 里，不吃 cell 的 text-fill；
               显式配选中色，否则失焦后默认深色字在深色胶囊上隐形 */
            .device-list .list-cell .device-name {
                -fx-text-fill: #2c2c31;
            }
            .device-list .list-cell:filled:selected .device-name {
                -fx-text-fill: #f7f7f9;
            }
            .diag-badge {
                -fx-background-color: #d9483b;
                -fx-text-fill: #ffffff;
                -fx-font-size: 11px;
                -fx-background-radius: 9;
                -fx-padding: 1 7 1 7;
                -fx-cursor: hand;
            }
            .diag-badge:pressed { -fx-background-color: #b23527; }
            """;

    private final BorderPane root = new BorderPane();
    private final ObservableList<DeviceInfo> devices = FXCollections.observableArrayList();
    private final ListView<DeviceInfo> deviceList = new ListView<>(devices);
    private final Label statusLabel = new Label("正在检测设备…");
    private final TextField bitRateField = new TextField("30");
    private final TextField fpsField = new TextField("60");
    private final Consumer<DeviceInfo> onSelect;

    public MainView(Consumer<DeviceInfo> onSelect) {
        this.onSelect = onSelect;
        deviceList.getStyleClass().add("device-list");
        root.getStylesheets().add(UiStyles.dataUrl(LIST_CSS));
        deviceList.setCellFactory(lv -> new ListCell<>() {
            private final Label name = new Label();
            {
                name.getStyleClass().add("device-name");
            }
            private final Label badge = new Label();
            private final HBox box = new HBox(8, name, spacer(), badge);

            {
                badge.getStyleClass().add("diag-badge");
                badge.setVisible(false);
                badge.setManaged(false);
                box.setAlignment(Pos.CENTER_LEFT);
                // 点击徽标 = 跳过对话框直接导出该设备；吞掉事件避免触发列表选中
                badge.setOnMouseClicked(e -> {
                    e.consume();
                    if (!isEmpty()) {
                        exportDiagnostics(Set.of(getItem().serial()));
                    }
                });
            }

            @Override
            protected void updateItem(DeviceInfo item, boolean empty) {
                super.updateItem(item, empty);
                boolean show = !empty && item != null;
                name.setText(show ? item.displayName() : null);
                setText(null);
                setGraphic(show ? box : null);
                var diag = show ? DiagnosticRegistry.get(item.serial(), item.model()) : null;
                int unread = diag == null ? 0 : diag.unreadErrors();
                badge.setVisible(unread > 0);
                badge.setManaged(unread > 0);
                badge.setText(unread + " 异常");
            }
        });
        deviceList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                DeviceInfo sel = deviceList.getSelectionModel().getSelectedItem();
                if (sel != null) {
                    onSelect.accept(sel);
                }
            }
        });
        // 徽标数字随诊断事件变化；轻量轮询刷新列表渲染
        Timeline badgeRefresher = new Timeline(new KeyFrame(Duration.seconds(2), ev ->
                deviceList.refresh()));
        badgeRefresher.setCycleCount(Timeline.INDEFINITE);
        badgeRefresher.play();

        bitRateField.setPrefColumnCount(4);
        fpsField.setPrefColumnCount(4);
        HBox settings = new HBox(8, new Label("码率(Mbps):"), bitRateField,
                new Label("帧率:"), fpsField);
        settings.setAlignment(Pos.CENTER_LEFT);
        // header 已有统一的 12px 外边距，这里只留垂直间距，保证与上面两行左对齐
        settings.setPadding(new Insets(8, 0, 8, 0));
        VBox header = new VBox(4, new Label("设备列表（双击投屏）"), statusLabel, settings);
        header.setPadding(new Insets(12));

        Button exportBtn = new Button("📤 导出诊断日志");
        exportBtn.setOnAction(e -> showExportDialog());
        Button openLogsBtn = new Button("📁 打开日志文件夹");
        openLogsBtn.setOnAction(e -> openLogsDir());
        HBox bottom = new HBox(10, exportBtn, openLogsBtn);
        bottom.setPadding(new Insets(10, 12, 10, 12));
        bottom.setAlignment(Pos.CENTER_LEFT);

        root.setTop(header);
        root.setCenter(deviceList);
        root.setBottom(bottom);
    }

    private static Region spacer() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    public Parent getRoot() {
        return root;
    }

    public void setDevices(List<DeviceInfo> list) {
        devices.setAll(list);
        statusLabel.setText(list.isEmpty() ? "未发现设备，请用 hdc 连接后重试" : "已连接 " + list.size() + " 台设备");
    }

    /** 按界面设置构建视频配置；非法输入回落默认。 */
    public VideoConfig videoConfig() {
        VideoConfig cfg = VideoConfig.defaults();
        try {
            cfg = cfg.withBitRate(Integer.parseInt(bitRateField.getText().trim()));
        } catch (NumberFormatException ignored) {
            // 保留默认
        }
        try {
            cfg = cfg.withFps(Integer.parseInt(fpsField.getText().trim()));
        } catch (NumberFormatException ignored) {
            // 保留默认
        }
        return cfg;
    }

    public void applyConfig(int bitRateMbps, int fps) {
        bitRateField.setText(String.valueOf(bitRateMbps));
        fpsField.setText(String.valueOf(fps));
    }

    // ---- 诊断导出 ----

    /** 导出对话框样式：边距、字号与设备列表一致（13px），同一套设计语言。 */
    private static final String DIALOG_CSS = """
            .diag-dialog {
                -fx-background-color: #fbfbfc;
            }
            .diag-dialog .header-panel {
                -fx-background-color: #fbfbfc;
            }
            .diag-dialog .header-panel .label {
                -fx-font-size: 13px;
                -fx-text-fill: #2c2c31;
            }
            .diag-dialog .content {
                -fx-font-size: 13px;
                -fx-text-fill: #2c2c31;
            }
            .diag-dialog .radio {
                -fx-font-size: 13px;
                -fx-text-fill: #2c2c31;
                -fx-padding: 4 8 4 8;
                -fx-background-radius: 6;
            }
            .diag-dialog .radio:hover {
                -fx-background-color: #ececf2;
            }
            /* 单选框结构：.radio-button > .radio（圆圈） > .dot（选中点）。
               注意圆圈类名是 .radio 而非 .box（.box 是 CheckBox 的）；:selected 在 .radio-button 根上 */
            .diag-dialog .radio-button > .radio {
                -fx-background-color: #ffffff;
                -fx-border-color: #b0b0b8;
                -fx-border-width: 1;
                -fx-border-radius: 100;
                -fx-background-radius: 100;
                -fx-min-width: 16;
                -fx-min-height: 16;
                -fx-max-width: 16;
                -fx-max-height: 16;
                -fx-background-insets: 0;
                -fx-padding: 0;
            }
            .diag-dialog .radio > .dot {
                -fx-background-color: transparent;
                -fx-background-insets: 0;
                -fx-background-radius: 100;
                -fx-min-width: 16;
                -fx-min-height: 16;
                -fx-max-width: 16;
                -fx-max-height: 16;
            }
            .diag-dialog .radio-button:selected > .radio {
                -fx-border-color: #2c2c31;
            }
            .diag-dialog .radio-button:selected .dot {
                -fx-background-color: #2c2c31;
                -fx-background-insets: 4;
                -fx-background-radius: 100;
            }
            /* 仅默认按钮（确定）用关于页同款深色胶囊；取消保持默认样式 */
            .diag-dialog .button:default {
                -fx-background-color: #2c2c31;
                -fx-text-fill: #f7f7f9;
                -fx-background-radius: 8;
                -fx-padding: 6 22 6 22;
                -fx-cursor: hand;
            }
            .diag-dialog .button:default:hover { -fx-background-color: #45454d; }
            .diag-dialog .button:default:pressed { -fx-background-color: #1e1e22; }
            """;

    /** 分设备导出对话框：列出本应用运行期所有有过记录的设备，可多选或全选。 */
    private void showExportDialog() {
        var entries = DiagnosticRegistry.all();
        if (entries.isEmpty()) {
            statusLabel.setText("暂无诊断记录");
            return;
        }
        Dialog<Set<String>> dialog = new Dialog<>();
        dialog.setTitle("导出诊断日志");
        dialog.setHeaderText("选择要导出的设备（包含该设备的异常事件记录与运行日志）");
        var pane = dialog.getDialogPane();
        pane.getStyleClass().add("diag-dialog");
        pane.getStylesheets().add(UiStyles.dataUrl(DIALOG_CSS));
        pane.setMinWidth(400);
        pane.setPadding(new Insets(8, 12, 8, 12));
        ToggleGroup group = new ToggleGroup();
        VBox box = new VBox(6);
        box.setPadding(new Insets(10, 6, 10, 6));
        for (var d : entries) {
            String text = d.deviceName() + "  (" + d.serial() + ")"
                    + (d.isOnline() ? "  · 在线" : "  · 离线")
                    + (d.unreadErrors() > 0 ? "  · " + d.unreadErrors() + " 条未读异常" : "");
            RadioButton rb = new RadioButton(text);
            rb.setToggleGroup(group);
            rb.setUserData(d.serial());
            box.getChildren().add(rb);
        }
        RadioButton all = new RadioButton("全部设备（合并导出）");
        all.setToggleGroup(group);
        all.setUserData("ALL");
        box.getChildren().add(all);
        group.getToggles().get(0).setSelected(true);
        pane.setContent(box);
        pane.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.setResultConverter(bt -> {
            if (bt.getButtonData() != ButtonBar.ButtonData.OK_DONE
                    || group.getSelectedToggle() == null) {
                return null;
            }
            String v = (String) group.getSelectedToggle().getUserData();
            if ("ALL".equals(v)) {
                Set<String> all_ = new java.util.HashSet<>();
                for (var t : group.getToggles()) {
                    if (t.getUserData() instanceof String s && !"ALL".equals(s)) {
                        all_.add(s);
                    }
                }
                return all_;
            }
            return Set.of(v);
        });
        var owner = root.getScene() == null ? null : root.getScene().getWindow();
        if (owner != null) {
            dialog.initOwner(owner);
        }
        dialog.showAndWait().ifPresent(this::exportDiagnostics);
    }

    /** 执行导出：FileChooser 选位置 → 打包 zip → 状态栏反馈。 */
    private void exportDiagnostics(Set<String> serials) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("导出诊断日志");
        chooser.setInitialFileName(DiagnosticExportService.suggestedFileName());
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Zip 压缩包", "*.zip"));
        var owner = root.getScene() == null ? null : root.getScene().getWindow();
        var chosen = chooser.showSaveDialog(owner);
        if (chosen == null) {
            return;
        }
        Path target = chosen.toPath();
        try {
            Path saved = DiagnosticExportService.export(serials, target);
            statusLabel.setText("诊断日志已导出: " + saved);
        } catch (Exception ex) {
            statusLabel.setText("导出失败: " + ex.getMessage());
            new Alert(Alert.AlertType.ERROR, "导出失败: " + ex.getMessage()).showAndWait();
        }
    }

    private void openLogsDir() {
        Path dir = Paths.get(System.getProperty("user.home"), ".hnscrcpy", "logs");
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(dir.toFile());
            } else {
                statusLabel.setText("请手动打开: " + dir);
            }
        } catch (Exception ex) {
            statusLabel.setText("打开日志文件夹失败: " + ex.getMessage());
        }
    }
}
