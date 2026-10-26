package com.hnscrcpy.control;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * KeyCharMap 表完整性：全部 95 个可打印 ASCII 有映射、键码与真机标定值一致、
 * isFast 与开关语义一致。表来源：scripts/spike-keymap.sh 真机标定（2026-10-26）。
 *
 * @Author: taxiao
 * WeChat：他晓
 */
class KeyCharMapTest {

    @Test
    @DisplayName("all 95 printable ASCII chars are fast-path mapped")
    void mapping_fullPrintableAscii() {
        for (char c = 0x20; c <= 0x7E; c++) {
            assertThat(KeyCharMap.isFast(c)).as("char %s (0x%02x)", c, (int) c).isTrue();
        }
        // 中文等非 ASCII 无键码映射，走 inputText
        assertThat(KeyCharMap.isFast('中')).isFalse();
        assertThat(KeyCharMap.isFast('。')).isFalse();
    }

    @Test
    @DisplayName("keycodes match device calibration (digits/letters/space/punct)")
    void keycode_calibratedValues() {
        // 数字 2000-2009、字母 2017-2042（真机标定）
        assertThat(KeyCharMap.keyCode('0')).isEqualTo(2000);
        assertThat(KeyCharMap.keyCode('9')).isEqualTo(2009);
        assertThat(KeyCharMap.keyCode('a')).isEqualTo(2017);
        assertThat(KeyCharMap.keyCode('z')).isEqualTo(2042);
        // 空格 = 2050（真机标定，非文档值 2052）
        assertThat(KeyCharMap.keyCode(' ')).isEqualTo(2050);
        // 直接标点
        assertThat(KeyCharMap.keyCode(',')).isEqualTo(2043);
        assertThat(KeyCharMap.keyCode('.')).isEqualTo(2044);
        assertThat(KeyCharMap.keyCode('-')).isEqualTo(2057);
        assertThat(KeyCharMap.keyCode('/')).isEqualTo(2064);
        // 移位字符与同键基键共享键码
        assertThat(KeyCharMap.keyCode('!')).isEqualTo(KeyCharMap.keyCode('1'));
        assertThat(KeyCharMap.keyCode('?')).isEqualTo(KeyCharMap.keyCode('/'));
        assertThat(KeyCharMap.keyCode('A')).isEqualTo(KeyCharMap.keyCode('a'));
    }

    @Test
    @DisplayName("shift semantics: unshifted vs shifted sets are disjoint and correct")
    void shift_semantics() {
        for (char c = '0'; c <= '9'; c++) {
            assertThat(KeyCharMap.needsShift(c)).as("digit %s", c).isFalse();
        }
        for (char c = 'a'; c <= 'z'; c++) {
            assertThat(KeyCharMap.needsShift(c)).as("lower %s", c).isFalse();
        }
        for (char c = 'A'; c <= 'Z'; c++) {
            assertThat(KeyCharMap.needsShift(c)).as("upper %s", c).isTrue();
        }
        assertThat(KeyCharMap.needsShift(' ')).isFalse();
        assertThat(KeyCharMap.needsShift('-')).isFalse();
        assertThat(KeyCharMap.needsShift('_')).isTrue();
        assertThat(KeyCharMap.needsShift('!')).isTrue();
    }

    @Test
    @DisplayName("keycodes are unique per base key and in calibrated anchor ranges")
    void keycode_sanity() {
        Set<Integer> codes = new HashSet<>();
        for (char c = 0x20; c <= 0x7E; c++) {
            codes.add(KeyCharMap.keyCode(c));
        }
        // 基键键码数 = 10 数字 + 26 字母 + 空格 + 11 直接标点 = 48
        assertThat(codes).hasSize(48);
    }

    @Test
    @DisplayName("keyCode of unmapped char throws")
    void keyCode_unmapped_throws() {
        assertThatThrownBy(() -> KeyCharMap.keyCode('中'))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
