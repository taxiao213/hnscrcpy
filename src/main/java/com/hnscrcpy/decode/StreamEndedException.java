package com.hnscrcpy.decode;

/** 视频流正常结束（对端关闭）——对投屏会话而言与异常等价，需触发重连。 */
public final class StreamEndedException extends RuntimeException {

    public StreamEndedException() {
        super("video stream ended");
    }
}

