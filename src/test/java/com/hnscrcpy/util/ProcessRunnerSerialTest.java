package com.hnscrcpy.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessRunnerSerialTest {

    @Test
    @DisplayName("hdc -t 命令提取序列号")
    void extractsSerialAfterT() {
        assertThat(ProcessRunner.serialFromCommand(
                List.of("/tools/hdc", "-t", "5KRUT25421010222", "shell", "param")))
                .isEqualTo("5KRUT25421010222");
    }

    @Test
    @DisplayName("无 -t 的命令返回 null")
    void returnsNullWithoutTarget() {
        assertThat(ProcessRunner.serialFromCommand(List.of("/tools/hdc", "list", "targets")))
                .isNull();
    }

    @Test
    @DisplayName("-t 位于末尾无值时返回 null")
    void returnsNullWhenTargetMissing() {
        assertThat(ProcessRunner.serialFromCommand(List.of("/tools/hdc", "-t"))).isNull();
    }
}
