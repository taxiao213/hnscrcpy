package com.hnscrcpy.diag;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 单条设备诊断事件：不可变值对象。
 *
 * @Author: taxiao
 * WeChat：他晓
 * CSDN:http://blog.csdn.net/yin13753884368/article
 * Github:https://github.com/taxiao213
 */

public record DiagnosticEvent(Instant time, Type type, String detail) {

    /** 异常类别；与用户感知的问题对应（网络 / 连接 / 花屏 / 卡顿）。 */
    public enum Category {
        NETWORK("网络"), CONNECTION("连接"), HDC("hdc"),
        DECODE("解码/花屏"), STALL("卡顿/延迟"), LIFECYCLE("会话"), DEVICE("设备");

        private final String label;

        Category(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum Type {
        STREAM_ERROR(Category.NETWORK, true),
        RECONNECT_SCHEDULED(Category.CONNECTION, false),
        RECONNECTED(Category.CONNECTION, false),
        WATCHDOG_STALL(Category.STALL, true),
        HDC_FAILURE(Category.HDC, true),
        HDC_TIMEOUT(Category.HDC, true),
        PARAM_SETS_CHANGED(Category.DECODE, true),
        DECODER_CREATED(Category.DECODE, false),
        DECODE_FAILURE(Category.DECODE, true),
        DEVICE_ONLINE(Category.DEVICE, false),
        DEVICE_OFFLINE(Category.DEVICE, false),
        SESSION_STARTED(Category.LIFECYCLE, false),
        SESSION_CLOSED(Category.LIFECYCLE, false);

        private final Category category;
        private final boolean error;

        Type(Category category, boolean error) {
            this.category = category;
            this.error = error;
        }

        public Category category() {
            return category;
        }

        public boolean isError() {
            return error;
        }
    }

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    public String formattedTime() {
        return FMT.format(time);
    }

    public boolean isError() {
        return type.isError();
    }
}
