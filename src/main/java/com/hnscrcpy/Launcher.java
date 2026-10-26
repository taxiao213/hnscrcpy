package com.hnscrcpy;

/**
 * 打包入口（jpackage 用）：JavaFX 以 classpath 方式加载时，
 * 主类不能直接继承 Application（否则报"缺少 JavaFX 运行时组件"）。
 * 委托 App.main 以保留 CLI 解析（--help/--version 直接退出）。
 *
 * @Author: taxiao
 * WeChat：他晓
 * CSDN:http://blog.csdn.net/yin13753884368/article
 * Github:https://github.com/taxiao213
 */

public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        // 所有非 FX 线程的未捕获异常落盘（native 崩溃由 -XX:ErrorFile 覆盖，见打包脚本）
        Thread.setDefaultUncaughtExceptionHandler((t, e) ->
                org.slf4j.LoggerFactory.getLogger(Launcher.class)
                        .error("uncaught exception on thread {}", t.getName(), e));
        App.main(args);
    }
}
