package com.hnscrcpy.decode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class H264DecoderTest {

    @Test
    @DisplayName("decodes golden sample access units into correct-resolution ARGB frames")
    void decode_goldenSample_producesFrames() throws Exception {
        byte[] sample;
        try (InputStream in = getClass().getResourceAsStream("/sample1.h264")) {
            assertThat(in).isNotNull();
            sample = in.readAllBytes();
        }
        List<byte[]> units = H264Decoder.splitAccessUnits(sample);
        assertThat(units).hasSizeGreaterThanOrEqualTo(45);

        int frames = 0;
        VideoFrame first = null;
        // 参数集以 extradata 注入（多线程下带内传播有竞态，见 H264Decoder 构造器文档）
        try (H264Decoder decoder = new H264Decoder(units.get(0))) {
            for (byte[] au : units) {
                VideoFrame f = decoder.decode(au);
                if (f != null) {
                    if (first == null) {
                        first = f;
                    }
                    frames++;
                }
            }
            // 帧级多线程解码会缓冲数帧，收尾冲刷取出滞留帧
            for (int i = 0; i < 8; i++) {
                VideoFrame f = decoder.flush();
                if (f == null) {
                    break;
                }
                if (first == null) {
                    first = f;
                }
                frames++;
            }
        }
        assertThat(frames).isGreaterThanOrEqualTo(45);
        assertThat(first.width()).isEqualTo(1272);
        assertThat(first.height()).isEqualTo(2860);
        java.nio.IntBuffer px = first.pixels();
        assertThat(px.remaining()).isEqualTo(1272 * 2860);
        // 画面内容非纯色：采样不同位置应存在多种颜色
        java.util.Set<Integer> colors = new java.util.HashSet<>();
        for (int i = 0; i < px.capacity(); i += 9973) {
            colors.add(px.get(i));
        }
        assertThat(colors.size()).isGreaterThan(10);
    }

    @Test
    @DisplayName("fitOut: caps at native, preserves aspect, even-rounds")
    void fitOut_scalingRules() {
        // 不限制 / 不放大
        assertThat(H264Decoder.fitOut(1000, 500, 0, 0)).containsExactly(1000, 500);
        assertThat(H264Decoder.fitOut(1000, 500, 4000, 4000)).containsExactly(1000, 500);
        // 等比缩小
        assertThat(H264Decoder.fitOut(1000, 500, 500, 500)).containsExactly(500, 250);
        assertThat(H264Decoder.fitOut(1000, 500, 500, 200)).containsExactly(400, 200);
        // 奇数结果偶数取整
        assertThat(H264Decoder.fitOut(1001, 501, 500, 250)).containsExactly(498, 250);
    }

    @Test
    @DisplayName("decode with output cap merges YUV→ARGB and downscale in one pass")
    void decode_outputCap_downscales() throws Exception {
        byte[] sample;
        try (InputStream in = getClass().getResourceAsStream("/sample1.h264")) {
            sample = in.readAllBytes();
        }
        List<byte[]> units = H264Decoder.splitAccessUnits(sample);
        try (H264Decoder decoder = new H264Decoder(units.get(0))) {
            // 帧级多线程流水线有 ≤3 帧滞留，连喂数帧把首帧推出来（保持同样的输出上限）
            VideoFrame f = null;
            for (int i = 1; i <= 6 && f == null; i++) {
                f = decoder.decode(units.get(i), 640, 1440);
            }
            assertThat(f).isNotNull();
            // 1272x2860 等比缩进 640x1440：s=0.5031 → 640x1438（偶数取整）
            assertThat(f.width()).isEqualTo(640);
            assertThat(f.height()).isEqualTo(1438);
            assertThat(f.pixels().remaining()).isEqualTo(f.width() * f.height());
        }
    }

    @Test
    @DisplayName("splitAccessUnits keeps wire format: SPS/PPS message first, IDR second")
    void splitAccessUnits_sampleStructure_expectedGroups() throws Exception {
        byte[] sample;
        try (InputStream in = getClass().getResourceAsStream("/sample1.h264")) {
            sample = in.readAllBytes();
        }
        List<byte[]> units = H264Decoder.splitAccessUnits(sample);
        // 首条 gRPC 消息是 SPS(7)+PPS(8) 参数集，第二条是 IDR(5)
        assertThat(hasNalType(units.get(0), 7)).isTrue();
        assertThat(hasNalType(units.get(0), 8)).isTrue();
        assertThat(hasNalType(units.get(1), 5)).isTrue();
    }

    @Test
    @DisplayName("param sets via extradata: first IDR decodes directly (no in-band race)")
    void decode_extradataParams_idrDecodes() throws Exception {
        byte[] sample;
        try (InputStream in = getClass().getResourceAsStream("/sample1.h264")) {
            sample = in.readAllBytes();
        }
        List<byte[]> units = H264Decoder.splitAccessUnits(sample);
        assertThat(H264Decoder.hasVclNal(units.get(0))).isFalse();
        assertThat(H264Decoder.hasVclNal(units.get(1))).isTrue();

        try (H264Decoder decoder = new H264Decoder(units.get(0))) {
            VideoFrame idr = decoder.decode(units.get(1));
            if (idr == null) {
                idr = decoder.flush(); // 多线程流水线缓冲，冲刷取出
            }
            assertThat(idr).isNotNull();
            assertThat(idr.width()).isEqualTo(1272);
        }
    }

    @Test
    @DisplayName("in-band parameter-only packet returns null without crashing")
    void decode_inBandParams_nullNoCrash() throws Exception {
        byte[] sample;
        try (InputStream in = getClass().getResourceAsStream("/sample1.h264")) {
            sample = in.readAllBytes();
        }
        List<byte[]> units = H264Decoder.splitAccessUnits(sample);
        try (H264Decoder decoder = new H264Decoder()) {
            assertThat(decoder.decode(units.get(0))).isNull();
        }
    }

    private static boolean hasNalType(byte[] au, int type) {
        for (int i = 0; i + 4 < au.length; i++) {
            if (au[i] == 0 && au[i + 1] == 0 && au[i + 2] == 0 && au[i + 3] == 1
                    && (au[i + 4] & 0x1f) == type) {
                return true;
            }
        }
        return false;
    }
}
