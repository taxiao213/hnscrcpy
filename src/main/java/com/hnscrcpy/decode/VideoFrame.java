package com.hnscrcpy.decode;

/**
 * 一帧解码后的 ARGB 图像。pixels 长度 = width * height，行主序，JavaFX 兼容格式。
 * sequence 单调递增，用于渲染端丢弃旧帧。
 */
public record VideoFrame(int width, int height, int[] pixels, long ptsMicros, long sequence) {
}
