package com.hnscrcpy.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * 用户配置持久化：~/.hnscrcpy/config.properties。
 *
 * @Author: taxiao
 * WeChat：他晓
 * CSDN:http://blog.csdn.net/yin13753884368/article
 * Github:https://github.com/taxiao213
 */

public final class AppConfig {

    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);

    private final Path file;
    private String lastSerial = "";
    private int bitRateMbps = 30;
    private int fps = 60;
    private boolean noControl;

    private AppConfig(Path file) {
        this.file = file;
    }

    public static AppConfig load() {
        return load(Platform.userHomeDir().resolve("config.properties"));
    }

    /** 路径可注入，便于测试。 */
    public static AppConfig load(Path file) {
        AppConfig cfg = new AppConfig(file);
        if (!Files.isRegularFile(file)) {
            return cfg;
        }
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            p.load(in);
            cfg.lastSerial = p.getProperty("lastSerial", "");
            cfg.bitRateMbps = parseInt(p.getProperty("bitRateMbps"), 30);
            cfg.fps = parseInt(p.getProperty("fps"), 60);
            cfg.noControl = Boolean.parseBoolean(p.getProperty("noControl", "false"));
        } catch (IOException e) {
            log.warn("load config failed, using defaults: {}", e.toString());
        }
        return cfg;
    }

    private static int parseInt(String v, int fallback) {
        try {
            return Integer.parseInt(v.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    public void save() {
        Properties p = new Properties();
        p.setProperty("lastSerial", lastSerial);
        p.setProperty("bitRateMbps", String.valueOf(bitRateMbps));
        p.setProperty("fps", String.valueOf(fps));
        p.setProperty("noControl", String.valueOf(noControl));
        try {
            Files.createDirectories(file.getParent());
            try (OutputStream out = Files.newOutputStream(file)) {
                p.store(out, "hnscrcpy config");
            }
        } catch (IOException e) {
            log.warn("save config failed: {}", e.toString());
        }
    }

    public String lastSerial() {
        return lastSerial;
    }

    public void setLastSerial(String sn) {
        this.lastSerial = sn == null ? "" : sn;
    }

    public int bitRateMbps() {
        return bitRateMbps;
    }

    public void setBitRateMbps(int v) {
        this.bitRateMbps = v;
    }

    public int fps() {
        return fps;
    }

    public void setFps(int v) {
        this.fps = v;
    }

    public boolean noControl() {
        return noControl;
    }

    public void setNoControl(boolean v) {
        this.noControl = v;
    }
}
