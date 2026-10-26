package com.hnscrcpy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 版本号单一真相源 = pom.xml：构建期 Maven 过滤进 app.properties，
 * App.VERSION 运行时读取。防止「关于页」与发布 tag 漂移（v1.0.2 曾显示 1.0.1）。
 *
 * @Author: taxiao
 * WeChat：他晓
 */
class AppVersionTest {

    @Test
    @DisplayName("VERSION equals filtered app.properties and looks like a release version")
    void version_matchesFilteredResource() throws Exception {
        Properties p = new Properties();
        try (InputStream in = getClass().getResourceAsStream("/app.properties")) {
            assertThat(in).as("app.properties must be on classpath").isNotNull();
            p.load(in);
        }
        String raw = p.getProperty("app.version");
        assertThat(raw).as("Maven resource filtering must inject pom version")
                .doesNotContain("${");
        assertThat(App.VERSION).isEqualTo(raw.trim());
        assertThat(App.VERSION).matches("\\d+\\.\\d+\\.\\d+.*");
    }
}
