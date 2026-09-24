package com.hnscrcpy;

import com.hnscrcpy.device.DeviceInfo;
import com.hnscrcpy.device.DeviceMonitor;
import com.hnscrcpy.device.HdcClient;
import com.hnscrcpy.stream.VideoConfig;
import com.hnscrcpy.ui.MainView;
import com.hnscrcpy.ui.MirrorWindow;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * hnscrcpy 入口。双击设备启动投屏会话（M2 起接入）。
 */
public class App extends Application {

    private static final Logger log = LoggerFactory.getLogger(App.class);

    private DeviceMonitor monitor;
    private MainView mainView;

    @Override
    public void start(Stage stage) {
        HdcClient hdc = new HdcClient();
        mainView = new MainView(this::onDeviceSelected);
        monitor = new DeviceMonitor(hdc, this::onDevicesChanged);
        monitor.start();

        stage.setTitle("hnscrcpy — 鸿蒙 NEXT 投屏");
        stage.setScene(new Scene(mainView.getRoot(), 420, 360));
        stage.setOnCloseRequest(e -> monitor.stop());
        stage.show();
    }

    private void onDevicesChanged(List<DeviceInfo> devices) {
        javafx.application.Platform.runLater(() -> mainView.setDevices(devices));
    }

    private void onDeviceSelected(DeviceInfo device) {
        log.info("opening mirror for {}", device.displayName());
        new MirrorWindow().open(device, VideoConfig.defaults());
    }

    public static void main(String[] args) {
        launch(args);
    }
}
