package com.hnscrcpy.ui;

import com.hnscrcpy.device.DeviceInfo;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.function.Consumer;

/**
 * 设备列表面板（M1 最小版；M4 完善交互与设置）。
 */
public class MainView {

    private final BorderPane root = new BorderPane();
    private final ObservableList<DeviceInfo> devices = FXCollections.observableArrayList();
    private final ListView<DeviceInfo> deviceList = new ListView<>(devices);
    private final Label statusLabel = new Label("正在检测设备…");
    private final Consumer<DeviceInfo> onSelect;

    public MainView(Consumer<DeviceInfo> onSelect) {
        this.onSelect = onSelect;
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
        VBox header = new VBox(4, new Label("设备列表（双击投屏）"), statusLabel);
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

    public void showPlaceholder(DeviceInfo device) {
        statusLabel.setText(device.displayName() + " — 投屏功能将在 M2 接入");
    }
}
