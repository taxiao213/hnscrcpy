package com.hnscrcpy.control;

import com.hnscrcpy.device.HdcClient;
import com.hnscrcpy.device.StubJar;
import com.hnscrcpy.stream.HosScrcpyBridge;
import com.hnscrcpy.stream.VideoConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeviceControllerTest {

    @TempDir
    static Path dir;
    private static HosScrcpyBridge bridge;

    /** 记录 shell 命令的假 HdcClient。 */
    private static final class FakeHdc extends HdcClient {
        final List<String> commands = new ArrayList<>();

        FakeHdc() {
            super(Path.of("/nonexistent-hdc"));
        }

        @Override
        public String shell(String sn, String command) {
            commands.add(command);
            return "ok";
        }
    }

    @BeforeAll
    static void openBridge() throws Exception {
        bridge = HosScrcpyBridge.open(StubJar.create(dir), "SN99", "/opt/hdc", VideoConfig.defaults());
    }

    @BeforeEach
    void clearLog() {
        StubJar.clearLog();
    }

    @Test
    @DisplayName("touch/mouse/wheel/rotation calls reach the device stub")
    void controlMethods_reachStub() {
        FakeHdc hdc = new FakeHdc();
        DeviceController ctl = new DeviceController(bridge, hdc, "SN99");

        ctl.touchDown(10, 20);
        ctl.touchMove(11, 21);
        ctl.touchUp(12, 22);
        ctl.mouseDown(DeviceController.MOUSE_RIGHT, 1, 2);
        ctl.mouseMove(DeviceController.MOUSE_RIGHT, 2, 3);
        ctl.mouseUp(DeviceController.MOUSE_RIGHT, 3, 4);
        ctl.mouseWheelUp(1, 1);
        ctl.mouseWheelDown(1, 1);
        ctl.mouseWheelStop(1, 1);
        ctl.setRotationHorizontal();
        ctl.setRotationVertical();

        String log = StubJar.log();
        assertThat(log)
                .contains("touchDown:10,20")
                .contains("touchMove:11,21")
                .contains("touchUp:12,22")
                .contains("mouseDown:mouseRight:1,2")
                .contains("mouseMove:mouseRight")
                .contains("mouseUp:mouseRight")
                .contains("wheelUp").contains("wheelDown").contains("wheelStop")
                .contains("rotH").contains("rotV");
    }

    @Test
    @DisplayName("keys are sent through uinput shell with verified keycodes")
    void keys_useUinputKeycodes() {
        FakeHdc hdc = new FakeHdc();
        DeviceController ctl = new DeviceController(bridge, hdc, "SN99");

        ctl.keyHome();
        ctl.keyBack();
        ctl.keyRecentTasks();
        ctl.keyPower();
        ctl.keyVolumeUp();
        ctl.keyVolumeDown();

        assertThat(hdc.commands).containsExactly(
                "uinput -K -d 1 -u 1",
                "uinput -K -d 2 -u 2",
                "uinput -K -d 2720 -u 2720",
                "uinput -K -d 18 -u 18",
                "uinput -K -d 16 -u 16",
                "uinput -K -d 17 -u 17");
    }
}
