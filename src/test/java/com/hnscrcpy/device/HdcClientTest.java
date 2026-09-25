package com.hnscrcpy.device;

import com.hnscrcpy.util.ProcessRunner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @Author: taxiao
 * WeChat：他晓
 * CSDN:http://blog.csdn.net/yin13753884368/article
 * Github:https://github.com/taxiao213
 */

class HdcClientTest {

    /** 脚本化 runner：按命令内容返回预设结果并记录命令。 */
    private static final class Scripted implements HdcClient.CommandRunner {
        final List<List<String>> commands = new ArrayList<>();

        @Override
        public ProcessRunner.Result run(List<String> command, long timeoutSec) {
            commands.add(command);
            String joined = String.join(" ", command);
            if (joined.endsWith("list targets")) {
                return new ProcessRunner.Result(0, "5KRUT25421010222\n", "", false, 10);
            }
            if (joined.contains("param get const.product.name")) {
                return new ProcessRunner.Result(0, "nova 14 Ultra\n", "", false, 10);
            }
            if (joined.contains("param get const.product.model")) {
                return new ProcessRunner.Result(0, "MRT-AL10\n", "", false, 10);
            }
            if (joined.contains("param get const.product.software.version")) {
                return new ProcessRunner.Result(0, "6.1.0.117\n", "", false, 10);
            }
            if (joined.contains("SP_daemon -screen")) {
                return new ProcessRunner.Result(0, "activeMode: 1272x2860\nrefreshRate=60\n", "", false, 10);
            }
            return new ProcessRunner.Result(0, "ok\n", "", false, 10);
        }
    }

    @Test
    @DisplayName("shell builds [-t sn shell cmd] and trims output")
    void shell_buildsCommand() {
        Scripted runner = new Scripted();
        HdcClient hdc = new HdcClient(Path.of("/opt/hdc"), runner);
        String out = hdc.shell("SN1", "uinput -K -d 1 -u 1");
        assertThat(out).isEqualTo("ok");
        assertThat(runner.commands.get(0)).containsExactly(
                "/opt/hdc", "-t", "SN1", "shell", "uinput -K -d 1 -u 1");
    }

    @Test
    @DisplayName("fport and fportRemove build correct arguments")
    void fport_buildsCommand() {
        Scripted runner = new Scripted();
        HdcClient hdc = new HdcClient(Path.of("/opt/hdc"), runner);
        assertThat(hdc.fport("SN1", 5000, "localabstract:scrcpy_grpc_socket")).isTrue();
        assertThat(runner.commands.get(0)).containsExactly(
                "/opt/hdc", "-t", "SN1", "fport", "tcp:5000", "localabstract:scrcpy_grpc_socket");
        assertThat(hdc.fportRemove("SN1", 5000)).isTrue();
        assertThat(runner.commands.get(1)).containsExactly(
                "/opt/hdc", "-t", "SN1", "fport", "rm", "tcp:5000");
    }

    @Test
    @DisplayName("fileSend builds file send command")
    void fileSend_buildsCommand() {
        Scripted runner = new Scripted();
        HdcClient hdc = new HdcClient(Path.of("/opt/hdc"), runner);
        assertThat(hdc.fileSend("SN1", Path.of("/tmp/a.so"), "/data/local/tmp/a.so")).isTrue();
        assertThat(runner.commands.get(0)).containsExactly(
                "/opt/hdc", "-t", "SN1", "file", "send", "/tmp/a.so", "/data/local/tmp/a.so");
    }

    @Test
    @DisplayName("describe fills model and os version from params")
    void describe_readsParams() {
        HdcClient hdc = new HdcClient(Path.of("/opt/hdc"), new Scripted());
        DeviceInfo info = hdc.describe("5KRUT25421010222");
        assertThat(info.model()).isEqualTo("nova 14 Ultra");
        assertThat(info.osVersion()).isEqualTo("6.1.0.117");
    }

    @Test
    @DisplayName("screenSize returns width/height/refreshRate triple")
    void screenSize_parsesTriple() {
        HdcClient hdc = new HdcClient(Path.of("/opt/hdc"), new Scripted());
        Optional<int[]> size = hdc.screenSize("SN1");
        assertThat(size).isPresent();
        assertThat(size.get()).containsExactly(1272, 2860, 60);
    }

    @Test
    @DisplayName("isOnline checks listTargets with retry")
    void isOnline_checksTargets() {
        Scripted runner = new Scripted();
        HdcClient hdc = new HdcClient(Path.of("/opt/hdc"), runner);
        assertThat(hdc.isOnline("5KRUT25421010222")).isTrue();
        assertThat(hdc.isOnline("OTHER")).isFalse();
    }
}
