package com.hnscrcpy.render;

import com.hnscrcpy.decode.DecoderPump;
import com.hnscrcpy.decode.H264Decoder;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class RenderSchedulerTest {

    @BeforeAll
    static void initToolkit() {
        try {
            Platform.runLater(() -> {
            });
        } catch (IllegalStateException e) {
            Platform.startup(() -> {
            });
        }
    }

    @Test
    @DisplayName("scheduler renders the latest pump frame on the FX thread")
    void scheduler_rendersLatestFrame() throws Exception {
        byte[] sample;
        try (InputStream in = getClass().getResourceAsStream("/sample1.h264")) {
            sample = in.readAllBytes();
        }
        DecoderPump pump = new DecoderPump();
        pump.start();
        try {
            // 喂两帧（参数集 + IDR），等解码出画面
            var units = H264Decoder.splitAccessUnits(sample);
            pump.onH264Frame(units.get(0));
            pump.onH264Frame(units.get(1));
            long deadline = System.currentTimeMillis() + 10000;
            while (pump.latestFrame() == null && System.currentTimeMillis() < deadline) {
                Thread.sleep(50);
            }
            assertThat(pump.latestFrame()).isNotNull();

            FrameRenderer renderer = new FrameRenderer();
            RenderScheduler scheduler = new RenderScheduler(pump, renderer);
            scheduler.start();
            try {
                long renderDeadline = System.currentTimeMillis() + 5000;
                while (renderer.renderedWidth() == 0 && System.currentTimeMillis() < renderDeadline) {
                    Thread.sleep(50);
                }
                assertThat(renderer.renderedWidth()).isEqualTo(1272);
                assertThat(renderer.renderedHeight()).isEqualTo(2860);
            } finally {
                scheduler.stop();
            }
        } finally {
            pump.close();
        }
    }

    @Test
    @DisplayName("start is idempotent and stop without start is safe")
    void startStop_idempotent() {
        DecoderPump pump = new DecoderPump();
        FrameRenderer renderer = new FrameRenderer();
        RenderScheduler scheduler = new RenderScheduler(pump, renderer);
        scheduler.stop();
        scheduler.start();
        scheduler.start();
        scheduler.stop();
        scheduler.stop();
    }
}
