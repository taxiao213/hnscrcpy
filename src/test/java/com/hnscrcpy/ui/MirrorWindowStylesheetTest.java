package com.hnscrcpy.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** MirrorWindow 样式表 URL 的回归测试：data: URL 编码破坏 CSS 会导致样式静默丢失。 */
class MirrorWindowStylesheetTest {

    @Test
    @DisplayName("toolbarStylesheetUrl 解码后还原完整 CSS（空格不得变成 +）")
    void stylesheetUrl_decodesToOriginalCss() {
        String url = MirrorWindow.toolbarStylesheetUrl();

        assertThat(url).startsWith("data:text/css,");
        String decoded = URLDecoder.decode(url.substring("data:text/css,".length()), StandardCharsets.UTF_8);
        // 关键断言：解码后与原始 CSS 完全一致。URLEncoder 会把空格编成 '+'，
        // data: URL 中 '+' 是字面量，若未替换回 %20，选择器如 ".tool-pill {" 会损坏。
        assertThat(decoded).isEqualTo(MirrorWindow.toolbarCss());
        assertThat(decoded).contains(".tool-pill {");
        assertThat(decoded).contains(".menu-row:hover");
    }
}
