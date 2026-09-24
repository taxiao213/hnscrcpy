package com.hnscrcpy;

/**
 * 打包入口（jpackage 用）：JavaFX 以 classpath 方式加载时，
 * 主类不能直接继承 Application（否则报"缺少 JavaFX 运行时组件"）。
 * 委托 App.main 以保留 CLI 解析（--help/--version 直接退出）。
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        App.main(args);
    }
}
