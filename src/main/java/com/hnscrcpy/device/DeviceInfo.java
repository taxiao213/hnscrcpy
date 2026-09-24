package com.hnscrcpy.device;

/**
 * 一台已连接设备。type 由序列号前缀推断：127.0.* 为模拟器。
 */
public record DeviceInfo(String serial, String model, String osVersion) {

    public enum Type { REAL, EMULATOR }

    public Type type() {
        return serial.startsWith("127.0.") ? Type.EMULATOR : Type.REAL;
    }

    /** 列表展示文本：型号 (序列号)；型号未知时只显示序列号。 */
    public String displayName() {
        if (model == null || model.isBlank()) {
            return serial;
        }
        return model + "  (" + serial + ")";
    }
}
