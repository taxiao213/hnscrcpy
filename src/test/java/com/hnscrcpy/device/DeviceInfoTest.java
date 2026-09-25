package com.hnscrcpy.device;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @Author: taxiao
 * WeChat：他晓
 * CSDN:http://blog.csdn.net/yin13753884368/article
 * Github:https://github.com/taxiao213
 */

class DeviceInfoTest {

    @Test
    @DisplayName("type detects emulator by 127.0. prefix")
    void type_serialPrefix_classifiesEmulator() {
        assertThat(new DeviceInfo("127.0.0.1:5555", "", "").type())
                .isEqualTo(DeviceInfo.Type.EMULATOR);
        assertThat(new DeviceInfo("5KRUT25421010222", "", "").type())
                .isEqualTo(DeviceInfo.Type.REAL);
    }

    @Test
    @DisplayName("displayName falls back to serial when model blank")
    void displayName_blankModel_showsSerial() {
        assertThat(new DeviceInfo("SN123", "", "").displayName()).isEqualTo("SN123");
        assertThat(new DeviceInfo("SN123", "MRT-AL10", "").displayName())
                .isEqualTo("MRT-AL10  (SN123)");
    }
}
