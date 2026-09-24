package com.hnscrcpy.stream;

/**
 * 编码视频流来源。
 */
public interface StreamProvider {

    /** 启动流；就绪/帧/错误通过 sink 异步回调。 */
    void start(FrameSink sink);

    /** 停止流并释放设备端资源；幂等。 */
    void stop();

    boolean isRunning();
}
