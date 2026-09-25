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
import static org.bytedeco.ffmpeg.global.avutil.av_mallocz;
import static org.bytedeco.ffmpeg.global.swscale.SWS_BILINEAR;
import static org.bytedeco.ffmpeg.global.swscale.sws_freeContext;
import static org.bytedeco.ffmpeg.global.swscale.sws_getContext;
import static org.bytedeco.ffmpeg.global.swscale.sws_scale;

/**
 * H.264 Annex B 解码器，直接走 avcodec（无 demuxer）。
 * 背景：hosScrcpy 的每条 gRPC 消息就是一个完整 access unit，不需要解封装；
 * 而 FFmpegFrameGrabber 的 find_stream_info 在裸 h264 直播管道上会阻塞到 EOF
 * （fps 估算需要读完整流），因此不能用 grabber 做实时解码。
 * 输出 ARGB 的 IntBuffer 视图（环形复用原生缓冲，零拷贝），直接对接 JavaFX PixelBuffer。
 */
public final class H264Decoder implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(H264Decoder.class);

    private final AVCodecContext ctx;
    private final AVPacket packet;
    private final AVFrame frame;
    /** extradata 底层缓冲（av_mallocz 分配，所有权归 ctx，由 avcodec_free_context 释放）。 */
    private BytePointer paramSetsRef;

    private SwsContext sws;
    /** 零拷贝环形原生缓冲：解码直接写入，VideoFrame 持 IntBuffer 视图，省掉每帧 int[] 分配+拷贝 */
    private static final int RING_SIZE = 4;
    private BytePointer[] rgbaRing;
    private IntBuffer[] viewRing;
    private PointerPointer<BytePointer>[] dstDataRing;
    private org.bytedeco.javacpp.IntPointer[] dstLinesizeRing;
    private int ringIdx;
    private int imgW;
    private int imgH;
    private int outW;
    private int outH;

    public H264Decoder() {
        this(null);
    }

    /**
     * @param annexBParams Annex B 形态的 SPS/PPS（可为 null）。帧级多线程下带内参数集
     *                     向各工作线程传播有竞态（紧随其后的 IDR 会 INVALIDDATA），
     *                     因此参数集一律以 extradata 在 open 前注入，工作线程天然继承。
     */
    public H264Decoder(byte[] annexBParams) {
        AVCodec codec = avcodec_find_decoder(AV_CODEC_ID_H264);
        if (codec == null) {
            throw new IllegalStateException("ffmpeg 中未找到 H.264 解码器");
        }
        ctx = avcodec_alloc_context3(codec);
        // 多线程帧级解码：单线程喂 1272x2860@60 高码率会积压丢帧（实测丢帧 2:1）；
        // 4 线程吞吐足够且帧延迟有界（最坏 +3 帧 ≈ 50ms）
        ctx.thread_count(4);
        if (annexBParams != null && annexBParams.length > 0) {
            // FFmpeg 要求 extradata 尾部带 AV_INPUT_BUFFER_PADDING_SIZE(64) 个零字节，
            // 否则解析器可能越界读（实测堆破坏 abort）。
            // 且必须用 av_mallocz 分配：avcodec_free_context 会对 extradata 调 av_free，
            // 给 JavaCPP malloc 的指针会造成堆破坏 abort（实测）。
            byte[] padded = java.util.Arrays.copyOf(annexBParams, annexBParams.length + 64);
            paramSetsRef = new BytePointer(av_mallocz(padded.length));
            paramSetsRef.capacity(padded.length);
            paramSetsRef.put(padded, 0, padded.length);
            ctx.extradata(paramSetsRef);
            ctx.extradata_size(annexBParams.length);
        }
        if (avcodec_open2(ctx, codec, (AVDictionary) null) < 0) {
            throw new IllegalStateException("avcodec_open2 失败");
        }
        packet = av_packet_alloc();
        frame = av_frame_alloc();
        log.debug("avcodec h264 decoder opened, extradata={}",
                annexBParams == null ? 0 : annexBParams.length);
    }

    /**
     * 投喂一个完整 access unit；解码出一帧则返回，否则（参数集、被参考帧缺失等）返回 null。
     * 输出原始尺寸。
     */
    public synchronized VideoFrame decode(byte[] annexB) {
        return decode(annexB, 0, 0);
    }

    /**
     * 同上，但限制输出尺寸上限（保持宽高比缩到 maxOutW x maxOutH 以内，1:1 封顶）。
     * 画面只在小窗口展示时，把 YUV→ARGB 与降采样合并在一次 sws 里完成，
     * 像素搬运量可降一个数量级。maxOutW/H 传 0 表示原始尺寸。
     */
    public synchronized VideoFrame decode(byte[] annexB, int maxOutW, int maxOutH) {
        av_packet_unref(packet);
        if (av_new_packet(packet, annexB.length) < 0) {
            throw new IllegalStateException("av_new_packet 失败");
        }
        packet.data().position(0).put(annexB, 0, annexB.length);
        packet.pts(AV_NOPTS_VALUE);
        VideoFrame drained = null;
        int ret = avcodec_send_packet(ctx, packet);
        if (ret == AVERROR_EAGAIN()) {
            // 帧级多线程下有未取走的输出时 send 会 EAGAIN：先收干再重发（至多一轮）
            drained = receiveOne(maxOutW, maxOutH);
            ret = avcodec_send_packet(ctx, packet);
        }
        av_packet_unref(packet);
        if (ret < 0) {
            // FFmpeg 对只含 SPS/PPS 的包会解析参数但不产出帧，返回 INVALIDDATA——
            // 参数已生效，属正常路径，降级为 debug；含 VCL 的失败才是真异常
            if (hasVclNal(annexB)) {
                log.warn("send_packet failed: {}", ret);
            } else {
                log.debug("parameter-only packet consumed: {}", ret);
            }
            return drained;
        }
        VideoFrame f = receiveOne(maxOutW, maxOutH);
        return f != null ? f : drained;
    }

    /**
     * 冲刷解码流水线：帧级多线程会缓冲数帧（延迟 ≤ thread_count-1 帧），
     * 实时流靠持续输入自然推出，无需调用；测量/测试收尾时用它取出滞留帧。
     * 冲刷后解码器复位，可用于下一条流。
     */
    public synchronized VideoFrame flush() {
        int ret = avcodec_send_packet(ctx, (AVPacket) null);
        if (ret < 0) {
            log.debug("flush send failed: {}", ret);
            return null;
        }
        return receiveOne(0, 0);
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

    private VideoFrame receiveOne(int maxOutW, int maxOutH) {
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
            // 多线程解码一次可能吐多帧，取最新（旧帧直接覆盖 result）
            result = convert(frame, maxOutW, maxOutH);
        }
        return result;
    }

    private VideoFrame convert(AVFrame f, int maxOutW, int maxOutH) {
        int w = f.width();
        int h = f.height();
        int[] out = fitOut(w, h, maxOutW, maxOutH);
        ensureScaler(w, h, f.format(), out[0], out[1]);
        ringIdx = (ringIdx + 1) % RING_SIZE;
        sws_scale(sws, f.data(), f.linesize(), 0, h, dstDataRing[ringIdx], dstLinesizeRing[ringIdx]);
        long pts = f.pts() == AV_NOPTS_VALUE ? -1 : f.pts();
        return new VideoFrame(out[0], out[1], viewRing[ringIdx], pts, -1);
    }

    /**
     * 保持宽高比把输出限制在 maxW x maxH 内（1:1 封顶，不放大），偶数取整。
     * maxW/H <= 0 表示不限制。
     */
    static int[] fitOut(int w, int h, int maxW, int maxH) {
        if (maxW <= 0 || maxH <= 0 || (w <= maxW && h <= maxH)) {
            return new int[]{w, h};
        }
        double s = Math.min(maxW / (double) w, maxH / (double) h);
        return new int[]{Math.max(2, (int) (w * s) & ~1), Math.max(2, (int) (h * s) & ~1)};
    }

    @SuppressWarnings("unchecked")
    private void ensureScaler(int w, int h, int srcFormat, int ow, int oh) {
        if (sws != null && imgW == w && imgH == h && outW == ow && outH == oh) {
            return;
        }
        if (sws != null) {
            sws_freeContext(sws);
        }
        // 旧环形缓冲不主动 deallocate：渲染线程可能仍持有着旧帧的 IntBuffer 视图
        // （尺寸变化瞬间 latest 槽里的旧帧），显式释放会产生 use-after-free 花屏。
        // 丢掉引用交给 JavaCPP Cleaner 在 GC 时释放，读取方安全。
        rgbaRing = null;
        viewRing = null;
        dstDataRing = null;
        dstLinesizeRing = null;
        imgW = w;
        imgH = h;
        outW = ow;
        outH = oh;
        sws = sws_getContext(w, h, srcFormat, ow, oh, org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_ARGB,
                SWS_BILINEAR, null, null, (org.bytedeco.javacpp.DoublePointer) null);
        if (sws == null) {
            throw new IllegalStateException("sws_getContext 失败");
        }
        rgbaRing = new BytePointer[RING_SIZE];
        viewRing = new IntBuffer[RING_SIZE];
        dstDataRing = new PointerPointer[RING_SIZE];
        dstLinesizeRing = new org.bytedeco.javacpp.IntPointer[RING_SIZE];
        for (int i = 0; i < RING_SIZE; i++) {
            rgbaRing[i] = new BytePointer((long) ow * oh * 4);
            viewRing[i] = rgbaRing[i].asByteBuffer().order(ByteOrder.BIG_ENDIAN).asIntBuffer();
            dstDataRing[i] = new PointerPointer<>(rgbaRing[i], null);
            dstLinesizeRing[i] = new org.bytedeco.javacpp.IntPointer(1);
            dstLinesizeRing[i].put(0, ow * 4);
        }
        ringIdx = -1;
        log.info("decoder scaler ready {}x{} -> {}x{}", w, h, ow, oh);
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
        if (rgbaRing != null) {
            for (BytePointer p : rgbaRing) {
                p.deallocate();
            }
        }
        if (ctx != null) {
            avcodec_free_context(ctx); // 同时 av_free 掉 extradata（paramSetsRef 不得自行释放）
        }
    }
}
