package com.hnscrcpy.device;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @Author: taxiao
 * WeChat：他晓
 * CSDN:http://blog.csdn.net/yin13753884368/article
 * Github:https://github.com/taxiao213
 */

class DeviceMonitorLifecycleTest {

    private static final class FakeHdc extends HdcClient {
        FakeHdc() {
            super(Path.of("/nonexistent-hdc"));
        }

        @Override
        public List<String> listTargets() {
            return List.of("SN1");
        }

        @Override
        public DeviceInfo describe(String sn) {
            return new DeviceInfo(sn, "M", "1.0");
        }
    }

    @Test
    @DisplayName("start polls and delivers initial device list; stop ends polling")
    void startStop_lifecycle() throws Exception {
        AtomicReference<List<DeviceInfo>> received = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        DeviceMonitor monitor = new DeviceMonitor(new FakeHdc(), devices -> {
            received.set(devices);
            latch.countDown();
        });
        monitor.start();
        try {
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(received.get()).extracting(DeviceInfo::serial).containsExactly("SN1");
        } finally {
            monitor.stop();
        }
        // stop 幂等
        monitor.stop();
    }
}
