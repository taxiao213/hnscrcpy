package com.hnscrcpy.control;

import com.hnscrcpy.device.HdcClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DeviceShell 通道选择：桥内优先、异常回退 hdc、无桥直连 hdc。
 *
 * @Author: taxiao
 * WeChat：他晓
 */
class DeviceShellTest {

    /** 记录命令的假 HdcClient。 */
    private static final class FakeHdc extends HdcClient {
        final List<String> commands = new ArrayList<>();

        FakeHdc() {
            super(Path.of("/nonexistent-hdc"));
        }

        @Override
        public String shell(String sn, String command, long timeoutSec) {
            commands.add(command);
            return "hdc-out";
        }
    }

    @Test
    @DisplayName("primary channel succeeds -> hdc untouched")
    void primarySuccess_hdcUntouched() {
        FakeHdc hdc = new FakeHdc();
        DeviceShell shell = new DeviceShell((cmd, t) -> "bridge-out:" + cmd, hdc, "SN1");

        String out = shell.exec("cmd1", 10);

        assertThat(out).isEqualTo("bridge-out:cmd1");
        assertThat(hdc.commands).isEmpty();
    }

    @Test
    @DisplayName("primary throws -> fallback to hdc")
    void primaryThrows_fallbackHdc() {
        FakeHdc hdc = new FakeHdc();
        DeviceShell shell = new DeviceShell((cmd, t) -> {
            throw new RuntimeException("bridge dead");
        }, hdc, "SN1");

        String out = shell.exec("cmd2", 10);

        assertThat(out).isEqualTo("hdc-out");
        assertThat(hdc.commands).containsExactly("cmd2");
    }

    @Test
    @DisplayName("null primary -> hdc only")
    void noPrimary_hdcOnly() {
        FakeHdc hdc = new FakeHdc();
        DeviceShell shell = DeviceShell.overHdc(hdc, "SN1");

        String out = shell.exec("cmd3", 10);

        assertThat(out).isEqualTo("hdc-out");
        assertThat(hdc.commands).containsExactly("cmd3");
    }

    @Test
    @DisplayName("primary returns null -> treated as empty output, no fallback")
    void primaryNull_becomesEmpty() {
        FakeHdc hdc = new FakeHdc();
        DeviceShell shell = new DeviceShell((cmd, t) -> null, hdc, "SN1");

        assertThat(shell.exec("cmd4", 10)).isEmpty();
        assertThat(hdc.commands).isEmpty();
    }
}
