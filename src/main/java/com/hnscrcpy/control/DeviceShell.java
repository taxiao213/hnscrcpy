package com.hnscrcpy.control;

import com.hnscrcpy.device.HdcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.BiFunction;

/**
 * 设备 shell 通道：优先走桥内 executeShellCommand（既有连接，免本地进程开销），
 * 桥不可用时回退 hdc shell 子进程。仅负责传输层，命令语义由调用方保证。
 *
 * @Author: taxiao
 * WeChat：他晓
 * CSDN:http://blog.csdn.net/yin13753884368/article
 * Github:https://github.com/taxiao213
 */
public final class DeviceShell {

    private static final Logger log = LoggerFactory.getLogger(DeviceShell.class);

    /** 桥内通道（cmd, timeoutSec）→ 输出；null 表示无桥，直接走 hdc。 */
    private final BiFunction<String, Integer, String> primary;
    private final HdcClient hdc;
    private final String sn;

    public DeviceShell(BiFunction<String, Integer, String> primary, HdcClient hdc, String sn) {
        this.primary = primary;
        this.hdc = hdc;
        this.sn = sn;
    }

    /** 仅 hdc 子进程通道（无桥场景/测试）。 */
    public static DeviceShell overHdc(HdcClient hdc, String sn) {
        return new DeviceShell(null, hdc, sn);
    }

    /** 执行 shell 命令；桥通道抛异常时回退 hdc（记录 debug）。 */
    public String exec(String command, int timeoutSec) {
        if (primary != null) {
            try {
                String out = primary.apply(command, timeoutSec);
                return out == null ? "" : out;
            } catch (RuntimeException e) {
                log.debug("bridge shell failed, fallback to hdc: {}", e.toString());
            }
        }
        return hdc.shell(sn, command, timeoutSec);
    }
}
