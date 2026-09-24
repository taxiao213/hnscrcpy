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
        try (H264Decoder decoder = new H264Decoder()) {
            for (byte[] au : units) {
                VideoFrame f = decoder.decode(au);
                if (f != null) {
                    if (first == null) {
                        first = f;
                    }
                    frames++;
                }
            }
        }
        assertThat(frames).isGreaterThanOrEqualTo(45);
        assertThat(first.width()).isEqualTo(1272);
        assertThat(first.height()).isEqualTo(2860);
        assertThat(first.pixels()).hasSize(1272 * 2860);
        // 画面内容非纯色：采样不同位置应存在多种颜色
        java.util.Set<Integer> colors = new java.util.HashSet<>();
        for (int i = 0; i < first.pixels().length; i += 9973) {
            colors.add(first.pixels()[i]);
        }
        assertThat(colors.size()).isGreaterThan(10);
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
