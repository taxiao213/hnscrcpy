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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * hnscrcpy 入口。双击设备启动投屏会话；支持 CLI 自动连接（--serial）。
 *
 * @Author: taxiao
 * WeChat：他晓
 * CSDN:http://blog.csdn.net/yin13753884368/article
 * Github:https://github.com/taxiao213
 */

public class App extends Application {

    private static final Logger log = LoggerFactory.getLogger(App.class);
    public static final String VERSION = "1.0.1";
    private static CliOptions cliOptions = CliOptions.defaults();

    private DeviceMonitor monitor;
    private MainView mainView;
    private AppConfig config;
    private boolean noControl;
    private boolean autoConnectDone;
    /** serial → 已打开的投屏窗口；同设备只允许一个会话（设备侧 scrcpy 单例）。 */
    private final Map<String, MirrorWindow> mirrorWindows = new HashMap<>();

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
            // 仅当 CLI 显式指定 --serial 时自动投屏；普通使用一律由用户双击设备发起，
            // 避免打开 app 就自动连上上次的设备
            String want = cliOptions.serial();
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
        // 设备侧 scrcpy 是单例：同设备第二个会话会把第一个踢断流，两边陷入重连互踢。
        // 已有同设备投屏窗口时只聚焦，不再开会话
        MirrorWindow existing = mirrorWindows.get(device.serial());
        if (existing != null) {
            existing.requestFocus();
            return;
        }
        MirrorWindow window = new MirrorWindow();
        mirrorWindows.put(device.serial(), window);
        window.setOnClosed(() -> mirrorWindows.remove(device.serial()));
        window.open(device, mainView.videoConfig(), !noControl);
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
