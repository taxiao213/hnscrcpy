package com.hnscrcpy.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MainView.validateAddr 纯静态校验测试（不初始化 JavaFX 控件）。
 *
 * @Author: taxiao
 * WeChat：他晓
 * CSDN:http://blog.csdn.net/yin13753884368/article
 * Github:https://github.com/taxiao213
 */
class MainViewAddrTest {

    @Test
    @DisplayName("valid IPv4:port and hostname:port pass")
    void validAddresses() {
        assertThat(MainView.validateAddr("192.168.1.100:10123")).isNull();
        assertThat(MainView.validateAddr("10.0.0.1:1")).isNull();
        assertThat(MainView.validateAddr("phone.local:5555")).isNull();
    }

    @Test
    @DisplayName("missing host/port/separator rejected")
    void malformedAddresses() {
        assertThat(MainView.validateAddr("192.168.1.100")).isNotNull();
        assertThat(MainView.validateAddr(":10123")).isNotNull();
        assertThat(MainView.validateAddr("192.168.1.100:")).isNotNull();
        assertThat(MainView.validateAddr("")).isNotNull();
    }

    @Test
    @DisplayName("port range and format rejected")
    void portValidation() {
        assertThat(MainView.validateAddr("192.168.1.100:0")).isNotNull();
        assertThat(MainView.validateAddr("192.168.1.100:65536")).isNotNull();
        assertThat(MainView.validateAddr("192.168.1.100:abc")).isNotNull();
    }

    @Test
    @DisplayName("invalid host characters rejected")
    void hostValidation() {
        assertThat(MainView.validateAddr("19 2.168.1.1:8080")).isNotNull();
        assertThat(MainView.validateAddr("手机:8080")).isNotNull();
    }
}
