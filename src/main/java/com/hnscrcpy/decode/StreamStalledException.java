package com.hnscrcpy.decode;

/**
 * 流静默死亡（设备侧退出/被抢占等，gRPC 无任何异常回调）——
 * 由看门狗在「请求 IDR 后仍无新帧」时判定。
 */
public final class StreamStalledException extends RuntimeException {

    public StreamStalledException() {
        super("video stream stalled (no frames after IDR request)");
    }
}
