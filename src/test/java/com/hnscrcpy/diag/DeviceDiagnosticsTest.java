package com.hnscrcpy.diag;

import com.hnscrcpy.diag.DiagnosticEvent.Type;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class DeviceDiagnosticsTest {

    private static DiagnosticEvent event(Type type) {
        return new DiagnosticEvent(Instant.now(), type, "detail");
    }

    @Test
    @DisplayName("环形缓冲满后丢最旧，容量恒为 500")
    void ringCapacityDropsOldest() {
        DeviceDiagnostics d = new DeviceDiagnostics("SN1", "dev");
        for (int i = 0; i < DeviceDiagnostics.CAPACITY + 100; i++) {
            d.add(event(Type.DECODER_CREATED));
        }
        assertThat(d.events()).hasSize(DeviceDiagnostics.CAPACITY);
    }

    @Test
    @DisplayName("未读异常只计 error 类事件，markRead 清零")
    void unreadErrorsCountAndReset() {
        DeviceDiagnostics d = new DeviceDiagnostics("SN1", "dev");
        d.add(event(Type.STREAM_ERROR));
        d.add(event(Type.WATCHDOG_STALL));
        d.add(event(Type.RECONNECTED)); // 非 error 不计
        assertThat(d.unreadErrors()).isEqualTo(2);
        d.markRead();
        assertThat(d.unreadErrors()).isZero();
    }

    @Test
    @DisplayName("事件按时间升序保留（FIFO）")
    void eventsKeptInOrder() {
        DeviceDiagnostics d = new DeviceDiagnostics("SN1", "dev");
        DiagnosticEvent first = new DiagnosticEvent(Instant.ofEpochSecond(1), Type.DEVICE_ONLINE, null);
        DiagnosticEvent second = new DiagnosticEvent(Instant.ofEpochSecond(2), Type.DEVICE_ONLINE, null);
        d.add(first);
        d.add(second);
        assertThat(d.events()).containsExactly(first, second);
    }

    @Test
    @DisplayName("名称更新与在线状态切换")
    void nameAndOnlineState() {
        DeviceDiagnostics d = new DeviceDiagnostics("SN1", null);
        assertThat(d.deviceName()).isNull();
        d.updateName("nova 14 Ultra");
        assertThat(d.deviceName()).isEqualTo("nova 14 Ultra");
        d.setOnline(true);
        assertThat(d.isOnline()).isTrue();
        d.setOnline(false);
        assertThat(d.isOnline()).isFalse();
    }

    @Test
    @DisplayName("按事件类型统计")
    void errorCountByType() {
        DeviceDiagnostics d = new DeviceDiagnostics("SN1", "dev");
        d.add(event(Type.STREAM_ERROR));
        d.add(event(Type.STREAM_ERROR));
        d.add(event(Type.HDC_FAILURE));
        assertThat(d.errorCount(Type.STREAM_ERROR)).isEqualTo(2);
        assertThat(d.errorCount(Type.HDC_FAILURE)).isEqualTo(1);
        assertThat(d.errorCount(Type.WATCHDOG_STALL)).isZero();
    }
}
