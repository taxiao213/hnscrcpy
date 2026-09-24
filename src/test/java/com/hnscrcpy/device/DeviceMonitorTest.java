package com.hnscrcpy.device;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class DeviceMonitorTest {

    /** 可控的假 HdcClient：只覆写 listTargets/describe，避免真实 hdc 调用。 */
    private static final class FakeHdc extends HdcClient {
        private final AtomicReference<List<String>> targets = new AtomicReference<>(List.of());
        private final AtomicInteger describeCalls = new AtomicInteger();

        FakeHdc() {
            super(java.nio.file.Path.of("/nonexistent-hdc"));
        }

        @Override
        public List<String> listTargets() {
            return targets.get();
        }

        @Override
        public DeviceInfo describe(String sn) {
            describeCalls.incrementAndGet();
            return new DeviceInfo(sn, "MODEL-" + sn, "6.0.0");
        }

        void setTargets(List<String> serials) {
            targets.set(serials);
        }
    }

    @Test
    @DisplayName("poll notifies listener only when serial list changes")
    void poll_changeDetection_notifiesOnce() {
        FakeHdc hdc = new FakeHdc();
        AtomicReference<List<DeviceInfo>> received = new AtomicReference<>();
        DeviceMonitor monitor = new DeviceMonitor(hdc, received::set);

        hdc.setTargets(List.of("AAA"));
        monitor.poll();
        assertThat(received.get()).extracting(DeviceInfo::serial).containsExactly("AAA");

        monitor.poll();
        assertThat(hdc.describeCalls.get()).isEqualTo(1);

        hdc.setTargets(List.of("AAA", "BBB"));
        monitor.poll();
        assertThat(received.get()).hasSize(2);
    }

    @Test
    @DisplayName("poll passes empty list when all devices removed")
    void poll_devicesRemoved_notifiesEmpty() {
        FakeHdc hdc = new FakeHdc();
        AtomicReference<List<DeviceInfo>> received = new AtomicReference<>();
        DeviceMonitor monitor = new DeviceMonitor(hdc, received::set);

        hdc.setTargets(List.of("AAA"));
        monitor.poll();
        hdc.setTargets(List.of());
        monitor.poll();
        assertThat(received.get()).isEmpty();
    }
}
