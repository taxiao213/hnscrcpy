package com.hnscrcpy.control;

import java.util.HashMap;
import java.util.Map;

/**
 * ASCII 字符 → OpenHarmony uinput 键码映射（键盘输入快通道）。
 *
 * <p><b>全表已经真机标定</b>（scripts/spike-keymap.sh，MRT-AL10 / HarmonyOS NEXT，
 * 2026-10-26）：数字 2000-2009、字母 2017-2042、空格 2050、Shift=2047、
 * 标点 2043/2044/2056-2064；Shift 组合键（单命令多段 -d/-u）实测可用，
 * 移位符号按 US 布局（shift+1→'!'、shift+-→'_'、shift+/→'?' 等已验证）。
 * 未映射字符返回 false，调用方回退 inputText 批量注入（中文 IME 上屏文本
 * 始终走 inputText）。</p>
 *
 * @Author: taxiao
 * WeChat：他晓
 */
public final class KeyCharMap {

    /** 快通道总开关：-Dhnscrcpy.keyFastPath=false 关闭（全部回退 inputText 批量注入）。 */
    static final boolean FAST_PATH_ENABLED =
            Boolean.parseBoolean(System.getProperty("hnscrcpy.keyFastPath", "true"));

    /**
     * uinput 单命令内多段 -d/-u（Shift+字符 组合键）——真机已验证可用，
     * 默认开启；异常设备可用 -Dhnscrcpy.keyCombinedSeq=false 回退。
     */
    static final boolean COMBINED_SEQUENCE_OK =
            Boolean.parseBoolean(System.getProperty("hnscrcpy.keyCombinedSeq", "true"));

    /** Shift（左）键码——真机标定值。 */
    static final int KEY_SHIFT_LEFT = 2047;

    private record Mapping(int keyCode, boolean shift) {
    }

    private static final Map<Character, Mapping> MAP = buildMap();

    private KeyCharMap() {
    }

    private static Map<Character, Mapping> buildMap() {
        Map<Character, Mapping> m = new HashMap<>();
        // 数字行：0-9 = 2000-2009
        for (int i = 0; i <= 9; i++) {
            m.put((char) ('0' + i), new Mapping(2000 + i, false));
        }
        // 字母：a-z = 2017-2042，大写 = 同键码 + Shift
        for (int i = 0; i < 26; i++) {
            m.put((char) ('a' + i), new Mapping(2017 + i, false));
            m.put((char) ('A' + i), new Mapping(2017 + i, true));
        }
        // 空格 = 2050（真机标定，非文档值 2052）
        m.put(' ', new Mapping(2050, false));
        // 直接标点（真机标定）
        m.put(',', new Mapping(2043, false));
        m.put('.', new Mapping(2044, false));
        m.put('`', new Mapping(2056, false));
        m.put('-', new Mapping(2057, false));
        m.put('=', new Mapping(2058, false));
        m.put('[', new Mapping(2059, false));
        m.put(']', new Mapping(2060, false));
        m.put('\\', new Mapping(2061, false));
        m.put(';', new Mapping(2062, false));
        m.put('\'', new Mapping(2063, false));
        m.put('/', new Mapping(2064, false));
        // Shift+数字 符号（US 布局，真机验证）：) ! @ # $ % ^ & * (
        char[] shiftedDigits = ")!@#$%^&*(".toCharArray();
        for (int i = 0; i <= 9; i++) {
            m.put(shiftedDigits[i], new Mapping(2000 + i, true));
        }
        // Shift+标点（US 布局）：< > ? : " { } | ~ _ +
        m.put('<', new Mapping(2043, true));
        m.put('>', new Mapping(2044, true));
        m.put('~', new Mapping(2056, true));
        m.put('_', new Mapping(2057, true));
        m.put('+', new Mapping(2058, true));
        m.put('{', new Mapping(2059, true));
        m.put('}', new Mapping(2060, true));
        m.put('|', new Mapping(2061, true));
        m.put(':', new Mapping(2062, true));
        m.put('"', new Mapping(2063, true));
        m.put('?', new Mapping(2064, true));
        return m;
    }

    /** 该字符可否走 uinput 快通道。 */
    public static boolean isFast(char c) {
        if (!FAST_PATH_ENABLED) {
            return false;
        }
        Mapping m = MAP.get(c);
        if (m == null) {
            return false;
        }
        return !m.shift() || COMBINED_SEQUENCE_OK;
    }

    /** 字符键码（调用前须 isFast 确认）。 */
    public static int keyCode(char c) {
        Mapping m = MAP.get(c);
        if (m == null) {
            throw new IllegalArgumentException("no keycode mapping for char: " + (int) c);
        }
        return m.keyCode();
    }

    /** 是否需要 Shift 组合。 */
    public static boolean needsShift(char c) {
        Mapping m = MAP.get(c);
        return m != null && m.shift();
    }

    /** 映射表大小（测试用）。 */
    static int size() {
        return MAP.size();
    }
}
