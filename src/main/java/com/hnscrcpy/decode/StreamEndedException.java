package com.hnscrcpy.decode;

/** 视频流正常结束（对端关闭）——对投屏会话而言与异常等价，需触发重连。
 *
 * @Author: taxiao
 * WeChat：他晓
 * CSDN:http://blog.csdn.net/yin13753884368/article
 * Github:https://github.com/taxiao213
 */

public final class StreamEndedException extends RuntimeException {

    public StreamEndedException() {
        super("video stream ended");
    }
}

