package com.hnscrcpy.control;

/**
 * 视图坐标 → 设备坐标映射。等比缩放 + 居中留边（letterbox），支持横竖屏切换。
 * 设备竖屏 (w&lt;h) 时，视图也竖直放置；horizontal=true 时设备宽高互换。
 *
 * @Author: taxiao
 * WeChat：他晓
 * CSDN:http://blog.csdn.net/yin13753884368/article
 * Github:https://github.com/taxiao213
 */

public final class CoordinateMapper {

    private final int deviceWidth;
    private final int deviceHeight;
    private volatile boolean horizontal;

    public CoordinateMapper(int deviceWidth, int deviceHeight) {
        this.deviceWidth = deviceWidth;
        this.deviceHeight = deviceHeight;
    }

    public void setHorizontal(boolean horizontal) {
        this.horizontal = horizontal;
    }

    public boolean isHorizontal() {
        return horizontal;
    }

    private int displayWidth() {
        return horizontal ? deviceHeight : deviceWidth;
    }

    private int displayHeight() {
        return horizontal ? deviceWidth : deviceHeight;
    }

    /** 视图内画面区域的缩放比与留边偏移。 */
    public double[] fit(double viewWidth, double viewHeight) {
        double scale = Math.min(viewWidth / displayWidth(), viewHeight / displayHeight());
        double offsetX = (viewWidth - displayWidth() * scale) / 2;
        double offsetY = (viewHeight - displayHeight() * scale) / 2;
        return new double[]{scale, offsetX, offsetY};
    }

    /**
     * 视图坐标 → 设备坐标（int[]{deviceX, deviceY}）；超出画面区域时钳制到边缘。
     * 注意：uitest 的布局 dump 与触摸注入使用同一套「当前显示方向」坐标空间
     * （横屏实测 dumpLayout 根节点 [0,0][2860,1272]），因此横竖屏都是直通映射，
     * 方向差异只体现在 displayWidth()/displayHeight() 的互换（见 fit）。
     */
    public int[] toDevice(double viewX, double viewY, double viewWidth, double viewHeight) {
        double[] f = fit(viewWidth, viewHeight);
        double dx = (viewX - f[1]) / f[0];
        double dy = (viewY - f[2]) / f[0];
        dx = clamp(dx, 0, displayWidth() - 1);
        dy = clamp(dy, 0, displayHeight() - 1);
        return new int[]{(int) dx, (int) dy};
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
