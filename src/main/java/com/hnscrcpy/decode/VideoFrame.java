package com.hnscrcpy.decode;

import java.nio.IntBuffer;

/**
 * 一帧解码后的 ARGB 图像。pixels 长度 = width * height，行主序，JavaFX 兼容格式。
 * sequence 单调递增，用于渲染端丢弃旧帧。
 * <p>
 * pixels 可能是解码器环形复用的原生直接缓冲（零拷贝路径）：访问器每次返回
 * duplicate()（共享内容、独立 position），消费方读取不影响其他持有者，
 * 但不得长期持有——内容会在后续解码中被覆写。
 */
public record VideoFrame(int width, int height, IntBuffer pixels, long ptsMicros, long sequence) {

    @Override
    public IntBuffer pixels() {
        return pixels.duplicate();
    }
}
