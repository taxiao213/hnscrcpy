package com.hnscrcpy.stream;

/**
 * 编码帧回调。onH264Frame 在 gRPC 线程上调用，实现方必须快速返回。
 *
 * @Author: taxiao
 * WeChat：他晓
 * CSDN:http://blog.csdn.net/yin13753884368/article
 * Github:https://github.com/taxiao213
 */

public interface FrameSink {

    /** 流就绪（首帧配置完成，可以开始解码）。 */
    void onStreamReady();

    /** 一个 Annex B H.264 帧（含起始码；首条消息含 SPS/PPS）。 */
    void onH264Frame(byte[] data);

    void onStreamError(Throwable t);

    /** 流正常结束或被对端关闭。 */
    void onStreamEnded();
}
