package com.hnscrcpy.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 看门狗静默判死矩阵（hosScrcpy 1.0.20：静态画面不推帧 ≠ 流死）：
 * 输入预言机快速判死 / 探活失败判死 / 静态画面不误杀 / 超长静默兜底。
 *
 * @Author: taxiao
 * WeChat：他晓
 */
class WatchdogDecisionTest {

    @Test
    @DisplayName("有输入活动 + 连续2轮静默 → 判死重连")
    void inputActive_twoSilentTicks_reconnect() {
        assertThat(MirrorSession.shouldReconnectOnSilence(true, 2, true)).isTrue();
        assertThat(MirrorSession.shouldReconnectOnSilence(true, 10, true)).isTrue();
    }

    @Test
    @DisplayName("有输入活动但仅1轮静默 → 再观察一轮")
    void inputActive_firstSilentTick_wait() {
        assertThat(MirrorSession.shouldReconnectOnSilence(true, 1, true)).isFalse();
    }

    @Test
    @DisplayName("探活失败 → 立即判死（无论静默轮数）")
    void probeDead_reconnectImmediately() {
        assertThat(MirrorSession.shouldReconnectOnSilence(false, 1, false)).isTrue();
        assertThat(MirrorSession.shouldReconnectOnSilence(false, 5, false)).isTrue();
    }

    @Test
    @DisplayName("探活正常 + 无输入 = 静态画面 → 不误杀")
    void staticScreen_neverReconnect() {
        for (int ticks = 1; ticks < MirrorSession.PASSIVE_RECONNECT_TICKS; ticks++) {
            assertThat(MirrorSession.shouldReconnectOnSilence(true, ticks, false))
                    .as("silentTicks=%d", ticks)
                    .isFalse();
        }
    }

    @Test
    @DisplayName("探活正常但静默累计到兜底轮数 → 重连（覆盖真·静默死亡）")
    void staticScreen_backstopReconnect() {
        assertThat(MirrorSession.shouldReconnectOnSilence(
                true, MirrorSession.PASSIVE_RECONNECT_TICKS, false)).isTrue();
        assertThat(MirrorSession.shouldReconnectOnSilence(
                true, MirrorSession.PASSIVE_RECONNECT_TICKS + 5, false)).isTrue();
    }
}
