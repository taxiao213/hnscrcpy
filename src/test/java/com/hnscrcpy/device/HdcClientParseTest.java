package com.hnscrcpy.device;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @Author: taxiao
 * WeChat：他晓
 * CSDN:http://blog.csdn.net/yin13753884368/article
 * Github:https://github.com/taxiao213
 */

class HdcClientParseTest {

    @Test
    @DisplayName("parseTargets skips empty markers and blank lines")
    void parseTargets_mixedOutput_returnsSerials() {
        List<String> serials = HdcClient.parseTargets("""
                [Empty]
                5KRUT25421010222
                127.0.0.1:5555

                """);
        assertThat(serials).containsExactly("5KRUT25421010222", "127.0.0.1:5555");
    }

    @Test
    @DisplayName("parseTargets returns empty list when no devices")
    void parseTargets_emptyOutput_returnsEmpty() {
        assertThat(HdcClient.parseTargets("[Empty]\n")).isEmpty();
        assertThat(HdcClient.parseTargets("")).isEmpty();
    }

    @Test
    @DisplayName("parseScreenSize extracts activeMode dimensions")
    void parseScreenSize_activeMode_returnsSize() {
        Optional<int[]> size = HdcClient.parseScreenSize(
                "activeMode: 1272x2860\nrefreshRate=60\n");
        assertThat(size).isPresent();
        assertThat(size.get()).containsExactly(1272, 2860);
    }

    @Test
    @DisplayName("parseScreenSize returns empty for unparseable output")
    void parseScreenSize_garbage_returnsEmpty() {
        assertThat(HdcClient.parseScreenSize("error: not supported")).isEmpty();
    }

    @Test
    @DisplayName("parseRefreshRate handles both refreshRate and refreshrate spellings")
    void parseRefreshRate_variants_returnsRate() {
        assertThat(HdcClient.parseRefreshRate("refreshRate=90")).contains(90);
        assertThat(HdcClient.parseRefreshRate("refreshrate=120")).contains(120);
        assertThat(HdcClient.parseRefreshRate("nothing here")).isEmpty();
    }
}
