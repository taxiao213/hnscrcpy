package com.hnscrcpy.diag;

import com.hnscrcpy.diag.DiagnosticEvent.Type;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DiagnosticRegistryTest {

    @AfterEach
    void resetRegistry() {
        DiagnosticRegistry.resetForTest();
    }

    @Test
    @DisplayName("record 对 null/空 serial 静默忽略，不抛异常")
    void recordIgnoresBlankSerial() {
        DiagnosticRegistry.record(null, Type.STREAM_ERROR, "x");
        DiagnosticRegistry.record("", Type.STREAM_ERROR, "x");
        assertThat(DiagnosticRegistry.all()).isEmpty();
    }

    @Test
    @DisplayName("get 同一 serial 返回同一实例（幂等创建）")
    void getIsIdempotent() {
        var a = DiagnosticRegistry.get("SN-A", "devA");
        var b = DiagnosticRegistry.get("SN-A", "devB");
        assertThat(a).isSameAs(b);
        assertThat(b.deviceName()).isEqualTo("devA"); // 首建名称优先，updateName 才可覆盖
    }

    @Test
    @DisplayName("设备上线/下线事件带名称与状态切换")
    void deviceOnlineOfflineLifecycle() {
        DiagnosticRegistry.deviceOnline("SN-B", "nova 12");
        var d = DiagnosticRegistry.get("SN-B", null);
        assertThat(d.isOnline()).isTrue();
        assertThat(d.events()).extracting(DiagnosticEvent::type).contains(Type.DEVICE_ONLINE);
        assertThat(d.events()).extracting(DiagnosticEvent::detail).contains("nova 12");
        DiagnosticRegistry.deviceOffline("SN-B");
        assertThat(d.isOnline()).isFalse();
        assertThat(d.events()).extracting(DiagnosticEvent::type).contains(Type.DEVICE_OFFLINE);
    }

    @Test
    @DisplayName("重复上线不产生重复 DEVICE_ONLINE 事件")
    void repeatedOnlineIsSingleEvent() {
        DiagnosticRegistry.deviceOnline("SN-C", "dev");
        DiagnosticRegistry.deviceOnline("SN-C", "dev");
        long onlineEvents = DiagnosticRegistry.get("SN-C", null).events().stream()
                .filter(e -> e.type() == Type.DEVICE_ONLINE).count();
        assertThat(onlineEvents).isEqualTo(1);
    }

    @Test
    @DisplayName("all 保持插入序（先连接的设备在前）")
    void allKeepsInsertionOrder() {
        DiagnosticRegistry.get("SN-1", "a");
        DiagnosticRegistry.get("SN-2", "b");
        assertThat(DiagnosticRegistry.all()).extracting(DeviceDiagnostics::serial)
                .containsExactly("SN-1", "SN-2");
    }
}
