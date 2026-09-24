package com.hnscrcpy.ui;

import com.hnscrcpy.device.DeviceInfo;
import com.hnscrcpy.stream.VideoConfig;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.function.Consumer;

/**
 * 设备列表面板：设备列表 + 码率/帧率设置。
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
            @Override
            protected void updateItem(DeviceInfo item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.displayName());
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
        bitRateField.setPrefColumnCount(4);
        fpsField.setPrefColumnCount(4);
        HBox settings = new HBox(8, new Label("码率(Mbps):"), bitRateField,
                new Label("帧率:"), fpsField);
        settings.setAlignment(Pos.CENTER_LEFT);
        settings.setPadding(new Insets(8, 12, 8, 12));
        VBox header = new VBox(4, new Label("设备列表（双击投屏）"), statusLabel, settings);
        header.setPadding(new Insets(12));
        root.setTop(header);
        root.setCenter(deviceList);
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
}
