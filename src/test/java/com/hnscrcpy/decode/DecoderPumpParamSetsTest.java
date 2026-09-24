package com.hnscrcpy.decode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 回归：重连后设备侧不重发 SPS/PPS（IDR 也不内嵌参数集）时，
 * 解码器持续性 INVALIDDATA 的画面卡死——泵须回灌缓存的参数集自愈。
 */
class DecoderPumpParamSetsTest {

    @Test
    @DisplayName("restart mid-stream: IDR without resent SPS/PPS recovers via cached param sets")
    void restart_withoutResendParams_injectsAndRecovers() throws Exception {
        byte[] sample;
        try (InputStream in = getClass().getResourceAsStream("/sample1.h264")) {
            assertThat(in).isNotNull();
            sample = in.readAllBytes();
        }
        List<byte[]> units = H264Decoder.splitAccessUnits(sample);
        assertThat(H264Decoder.isParameterSets(units.get(0))).isTrue();
        assertThat(H264Decoder.isParameterSets(units.get(1))).isFalse();

        DecoderPump pump = new DecoderPump();
        pump.start();
        try {
            pump.onH264Frame(units.get(0)); // SPS/PPS 首包
            Thread.sleep(50);
            pump.onH264Frame(units.get(1)); // IDR
            waitDecoded(pump, 1, 10_000);

            pump.restart(); // 全新解码器，paramSets 缓存保留
            pump.onH264Frame(units.get(1)); // 设备侧重连后未重发参数集，直接来 IDR
            waitDecoded(pump, 2, 10_000);
            assertThat(pump.decodedCount()).isEqualTo(2);
        } finally {
            pump.close();
        }
    }

    private static void waitDecoded(DecoderPump pump, long target, long timeoutMs)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (pump.decodedCount() < target && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertThat(pump.decodedCount())
                .as("decoded %d frames within %dms", target, timeoutMs)
                .isGreaterThanOrEqualTo(target);
    }
}
