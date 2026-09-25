package com.hnscrcpy.stream;

import com.hnscrcpy.session.SessionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.ByteBuffer;
import java.nio.file.Path;

/**
 * hosScrcpy jar 的反射桥。jar 无再分发授权、且自带 grpc/netty/protobuf
 * （与外部依赖冲突，见 docs/HOS_SCRCPY_PROTOCOL.md），因此：
 * - 编译期零依赖，运行期通过 URLClassLoader 加载；
 * - 父加载器为系统类加载器（本应用不含 grpc/netty/protobuf，委托落空后由 jar 自带实现加载）；
 * - ScreenCapCallback 接口用动态代理实现。
 *
 * @Author: taxiao
 * WeChat：他晓
 * CSDN:http://blog.csdn.net/yin13753884368/article
 * Github:https://github.com/taxiao213
 */

public final class HosScrcpyBridge implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(HosScrcpyBridge.class);
    private static final String PKG = "com.huawei.hosscrcpy.api.";

    private final URLClassLoader loader;
    private final Object device;

    private HosScrcpyBridge(URLClassLoader loader, Object device) {
        this.loader = loader;
        this.device = device;
    }

    /** 打开设备桥：构造 HosRemoteConfig + HosRemoteDevice（会触发设备检查）。 */
    public static HosScrcpyBridge open(Path jar, String sn, String hdcPath, VideoConfig cfg) {
        try {
            URLClassLoader loader = new URLClassLoader(
                    new URL[]{jar.toUri().toURL()}, ClassLoader.getSystemClassLoader());
            Class<?> configClass = loader.loadClass(PKG + "HosRemoteConfig");
            Object config = configClass.getConstructor(String.class).newInstance(sn);
            configClass.getMethod("setHdcPath", String.class).invoke(config, hdcPath);
            configClass.getMethod("setFrameRate", int.class).invoke(config, cfg.fps());
            configClass.getMethod("setBitRate", int.class).invoke(config, cfg.bitRateMbps());
            configClass.getMethod("setIFrameInterval", int.class).invoke(config, cfg.iFrameIntervalMs());
            configClass.getMethod("setScale", int.class).invoke(config, cfg.scale());
            Class<?> deviceClass = loader.loadClass(PKG + "HosRemoteDevice");
            Object device = deviceClass.getConstructor(configClass).newInstance(config);
            log.info("hosScrcpy bridge opened for {} (fps={}, bitrate={}Mbps)", sn, cfg.fps(), cfg.bitRateMbps());
            return new HosScrcpyBridge(loader, device);
        } catch (ReflectiveOperationException | java.io.IOException e) {
            throw new SessionException("初始化 hosScrcpy 桥失败: " + e, e);
        }
    }

    /** 创建 ScreenCapCallback 动态代理。 */
    public Object newCallback(FrameSink sink) {
        try {
            Class<?> cbClass = loader.loadClass(PKG + "ScreenCapCallback");
            InvocationHandler handler = (proxy, method, args) -> dispatch(method, args, sink);
            return Proxy.newProxyInstance(loader, new Class<?>[]{cbClass}, handler);
        } catch (ClassNotFoundException e) {
            throw new SessionException("hosScrcpy jar 缺少 ScreenCapCallback 接口", e);
        }
    }

    private static Object dispatch(Method method, Object[] args, FrameSink sink) {
        switch (method.getName()) {
            case "onReady" -> sink.onStreamReady();
            case "onData" -> {
                ByteBuffer bb = (ByteBuffer) args[0];
                byte[] data = new byte[bb.remaining()];
                bb.duplicate().get(data);
                sink.onH264Frame(data);
            }
            case "onException" -> sink.onStreamError((Throwable) args[0]);
            default -> {
                // Object 方法（toString/hashCode/equals）不转发
            }
        }
        return null;
    }

    /** 反射调用 device 的无参/有参方法。 */
    public Object invoke(String name, Class<?>[] signature, Object... args) {
        try {
            Method m = device.getClass().getMethod(name, signature);
            return m.invoke(device, args);
        } catch (ReflectiveOperationException e) {
            throw new SessionException("调用 hosScrcpy 方法失败: " + name, e);
        }
    }

    public void startCaptureScreen(Object callback) {
        invoke("startCaptureScreen", new Class<?>[]{callback.getClass().getInterfaces()[0]}, callback);
    }

    public void stopCaptureScreen() {
        invoke("stopCaptureScreen", new Class<?>[0]);
    }

    public void requestIDRFrame() {
        invoke("requestIDRFrame", new Class<?>[0]);
    }

    @Override
    public void close() {
        try {
            loader.close();
        } catch (java.io.IOException e) {
            log.debug("close loader failed: {}", e.toString());
        }
    }
}
