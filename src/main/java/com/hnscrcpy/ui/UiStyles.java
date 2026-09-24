package com.hnscrcpy.ui;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 内联 CSS → data: URL。
 * URLEncoder 把空格编成 '+'，而 data: URL 中 '+' 是字面量，会导致整条 CSS
 * 解析失败（样式静默丢失）——必须再替换回 %20。所有内联样式表统一走这里。
 */
final class UiStyles {

    private UiStyles() {
    }

    static String dataUrl(String css) {
        return "data:text/css," + URLEncoder.encode(css, StandardCharsets.UTF_8)
                .replace("+", "%20");
    }
}
