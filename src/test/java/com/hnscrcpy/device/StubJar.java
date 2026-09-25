package com.hnscrcpy.device;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/**
 * 测试期桩 jar：编译一组 com.huawei.hosscrcpy.api 桩类，
 * 让 HosScrcpyBridge / DeviceController / HosScrcpyStream 在不依赖真实 jar 与设备的情况下被测试。
 * 桩类通过系统属性 stub.log 记录调用（同一 JVM，子加载器共享系统属性）。
 *
 * @Author: taxiao
 * WeChat：他晓
 * CSDN:http://blog.csdn.net/yin13753884368/article
 * Github:https://github.com/taxiao213
 */

public final class StubJar {

    private static final String CONFIG_SRC = """
            package com.huawei.hosscrcpy.api;
            public class HosRemoteConfig {
                public HosRemoteConfig(String sn) { System.setProperty("stub.sn", sn); }
                public void setHdcPath(String v) { System.setProperty("stub.hdcPath", v); }
                public void setFrameRate(int v) { System.setProperty("stub.frameRate", String.valueOf(v)); }
                public void setBitRate(int v) { System.setProperty("stub.bitRate", String.valueOf(v)); }
                public void setIFrameInterval(int v) { System.setProperty("stub.iFrameInterval", String.valueOf(v)); }
                public void setScale(int v) { System.setProperty("stub.scale", String.valueOf(v)); }
            }
            """;

    private static final String CALLBACK_SRC = """
            package com.huawei.hosscrcpy.api;
            public interface ScreenCapCallback {
                void onData(java.nio.ByteBuffer data);
                void onException(Throwable t);
                void onReady();
            }
            """;

    private static final String DEVICE_SRC = """
            package com.huawei.hosscrcpy.api;
            public class HosRemoteDevice {
                private static void log(String s) {
                    System.setProperty("stub.log", System.getProperty("stub.log", "") + s + ";");
                }
                public HosRemoteDevice(HosRemoteConfig c) { log("init"); }
                public void startCaptureScreen(ScreenCapCallback cb) { log("start"); cb.onReady(); }
                public void stopCaptureScreen() { log("stop"); }
                public void requestIDRFrame() { log("idr"); }
                public void onTouchDown(int x, int y) { log("touchDown:" + x + "," + y); }
                public void onTouchMove(int x, int y) { log("touchMove:" + x + "," + y); }
                public void onTouchUp(int x, int y) { log("touchUp:" + x + "," + y); }
                public void onMouseDown(String b, int x, int y) { log("mouseDown:" + b + ":" + x + "," + y); }
                public void onMouseMove(String b, int x, int y) { log("mouseMove:" + b); }
                public void onMouseUp(String b, int x, int y) { log("mouseUp:" + b); }
                public void onMouseWheelUp(int x, int y) { log("wheelUp"); }
                public void onMouseWheelDown(int x, int y) { log("wheelDown"); }
                public void onMouseWheelStop(int x, int y) { log("wheelStop"); }
                public void setRotationHorizontal() { log("rotH"); }
                public void setRotationVertical() { log("rotV"); }
            }
            """;

    private StubJar() {
    }

    /** 编译桩类并打包成 jar。 */
    public static Path create(Path workDir) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("需要 JDK（系统 Java 编译器）运行测试");
        }
        Path srcDir = workDir.resolve("stub-src");
        Path clsDir = workDir.resolve("stub-cls");
        Files.createDirectories(srcDir.resolve("com/huawei/hosscrcpy/api"));
        Files.createDirectories(clsDir);
        write(srcDir.resolve("com/huawei/hosscrcpy/api/HosRemoteConfig.java"), CONFIG_SRC);
        write(srcDir.resolve("com/huawei/hosscrcpy/api/ScreenCapCallback.java"), CALLBACK_SRC);
        write(srcDir.resolve("com/huawei/hosscrcpy/api/HosRemoteDevice.java"), DEVICE_SRC);
        int rc = compiler.run(null, null, null,
                "-d", clsDir.toString(),
                srcDir.resolve("com/huawei/hosscrcpy/api/HosRemoteConfig.java").toString(),
                srcDir.resolve("com/huawei/hosscrcpy/api/ScreenCapCallback.java").toString(),
                srcDir.resolve("com/huawei/hosscrcpy/api/HosRemoteDevice.java").toString());
        if (rc != 0) {
            throw new IllegalStateException("桩类编译失败: " + rc);
        }
        Path jar = workDir.resolve("stub-hosScrcpy.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            try (var files = Files.walk(clsDir)) {
                for (Path p : files.filter(Files::isRegularFile).toList()) {
                    String entry = clsDir.relativize(p).toString().replace('\\', '/');
                    out.putNextEntry(new JarEntry(entry));
                    out.write(Files.readAllBytes(p));
                    out.closeEntry();
                }
            }
        }
        return jar;
    }

    private static void write(Path file, String content) throws IOException {
        Files.writeString(file, content);
    }

    /** 读取桩调用日志。 */
    public static String log() {
        return System.getProperty("stub.log", "");
    }

    public static void clearLog() {
        System.clearProperty("stub.log");
    }
}
