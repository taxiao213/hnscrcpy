package com.hnscrcpy.decode;

import com.hnscrcpy.stream.FrameSink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * 解码泵：连接流回调与解码器。
 * - gRPC 线程只入队（有界 8，满则丢最旧），绝不阻塞；
 * - 单个泵线程依次取帧 → avcodec 解码 → 结果放入 latest 槽位（只保留最新一帧）。
 */
public final class DecoderPump implements FrameSink, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(DecoderPump.class);
    private static final int QUEUE_CAPACITY = 8;

    /** 有界丢最旧队列逻辑；包可见静态工厂便于测试。 */
    static <T> boolean offerDropOldest(Queue<T> queue, T item, int capacity) {
        if (queue.size() >= capacity) {
            queue.poll();
        }
        return queue.offer(item);
    }

    private final Object queueLock = new Object();
    private final Queue<byte[]> pending = new ArrayDeque<>(QUEUE_CAPACITY);
    private final AtomicReference<VideoFrame> latest = new AtomicReference<>();
    private final AtomicLong sequence = new AtomicLong();
    private final AtomicLong decodedCount = new AtomicLong();
    private final AtomicLong droppedCount = new AtomicLong();

    private H264Decoder decoder;
    private Thread pumpThread;
    private volatile boolean running;
    private volatile Consumer<Throwable> errorHandler = t -> { };
    /** 最近一条纯参数集消息（首包）。跨重连保留：IDR 不内嵌参数集，重连后设备侧可能不重发。 */
    private volatile byte[] paramSets;
    private volatile boolean paramsInjected;

    /** 流异常/结束回调（gRPC 线程调用）；由会话层接管重连。 */
    public void setErrorHandler(Consumer<Throwable> handler) {
        this.errorHandler = handler != null ? handler : t -> { };
    }

    public void start() {
        if (running) {
            return;
        }
        synchronized (queueLock) {
            pending.clear(); // 重连时丢弃旧流残留帧（新解码器无 SPS/PPS，解不了）
        }
        running = true;
        paramsInjected = false;
        decoder = new H264Decoder();
        pumpThread = new Thread(this::pumpLoop, "decoder-pump");
        pumpThread.setDaemon(true);
        pumpThread.start();
    }

    // ---- FrameSink（gRPC 线程调用，必须快） ----

    @Override
    public void onStreamReady() {
        log.info("stream ready");
    }

    @Override
    public void onH264Frame(byte[] data) {
        if (H264Decoder.isParameterSets(data)) {
            paramSets = data;
        }
        synchronized (queueLock) {
            if (pending.size() >= QUEUE_CAPACITY) {
                pending.poll();
                droppedCount.incrementAndGet();
            }
            pending.offer(data);
            queueLock.notify();
        }
    }

    @Override
    public void onStreamError(Throwable t) {
        log.warn("stream error: {}", t.toString());
        errorHandler.accept(t);
    }

    @Override
    public void onStreamEnded() {
        log.info("stream ended");
        stop();
        errorHandler.accept(new StreamEndedException());
    }

    // ---- 泵线程 ----

    private void pumpLoop() {
        while (running) {
            byte[] chunk = takeChunk();
            if (chunk == null) {
                continue;
            }
            VideoFrame f = tryDecode(chunk);
            if (f == null && paramSets != null && !paramsInjected
                    && !H264Decoder.isParameterSets(chunk)) {
                // 重连后设备侧可能不重发 SPS/PPS（IDR 也不内嵌），解码器缺参数集
                // 会持续性 INVALIDDATA；回灌缓存的参数集后重试一次
                paramsInjected = true;
                log.info("decode failed without params, injecting cached SPS/PPS");
                tryDecode(paramSets);
                f = tryDecode(chunk);
            }
            if (f != null) {
                decodedCount.incrementAndGet();
                latest.set(new VideoFrame(f.width(), f.height(), f.pixels(), f.ptsMicros(),
                        sequence.incrementAndGet()));
            }
        }
    }

    private VideoFrame tryDecode(byte[] chunk) {
        try {
            return decoder.decode(chunk);
        } catch (RuntimeException e) {
            log.warn("decode failed: {}", e.toString());
            return null;
        }
    }

    private byte[] takeChunk() {
        synchronized (queueLock) {
            while (running && pending.isEmpty()) {
                try {
                    queueLock.wait(200);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
            return pending.poll();
        }
    }

    /** 最新解码帧（可能为 null）；引用不可变，可安全跨线程持有。 */
    public VideoFrame latestFrame() {
        return latest.get();
    }

    public long decodedCount() {
        return decodedCount.get();
    }

    public long droppedCount() {
        return droppedCount.get();
    }

    public void stop() {
        running = false;
        synchronized (queueLock) {
            queueLock.notifyAll();
        }
    }

    /** 断流重连：停泵 → 等线程退出 → 释放旧解码器 → 以全新解码器重启。 */
    public void restart() {
        stop();
        joinPumpThread();
        closeDecoder();
        start();
    }

    @Override
    public void close() {
        stop();
        joinPumpThread();
        closeDecoder();
    }

    private void joinPumpThread() {
        if (pumpThread != null) {
            try {
                pumpThread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void closeDecoder() {
        if (decoder != null) {
            decoder.close();
            decoder = null;
        }
    }
}
