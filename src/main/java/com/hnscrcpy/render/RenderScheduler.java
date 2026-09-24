package com.hnscrcpy.render;

import com.hnscrcpy.decode.DecoderPump;
import com.hnscrcpy.decode.VideoFrame;
import javafx.animation.AnimationTimer;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongConsumer;

/**
 * 渲染调度：60fps AnimationTimer 轮询最新帧槽位，序列号未变则跳过。
 * 渲染永远只发生在 FX 线程。
 */
public final class RenderScheduler {

    private final DecoderPump pump;
    private final FrameRenderer renderer;
    private final AtomicLong lastRendered = new AtomicLong(-1);
    private AnimationTimer timer;
    private LongConsumer statsListener;
    private Runnable frameHook;
    private long statWindowStart;
    private int statFrames;

    public RenderScheduler(DecoderPump pump, FrameRenderer renderer) {
        this.pump = pump;
        this.renderer = renderer;
    }

    /** 每秒回调一次实际渲染帧率。 */
    public void setStatsListener(LongConsumer listener) {
        this.statsListener = listener;
    }

    /** 每渲染一帧新画面后回调（FX 线程），用于旋转自适应等。 */
    public void setFrameHook(Runnable hook) {
        this.frameHook = hook;
    }

    public void start() {
        if (timer != null) {
            return;
        }
        statWindowStart = System.currentTimeMillis();
        timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                VideoFrame f = pump.latestFrame();
                if (f != null && f.sequence() != lastRendered.get()) {
                    renderer.render(f);
                    lastRendered.set(f.sequence());
                    statFrames++;
                    if (frameHook != null) {
                        frameHook.run();
                    }
                }
                long elapsed = System.currentTimeMillis() - statWindowStart;
                if (elapsed >= 1000 && statsListener != null) {
                    statsListener.accept(statFrames * 1000L / elapsed);
                    statFrames = 0;
                    statWindowStart = System.currentTimeMillis();
                }
            }
        };
        timer.start();
    }

    public void stop() {
        if (timer != null) {
            timer.stop();
            timer = null;
        }
    }
}
