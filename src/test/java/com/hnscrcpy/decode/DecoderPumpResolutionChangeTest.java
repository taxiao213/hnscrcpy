package com.hnscrcpy.decode;

import org.bytedeco.ffmpeg.avcodec.AVCodec;
import org.bytedeco.ffmpeg.avcodec.AVCodecContext;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avutil.AVDictionary;
import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.bytedeco.ffmpeg.avutil.AVRational;
import org.bytedeco.javacpp.BytePointer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_FLAG_GLOBAL_HEADER;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_alloc_context3;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_find_encoder_by_name;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_free_context;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_open2;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_receive_packet;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_send_frame;
import static org.bytedeco.ffmpeg.global.avcodec.av_packet_alloc;
import static org.bytedeco.ffmpeg.global.avcodec.av_packet_unref;
import static org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_YUV420P;
import static org.bytedeco.ffmpeg.global.avutil.av_frame_alloc;
import static org.bytedeco.ffmpeg.global.avutil.av_frame_free;
import static org.bytedeco.ffmpeg.global.avutil.av_frame_get_buffer;
import static org.bytedeco.ffmpeg.global.avutil.av_frame_make_writable;

/**
 * 回归：横竖屏切换（分辨率变化）时设备重发新参数集，泵必须以新参数集重建解码器——
 * 旧参数集解新分辨率帧就是花屏。测试用 openh264 现场编码一条 160x120 横屏流，
 * 接到竖屏 golden 样本之后，验证解码输出尺寸跟随切换。
 */
class DecoderPumpResolutionChangeTest {

    @Test
    @DisplayName("resolution change mid-stream: decoder recreated, output dims follow new stream")
    void resolutionChange_outputDimsFollow() throws Exception {
        byte[] sample;
        try (InputStream in = getClass().getResourceAsStream("/sample1.h264")) {
            assertThat(in).isNotNull();
            sample = in.readAllBytes();
        }
        List<byte[]> portrait = H264Decoder.splitAccessUnits(sample);
        byte[][] landscape = encodeH264(160, 120, 8);
        assertThat(H264Decoder.isParameterSets(landscape[0])).isTrue();

        DecoderPump pump = new DecoderPump();
        pump.start();
        try {
            // 竖屏流先跑起来
            pump.onH264Frame(portrait.get(0));
            Thread.sleep(50);
            for (int i = 1; i <= 6; i++) {
                pump.onH264Frame(portrait.get(i));
                Thread.sleep(20);
            }
            waitFor(pump, 1272, 10_000);

            // 旋转：参数集包（新分辨率）+ 横屏帧
            for (byte[] au : landscape) {
                pump.onH264Frame(au);
                Thread.sleep(20);
            }
            VideoFrame f = waitFor(pump, 160, 10_000);
            assertThat(f.height()).isEqualTo(120);
        } finally {
            pump.close();
        }
    }

    private static VideoFrame waitFor(DecoderPump pump, int width, long timeoutMs)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        VideoFrame f;
        while (System.currentTimeMillis() < deadline) {
            f = pump.latestFrame();
            if (f != null && f.width() == width) {
                return f;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("no frame with width=" + width + " within " + timeoutMs + "ms"
                + ", latest=" + pump.latestFrame());
    }

    /**
     * 用 openh264 编码一条渐变图案流：返回 [annexb 参数集, AU, AU, ...]。
     * GLOBAL_HEADER 让 SPS/PPS 进 extradata（openh264 的 extradata 本身就是 Annex B）。
     */
    private static byte[][] encodeH264(int w, int h, int frames) {
        AVCodec codec = avcodec_find_encoder_by_name("libopenh264");
        assertThat(codec).as("libopenh264 encoder available").isNotNull();
        AVCodecContext c = avcodec_alloc_context3(codec);
        c.width(w);
        c.height(h);
        c.pix_fmt(AV_PIX_FMT_YUV420P);
        c.time_base(new AVRational().num(1).den(30));
        c.bit_rate(200_000);
        c.gop_size(10);
        c.max_b_frames(0);
        c.flags(c.flags() | AV_CODEC_FLAG_GLOBAL_HEADER);
        assertThat(avcodec_open2(c, codec, (AVDictionary) null)).isEqualTo(0);

        byte[] annexbParams = new byte[c.extradata_size()];
        c.extradata().position(0).get(annexbParams);

        AVFrame frame = av_frame_alloc();
        frame.width(w);
        frame.height(h);
        frame.format(AV_PIX_FMT_YUV420P);
        assertThat(av_frame_get_buffer(frame, 0)).isEqualTo(0);
        AVPacket pkt = av_packet_alloc();
        List<byte[]> aus = new ArrayList<>();
        try {
            for (int i = 0; i < frames; i++) {
                av_frame_make_writable(frame);
                fillPattern(frame, w, h, i);
                frame.pts(i);
                assertThat(avcodec_send_frame(c, frame)).isEqualTo(0);
                drain(c, pkt, aus);
            }
            avcodec_send_frame(c, (AVFrame) null);
            drain(c, pkt, aus);
        } finally {
            av_frame_free(frame);
            av_packet_unref(pkt);
            avcodec_free_context(c);
        }
        assertThat(aus).isNotEmpty();
        List<byte[]> out = new ArrayList<>();
        out.add(annexbParams);
        out.addAll(aus);
        return out.toArray(new byte[0][]);
    }

    private static void drain(AVCodecContext c, AVPacket pkt, List<byte[]> aus) {
        while (avcodec_receive_packet(c, pkt) == 0) {
            byte[] au = new byte[pkt.size()];
            pkt.data().position(0).get(au);
            aus.add(au);
            av_packet_unref(pkt);
        }
    }

    private static void fillPattern(AVFrame f, int w, int h, int phase) {
        BytePointer y = f.data(0);
        for (int row = 0; row < h; row++) {
            long base = (long) row * f.linesize(0);
            for (int col = 0; col < w; col++) {
                y.put(base + col, (byte) ((row + col + phase * 11) & 0xFF));
            }
        }
        for (int plane = 1; plane <= 2; plane++) {
            BytePointer p = f.data(plane);
            for (int row = 0; row < h / 2; row++) {
                long base = (long) row * f.linesize(plane);
                for (int col = 0; col < w / 2; col++) {
                    p.put(base + col, (byte) 128);
                }
            }
        }
    }

}
