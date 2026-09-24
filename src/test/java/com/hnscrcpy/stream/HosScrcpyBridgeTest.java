package com.hnscrcpy.stream;

import com.hnscrcpy.device.StubJar;
import com.hnscrcpy.stream.VideoConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class HosScrcpyBridgeTest {

    @TempDir
    static Path dir;
    private static Path stubJar;

    @BeforeAll
    static void createStub() throws Exception {
        stubJar = StubJar.create(dir);
    }

    @BeforeEach
    void clearLog() {
        StubJar.clearLog();
    }

    private static final class RecordingSink implements FrameSink {
        final List<byte[]> frames = new CopyOnWriteArrayList<>();
        volatile boolean ready;
        volatile Throwable error;

        @Override
        public void onStreamReady() {
            ready = true;
        }

        @Override
        public void onH264Frame(byte[] data) {
            frames.add(data);
        }

        @Override
        public void onStreamError(Throwable t) {
            error = t;
        }

        @Override
        public void onStreamEnded() {
        }
    }

    @Test
    @DisplayName("open passes sn/hdc/video params into stub config")
    void open_configuresDevice() {
        try (HosScrcpyBridge bridge = HosScrcpyBridge.open(
                stubJar, "SN99", "/opt/hdc", new VideoConfig(1600, 20, 90, 1000, 1))) {
            assertThat(System.getProperty("stub.sn")).isEqualTo("SN99");
            assertThat(System.getProperty("stub.hdcPath")).isEqualTo("/opt/hdc");
            assertThat(System.getProperty("stub.frameRate")).isEqualTo("90");
            assertThat(System.getProperty("stub.bitRate")).isEqualTo("20");
            assertThat(System.getProperty("stub.iFrameInterval")).isEqualTo("1000");
            assertThat(System.getProperty("stub.scale")).isEqualTo("1");
        }
    }

    @Test
    @DisplayName("callback proxy forwards onReady and copies ByteBuffer data")
    void callbackProxy_forwardsEvents() {
        try (HosScrcpyBridge bridge = HosScrcpyBridge.open(
                stubJar, "SN99", "/opt/hdc", VideoConfig.defaults())) {
            RecordingSink sink = new RecordingSink();
            Object callback = bridge.newCallback(sink);
            bridge.startCaptureScreen(callback);
            assertThat(sink.ready).isTrue();

            // 桩的 startCaptureScreen 只触发 onReady；数据面手动经反射调用代理验证
            byte[] payload = {0, 0, 0, 1, 0x65};
            invokeCallback(callback, "onData", ByteBuffer.class, ByteBuffer.wrap(payload));
            assertThat(sink.frames).hasSize(1);
            assertThat(sink.frames.get(0)).containsExactly(payload);

            invokeCallback(callback, "onException", Throwable.class, new RuntimeException("x"));
            assertThat(sink.error).hasMessage("x");
        }
    }

    private static void invokeCallback(Object proxy, String method, Class<?> paramType, Object arg) {
        try {
            Class<?> cbClass = Class.forName("com.huawei.hosscrcpy.api.ScreenCapCallback",
                    true, proxy.getClass().getClassLoader());
            cbClass.getMethod(method, paramType).invoke(proxy, arg);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    @DisplayName("invoke and lifecycle methods reach the stub device")
    void invoke_reachesDevice() {
        try (HosScrcpyBridge bridge = HosScrcpyBridge.open(
                stubJar, "SN99", "/opt/hdc", VideoConfig.defaults())) {
            bridge.invoke("onTouchDown", new Class<?>[]{int.class, int.class}, 3, 4);
            bridge.requestIDRFrame();
            bridge.stopCaptureScreen();
            assertThat(StubJar.log()).contains("touchDown:3,4").contains("idr").contains("stop");
        }
    }
}
