package com.hnscrcpy.decode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DecoderPumpLiveTest {

    @Test
    @DisplayName("pump decodes a fed golden-sample stream end to end")
    void pump_feedSample_decodesToLatest() throws Exception {
        byte[] sample;
        try (InputStream in = getClass().getResourceAsStream("/sample1.h264")) {
            assertThat(in).isNotNull();
            sample = in.readAllBytes();
        }
        List<byte[]> units = H264Decoder.splitAccessUnits(sample);

        DecoderPump pump = new DecoderPump();
        pump.start();
        try {
            // 按泵的消费节奏投喂，避免触发丢帧（突发灌入会按设计丢最旧）
            for (byte[] au : units) {
                pump.onH264Frame(au);
                Thread.sleep(30);
            }
            long deadline = System.currentTimeMillis() + 15000;
            while (pump.decodedCount() < units.size() - 4 && System.currentTimeMillis() < deadline) {
                Thread.sleep(50);
            }
            // 帧级多线程解码有 ≤3 帧流水线滞留，收尾不冲刷，允许末尾少 4 帧（1 参数集 + 3 滞留）
            assertThat(pump.decodedCount()).isGreaterThanOrEqualTo(units.size() - 4);
            VideoFrame latest = pump.latestFrame();
            assertThat(latest).isNotNull();
            assertThat(latest.width()).isEqualTo(1272);
            assertThat(latest.height()).isEqualTo(2860);
            assertThat(latest.sequence()).isGreaterThan(0);
            assertThat(pump.droppedCount()).isZero();
        } finally {
            pump.close();
        }
    }
}
