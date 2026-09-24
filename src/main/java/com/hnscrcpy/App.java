package com.hnscrcpy;

import com.hnscrcpy.cli.CliOptions;
import com.hnscrcpy.device.DeviceInfo;
import com.hnscrcpy.device.DeviceMonitor;
import com.hnscrcpy.device.HdcClient;
import com.hnscrcpy.stream.VideoConfig;
import com.hnscrcpy.ui.MainView;
import com.hnscrcpy.ui.MirrorWindow;
import com.hnscrcpy.util.AppConfig;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * hnscrcpy 入口。双击设备启动投屏会话；支持 CLI 自动连接（--serial）。
 */
public class App extends Application {

    private static final Logger log = LoggerFactory.getLogger(App.class);
    public static final String VERSION = "1.0.0";
    private static CliOptions cliOptions = CliOptions.defaults();

    private DeviceMonitor monitor;
    private MainView mainView;
    private AppConfig config;
    private boolean noControl;
    private boolean autoConnectDone;

    @Override
    public void start(Stage stage) {
        config = AppConfig.load();
        applyCliToConfig();

        HdcClient hdc = new HdcClient();
        mainView = new MainView(this::onDeviceSelected);
        mainView.applyConfig(config.bitRateMbps(), config.fps());
        monitor = new DeviceMonitor(hdc, this::onDevicesChanged);
        monitor.start();

        stage.setTitle("hnscrcpy — 鸿蒙 NEXT 投屏");
        stage.setScene(new Scene(mainView.getRoot(), 420, 400));
        stage.setOnCloseRequest(e -> monitor.stop());
        stage.show();
    }

    private void applyCliToConfig() {
        if (cliOptions.bitRate() != null) {
            config.setBitRateMbps(cliOptions.bitRate());
        }
        if (cliOptions.fps() != null) {
            config.setFps(cliOptions.fps());
        }
        if (cliOptions.serial() != null) {
            config.setLastSerial(cliOptions.serial());
        }
        noControl = cliOptions.noControl() || config.noControl();
    }

    private void onDevicesChanged(List<DeviceInfo> devices) {
        javafx.application.Platform.runLater(() -> {
            mainView.setDevices(devices);
            String want = cliOptions.serial() != null ? cliOptions.serial() : config.lastSerial();
            if (!autoConnectDone && want != null && !want.isEmpty()) {
                devices.stream().filter(d -> d.serial().equals(want)).findFirst()
                        .ifPresent(d -> {
                            autoConnectDone = true;
                            onDeviceSelected(d);
                        });
            }
        });
    }

    private void onDeviceSelected(DeviceInfo device) {
        log.info("opening mirror for {}", device.displayName());
        config.setLastSerial(device.serial());
        config.setBitRateMbps(mainView.videoConfig().bitRateMbps());
        config.setFps(mainView.videoConfig().fps());
        config.save();
        new MirrorWindow().open(device, mainView.videoConfig(), noControl);
    }

    public static void main(String[] args) {
        try {
            cliOptions = CliOptions.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.err.println(CliOptions.usage());
            System.exit(2);
        }
        if (cliOptions.help()) {
            System.out.println(CliOptions.usage());
            System.exit(0);
        }
        if (cliOptions.version()) {
            System.out.println("hnscrcpy " + VERSION);
            System.exit(0);
        }
        launch(args);
    }
}
