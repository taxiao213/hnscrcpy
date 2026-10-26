package com.hnscrcpy.decode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * restart 回归：重连必须清掉旧帧槽位（旧帧视图指向旧解码器环形缓冲，
 * 渲染线程再画它就是花屏/use-after-free），并重置计数器（看门狗首帧判定、
 * 状态栏统计都以新代为准）。
 *
 * @Author: taxiao
 * WeChat：他晓
 */
class DecoderPumpRestartTest {

    @Test
    @DisplayName("restart clears latest frame and resets counters; old frame stays readable")
    void restart_clearsLatestAndResetsCounters() throws Exception {
        byte[] sample;
        try (InputStream in = getClass().getResourceAsStream("/sample1.h264")) {
            assertThat(in).isNotNull();
            sample = in.readAllBytes();
        }
        List<byte[]> units = H264Decoder.splitAccessUnits(sample);

        DecoderPump pump = new DecoderPump();
        pump.start();
        try {
            feed(pump, units, 8);
            VideoFrame first = awaitFrame(pump, 10_000);
            assertThat(first).isNotNull();
            assertThat(pump.decodedCount()).isGreaterThan(0);

            pump.restart();

            assertThat(pump.latestFrame()).as("restart 后渲染线程不得再看到旧帧").isNull();
            assertThat(pump.decodedCount()).isZero();
            assertThat(pump.droppedCount()).isZero();
            // 旧帧视图仍须可读（旧解码器环形缓冲不随 restart 显式释放）
            assertThat(first.pixels().remaining()).isEqualTo(1272L * 2860L);
            first.pixels().get(0);

            // 新代恢复出图（参数集跨重连缓存，VCL 帧到达即懒建解码器）
            feed(pump, units, 8);
            assertThat(awaitFrame(pump, 10_000)).isNotNull();
            assertThat(pump.decodedCount()).isGreaterThan(0);
        } finally {
            pump.close();
        }
    }

    private static void feed(DecoderPump pump, List<byte[]> units, int frames)
            throws InterruptedException {
        pump.onH264Frame(units.get(0));
        for (int i = 1; i <= frames; i++) {
            pump.onH264Frame(units.get(i));
            Thread.sleep(15);
        }
    }

    private static VideoFrame awaitFrame(DecoderPump pump, long timeoutMs)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            VideoFrame f = pump.latestFrame();
            if (f != null) {
                return f;
            }
            Thread.sleep(50);
        }
        return null;
    }
}
