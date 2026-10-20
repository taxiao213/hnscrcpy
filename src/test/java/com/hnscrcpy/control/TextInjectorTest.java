package com.hnscrcpy.control;

import com.hnscrcpy.device.HdcClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TextInjector 攒批 / 分片 / 命令构造的单元测试（纯逻辑，无设备依赖）。
 *
 * @Author: taxiao
 * WeChat：他晓
 * CSDN:http://blog.csdn.net/yin13753884368/article
 * Github:https://github.com/taxiao213
 */

class TextInjectorTest {

    /** 记录 shell 调用的假 hdc（shell 全部覆写，runner 永不触发）。 */
    private static class RecordingHdc extends HdcClient {
        final ConcurrentLinkedQueue<String> commands = new ConcurrentLinkedQueue<>();

        RecordingHdc() {
            super(Path.of("/fake/hdc"));
        }

        @Override
        public String shell(String sn, String command, long timeoutSec) {
            commands.add(command);
            return "OK";
        }
    }

    private static TextInjector newInjector(RecordingHdc hdc, int x, int y) {
        TextInjector injector = new TextInjector(hdc, "SN99");
        injector.setAnchor(x, y);
        return injector;
    }

    @Test
    @DisplayName("buildInputCommand: 单引号按 shell 规则转义，控制字符剔除")
    void buildInputCommand_escapesQuotes() {
        String cmd = TextInjector.buildInputCommand(100, 200, "it's a \"test\"");
        assertThat(cmd).isEqualTo("uitest uiInput inputText 100 200 'it'\\''s a \"test\"'");
    }

    @Test
    @DisplayName("buildInputCommand: 中文原样保留")
    void buildInputCommand_keepsChinese() {
        String cmd = TextInjector.buildInputCommand(0, 0, "你好世界");
        assertThat(cmd).isEqualTo("uitest uiInput inputText 0 0 '你好世界'");
    }

    @Test
    @DisplayName("slices: 换行拆段，段间补 ENTER 标记（含末尾换行=注入后回车）")
    void slices_splitsNewlines() {
        List<String> slices = TextInjector.slices("ab\ncd");
        assertThat(slices).containsExactly("ab", "\n", "cd");
        assertThat(TextInjector.slices("ab\n")).containsExactly("ab", "\n");
        assertThat(TextInjector.slices("")).isEmpty();
        assertThat(TextInjector.slices("\n")).containsExactly("\n");
    }

    @Test
    @DisplayName("slices: 超长文本按 MAX_SLICE 分片")
    void slices_chunksLongText() {
        String longText = "a".repeat(1500);
        List<String> slices = TextInjector.slices(longText);
        assertThat(slices).hasSize(1);
        assertThat(slices.get(0)).hasSize(1500);

        String longer = longText + "b".repeat(600); // 2100 chars
        slices = TextInjector.slices(longer);
        assertThat(slices).hasSize(2);
        assertThat(slices.get(0)).hasSize(TextInjector.MAX_SLICE);
        assertThat(slices.get(1)).hasSize(2100 - TextInjector.MAX_SLICE);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("append 攒批：停顿后一次性注入整段")
    void append_batchesThenFlushes() throws Exception {
        RecordingHdc hdc = new RecordingHdc();
        try (TextInjector injector = newInjector(hdc, 100, 200)) {
            injector.append("he");
            injector.append("llo");
            Thread.sleep(TextInjector.IDLE_FLUSH_MS + 300);
            assertThat(hdc.commands).containsExactly(
                    "uitest uiInput inputText 100 200 'hello'");
        }
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("injectNow: 立即注入，与打字缓冲合并且顺序正确")
    void injectNow_flushesBufferFirst() throws Exception {
        RecordingHdc hdc = new RecordingHdc();
        try (TextInjector injector = newInjector(hdc, 5, 6)) {
            injector.append("abc");
            injector.injectNow("PASTE");
            Thread.sleep(TextInjector.IDLE_FLUSH_MS + 500);
            assertThat(hdc.commands).containsExactly(
                    "uitest uiInput inputText 5 6 'abc'",
                    "uitest uiInput inputText 5 6 'PASTE'");
        }
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("无锚点时丢弃并告警，不崩溃")
    void noAnchor_dropsGracefully() throws Exception {
        RecordingHdc hdc = new RecordingHdc();
        try (TextInjector injector = new TextInjector(hdc, "SN99")) {
            injector.append("orphan");
            injector.injectNow("paste");
            Thread.sleep(300);
            assertThat(hdc.commands).isEmpty();
        }
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("close 后追加与注入静默忽略")
    void close_isSafe() {
        RecordingHdc hdc = new RecordingHdc();
        TextInjector injector = newInjector(hdc, 1, 2);
        injector.close();
        injector.append("x");
        injector.injectNow("y");
        injector.flushSync();
        assertThat(hdc.commands).isEmpty();
    }
}
