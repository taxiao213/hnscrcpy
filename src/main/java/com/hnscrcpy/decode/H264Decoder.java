package com.hnscrcpy.decode;

import org.bytedeco.ffmpeg.avcodec.AVCodec;
import org.bytedeco.ffmpeg.avcodec.AVCodecContext;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.bytedeco.ffmpeg.avutil.AVDictionary;
import org.bytedeco.ffmpeg.swscale.SwsContext;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.PointerPointer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_H264;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_alloc_context3;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_find_decoder;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_free_context;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_open2;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_receive_frame;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_send_packet;
import static org.bytedeco.ffmpeg.global.avcodec.av_new_packet;
import static org.bytedeco.ffmpeg.global.avcodec.av_packet_alloc;
import static org.bytedeco.ffmpeg.global.avcodec.av_packet_unref;
import static org.bytedeco.ffmpeg.global.avutil.AVERROR_EAGAIN;
import static org.bytedeco.ffmpeg.global.avutil.AVERROR_EOF;
import static org.bytedeco.ffmpeg.global.avutil.AV_NOPTS_VALUE;
import static org.bytedeco.ffmpeg.global.avutil.av_frame_alloc;
import static org.bytedeco.ffmpeg.global.avutil.av_frame_unref;
import static org.bytedeco.ffmpeg.global.avutil.av_free;
import static org.bytedeco.ffmpeg.global.avutil.av_image_alloc;
import static org.bytedeco.ffmpeg.global.swscale.SWS_BILINEAR;
import static org.bytedeco.ffmpeg.global.swscale.sws_freeContext;
import static org.bytedeco.ffmpeg.global.swscale.sws_getContext;
import static org.bytedeco.ffmpeg.global.swscale.sws_scale;

/**
 * H.264 Annex B 解码器，直接走 avcodec（无 demuxer）。
 * 背景：hosScrcpy 的每条 gRPC 消息就是一个完整 access unit，不需要解封装；
 * 而 FFmpegFrameGrabber 的 find_stream_info 在裸 h264 直播管道上会阻塞到 EOF
 * （fps 估算需要读完整流），因此不能用 grabber 做实时解码。
 * 输出 ARGB int[]，直接对接 JavaFX PixelBuffer。
 */
public final class H264Decoder implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(H264Decoder.class);

    private final AVCodecContext ctx;
    private final AVPacket packet;
    private final AVFrame frame;

    private SwsContext sws;
    private BytePointer rgbaBuf;
    private PointerPointer<BytePointer> dstData;
    private org.bytedeco.javacpp.IntPointer dstLinesize;
    private int imgW;
    private int imgH;

    public H264Decoder() {
        AVCodec codec = avcodec_find_decoder(AV_CODEC_ID_H264);
        if (codec == null) {
            throw new IllegalStateException("ffmpeg 中未找到 H.264 解码器");
        }
        ctx = avcodec_alloc_context3(codec);
        ctx.thread_count(1);
        if (avcodec_open2(ctx, codec, (AVDictionary) null) < 0) {
            throw new IllegalStateException("avcodec_open2 失败");
        }
        packet = av_packet_alloc();
        frame = av_frame_alloc();
        log.debug("avcodec h264 decoder opened");
    }

    /**
     * 投喂一个完整 access unit；解码出一帧则返回，否则（参数集、被参考帧缺失等）返回 null。
     */
    public synchronized VideoFrame decode(byte[] annexB) {
        av_packet_unref(packet);
        if (av_new_packet(packet, annexB.length) < 0) {
            throw new IllegalStateException("av_new_packet 失败");
        }
        packet.data().position(0).put(annexB, 0, annexB.length);
        packet.pts(AV_NOPTS_VALUE);
        int ret = avcodec_send_packet(ctx, packet);
        av_packet_unref(packet);
        if (ret < 0) {
            // FFmpeg 对只含 SPS/PPS 的包会解析参数但不产出帧，返回 INVALIDDATA——
            // 参数已生效，属正常路径，降级为 debug；含 VCL 的失败才是真异常
            if (hasVclNal(annexB)) {
                log.warn("send_packet failed: {}", ret);
            } else {
                log.debug("parameter-only packet consumed: {}", ret);
            }
            return null;
        }
        return receiveOne();
    }

    /** 是否含 VCL NAL（slice，类型 1/5）。 */
    static boolean hasVclNal(byte[] data) {
        int n = data.length;
        int i = 0;
        while (i + 4 < n) {
            if (data[i] == 0 && data[i + 1] == 0 && (data[i + 2] == 1
                    || (data[i + 2] == 0 && data[i + 3] == 1))) {
                int nalType = data[data[i + 2] == 1 ? i + 3 : i + 4] & 0x1f;
                if (nalType == 1 || nalType == 5) {
                    return true;
                }
                i += 4;
            } else {
                i++;
            }
        }
        return false;
    }

    private VideoFrame receiveOne() {
        VideoFrame result = null;
        while (true) {
            av_frame_unref(frame);
            int ret = avcodec_receive_frame(ctx, frame);
            if (ret == AVERROR_EAGAIN() || ret == AVERROR_EOF()) {
                break;
            }
            if (ret < 0) {
                log.warn("receive_frame failed: {}", ret);
                break;
            }
            result = convert(frame);
        }
        return result;
    }

    private VideoFrame convert(AVFrame f) {
        int w = f.width();
        int h = f.height();
        ensureScaler(w, h, f.format());
        sws_scale(sws, f.data(), f.linesize(), 0, h, dstData, dstLinesize);
        int[] pixels = new int[w * h];
        ByteBuffer bb = rgbaBuf.asByteBuffer().order(ByteOrder.BIG_ENDIAN);
        IntBuffer ib = bb.asIntBuffer();
        ib.get(pixels);
        long pts = f.pts() == AV_NOPTS_VALUE ? -1 : f.pts();
        return new VideoFrame(w, h, pixels, pts, -1);
    }

    private void ensureScaler(int w, int h, int srcFormat) {
        if (sws != null && imgW == w && imgH == h) {
            return;
        }
        if (sws != null) {
            sws_freeContext(sws);
        }
        imgW = w;
        imgH = h;
        sws = sws_getContext(w, h, srcFormat, w, h, org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_ARGB,
                SWS_BILINEAR, null, null, (org.bytedeco.javacpp.DoublePointer) null);
        if (sws == null) {
            throw new IllegalStateException("sws_getContext 失败");
        }
        rgbaBuf = new BytePointer((long) w * h * 4);
        dstData = new PointerPointer<>(rgbaBuf, null);
        dstLinesize = new org.bytedeco.javacpp.IntPointer(1);
        dstLinesize.put(0, w * 4);
        log.info("decoder scaler ready {}x{}", w, h);
    }

    /**
     * 把 Annex B 字节流切成 access unit：遇到 IDR(5)/非 IDR(1) slice 的 NAL 即开始新 AU，
     * 其前的 SPS(7)/PPS(8)/SEI(6)/AUD(9) 归入该 AU。
     */
    public static List<byte[]> splitAccessUnits(byte[] data) {
        List<byte[]> units = new ArrayList<>();
        int n = data.length;
        // 从 0 开始：流首的 SPS/PPS/SEI 归入第一个 slice AU
        int auStart = 0;
        int i = 0;
        while (i + 4 < n) {
            if (data[i] == 0 && data[i + 1] == 0 && (data[i + 2] == 1
                    || (data[i + 2] == 0 && data[i + 3] == 1))) {
                int nalPos = data[i + 2] == 1 ? i + 3 : i + 4;
                int nalType = data[nalPos] & 0x1f;
                if (nalType == 1 || nalType == 5) {
                    if (auStart < i) {
                        units.add(java.util.Arrays.copyOfRange(data, auStart, i));
                    }
                    auStart = i;
                }
                i = nalPos + 1;
            } else {
                i++;
            }
        }
        if (auStart >= 0) {
            units.add(java.util.Arrays.copyOfRange(data, auStart, n));
        }
        return units;
    }

    /**
     * 判断一条消息是否为纯参数集（含 SPS/PPS、无 VCL slice）。
     * hosScrcpy 首条 gRPC 消息即此形态（约 33 字节），且 IDR 不内嵌参数集——
     * 重连后设备侧可能不重发，需客户端缓存并在解码失败时回灌。
     */
    public static boolean isParameterSets(byte[] data) {
        boolean hasParams = false;
        int n = data.length;
        int i = 0;
        while (i + 4 < n) {
            if (data[i] == 0 && data[i + 1] == 0 && (data[i + 2] == 1
                    || (data[i + 2] == 0 && data[i + 3] == 1))) {
                int nalPos = data[i + 2] == 1 ? i + 3 : i + 4;
                int nalType = data[nalPos] & 0x1f;
                if (nalType == 1 || nalType == 5) {
                    return false;
                }
                if (nalType == 7 || nalType == 8) {
                    hasParams = true;
                }
                i = nalPos + 1;
            } else {
                i++;
            }
        }
        return hasParams;
    }

    @Override
    public void close() {
        if (sws != null) {
            sws_freeContext(sws);
        }
        if (rgbaBuf != null) {
            rgbaBuf.deallocate();
        }
        if (ctx != null) {
            avcodec_free_context(ctx);
        }
    }
}
