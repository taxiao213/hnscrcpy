package com.hnscrcpy.diag;

import com.hnscrcpy.diag.DiagnosticEvent.Type;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DiagnosticExportServiceTest {

    @Test
    @DisplayName("报告含设备信息、汇总与时间线表格；detail 中的竖线被转义")
    void renderReportContainsSectionsAndEscapesPipes() {
        DeviceDiagnostics d = new DeviceDiagnostics("SN-9", "nova 14 Ultra");
        d.setOnline(true);
        d.add(new DiagnosticEvent(Instant.now(), Type.STREAM_ERROR,
                "UNAVAILABLE: io | exception"));
        d.add(new DiagnosticEvent(Instant.now(), Type.WATCHDOG_STALL, "10000ms 无新帧"));

        String report = DiagnosticExportService.renderReport(d);

        assertThat(report)
                .contains("# 诊断报告 — nova 14 Ultra (SN-9)")
                .contains("- 在线状态: 在线")
                .contains("## 异常分类汇总")
                .contains("STREAM_ERROR")
                .contains("UNAVAILABLE: io \\| exception") // 竖线转义，防表格错位
                .contains("| 时间 | 类别 | 事件 | 详情 |");
    }

    @Test
    @DisplayName("空设备的报告无汇总条目但结构完整")
    void renderReportForEmptyDevice() {
        String report = DiagnosticExportService.renderReport(new DeviceDiagnostics("SN-0", "dev"));
        assertThat(report).contains("- 事件总数: 0").contains("## 事件时间线");
    }

    @Test
    @DisplayName("环境信息含版本、OS、导出设备清单")
    void renderEnvironmentListsDevices() {
        String env = DiagnosticExportService.renderEnvironment(Set.of("SN-9", "SN-8"));
        assertThat(env).contains("hnscrcpy").contains("OS:")
                .contains("SN-9").contains("SN-8");
    }

    @Test
    @DisplayName("建议文件名带时间戳且无非法字符")
    void suggestedFileNameHasTimestamp() {
        String name = DiagnosticExportService.suggestedFileName();
        assertThat(name).matches("hnscrcpy-diag-\\d{8}-\\d{6}\\.zip");
    }
}
