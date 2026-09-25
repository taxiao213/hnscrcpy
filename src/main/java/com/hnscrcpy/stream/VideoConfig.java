package com.hnscrcpy.stream;

/**
 * 视频流参数。bitRate 单位为 Mbps（hosScrcpy 内部按 &lt;&lt;20 换算），
 * iFrameInterval 单位为毫秒。
 *
 * @Author: taxiao
 * WeChat：他晓
 * CSDN:http://blog.csdn.net/yin13753884368/article
 * Github:https://github.com/taxiao213
 */

public record VideoConfig(int maxSize, int bitRateMbps, int fps, int iFrameIntervalMs, int scale) {

    public static VideoConfig defaults() {
        return new VideoConfig(1600, 30, 60, 2000, 1);
    }

    public VideoConfig withBitRate(int mbps) {
        return new VideoConfig(maxSize, mbps, fps, iFrameIntervalMs, scale);
    }

    public VideoConfig withFps(int fps) {
        return new VideoConfig(maxSize, bitRateMbps, fps, iFrameIntervalMs, scale);
    }
}
