package com.hnscrcpy.control;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CoordinateMapperTest {

    private final CoordinateMapper mapper = new CoordinateMapper(1272, 2860);

    @Test
    @DisplayName("portrait: exact-size view maps 1:1")
    void toDevice_exactView_mapsDirectly() {
        int[] p = mapper.toDevice(636, 1430, 1272, 2860);
        assertThat(p).containsExactly(636, 1430);
    }

    @Test
    @DisplayName("portrait: half-size view scales by 2")
    void toDevice_halfView_scales() {
        int[] p = mapper.toDevice(318, 715, 636, 1430);
        assertThat(p).containsExactly(636, 1430);
    }

    @Test
    @DisplayName("portrait: letterbox offset is compensated")
    void toDevice_letterbox_compensatesOffset() {
        // 视图 1000x2860：画面宽 1272→1000 缩放 0.786，无纵向留边
        int[] p = mapper.toDevice(500, 1430, 1000, 2860);
        assertThat(p[1]).isEqualTo(1430);
        assertThat(p[0]).isBetween(630, 642);
    }

    @Test
    @DisplayName("out-of-bounds view coords clamp to device edges")
    void toDevice_outOfBounds_clamps() {
        int[] p = mapper.toDevice(-50, 99999, 636, 1430);
        assertThat(p[0]).isEqualTo(0);
        assertThat(p[1]).isEqualTo(2859);
    }

    @Test
    @DisplayName("horizontal swaps display dimensions but passes coords through")
    void toDevice_horizontal_swapsAxes() {
        mapper.setHorizontal(true);
        // 横屏显示尺寸 2860x1272；视图同尺寸 → 1:1。
        // uitest 布局与触摸注入同为当前方向坐标空间（实测横屏 dumpLayout 根节点
        // [0,0][2860,1272]），因此坐标直通，不做逆旋转。
        int[] p = mapper.toDevice(100, 200, 2860, 1272);
        assertThat(p).containsExactly(100, 200);
        // 缩放 + 留边仍按横屏显示尺寸计算
        int[] q = mapper.toDevice(50, 100, 1430, 636);
        assertThat(q).containsExactly(100, 200);
    }

    @Test
    @DisplayName("horizontal: out-of-bounds clamps to landscape display edges")
    void toDevice_horizontal_clamps() {
        mapper.setHorizontal(true);
        int[] p = mapper.toDevice(-50, 99999, 2860, 1272);
        assertThat(p[0]).isEqualTo(0);
        assertThat(p[1]).isEqualTo(1271);
    }
}
