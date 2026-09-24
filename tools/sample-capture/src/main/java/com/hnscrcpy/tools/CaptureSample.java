package com.hnscrcpy.tools;

import com.huawei.hosscrcpy.api.HosRemoteConfig;
import com.huawei.hosscrcpy.api.HosRemoteDevice;
import com.huawei.hosscrcpy.api.ScreenCapCallback;
import com.huawei.hosscrcpy.api.Size;

import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * M0 调研工具：从真机抓取 hosScrcpy H.264 流样本，用于帧格式分析（R5）与 JavaCV 解码 spike。
 *
 * 用法: CaptureSample <sn> <hdcPath> <output.h264> [seconds] [bitRateMbps]
 */
public final class CaptureSample {

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("usage: CaptureSample <sn> <hdcPath> <output.h264> [seconds] [bitRateMbps]");
            System.exit(1);
        }
        String sn = args[0];
        String hdcPath = args[1];
        String output = args[2];
        int seconds = args.length > 3 ? Integer.parseInt(args[3]) : 5;
        int bitRateMbps = args.length > 4 ? Integer.parseInt(args[4]) : 8;

        HosRemoteConfig config = new HosRemoteConfig(sn);
        config.setHdcPath(hdcPath);
        config.setFrameRate(30);
        config.setBitRate(bitRateMbps);

        HosRemoteDevice device = new HosRemoteDevice(config);
        CountDownLatch done = new CountDownLatch(1);
        AtomicLong totalBytes = new AtomicLong();
        AtomicLong totalCalls = new AtomicLong();

        FileOutputStream out = new FileOutputStream(output);
        long startAt = System.currentTimeMillis();

        device.startCaptureScreen(new ScreenCapCallback() {
            @Override
            public void onReady() {
                Size size = device.getScreenSize(false);
                System.out.println("[capture] onReady, screen=" + size.width + "x" + size.height);
            }

            @Override
            public void onData(ByteBuffer data) {
                long elapsed = System.currentTimeMillis() - startAt;
                if (elapsed > seconds * 1000L) {
                    return;
                }
                try {
                    byte[] buf = new byte[data.remaining()];
                    data.get(buf);
                    out.write(buf);
                    long n = totalCalls.incrementAndGet();
                    totalBytes.addAndGet(buf.length);
                    if (n <= 5 || n % 100 == 0) {
                        System.out.printf("[capture] chunk #%d size=%d head=%s%n", n, buf.length, hexHead(buf));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onException(Throwable e) {
                System.out.println("[capture] onException: " + e);
                done.countDown();
            }
        });

        Thread.sleep(seconds * 1000L + 1500L);
        device.stopCaptureScreen();
        out.close();

        long elapsed = System.currentTimeMillis() - startAt;
        System.out.printf("[capture] finished: chunks=%d bytes=%d elapsed=%dms throughput=%.2f Mbps%n",
                totalCalls.get(), totalBytes.get(), elapsed,
                totalBytes.get() * 8.0 / 1000 / 1000 / (elapsed / 1000.0));
        System.out.println("[capture] NAL analysis: " + analyzeAnnexB(output));
        done.countDown();
        System.exit(0);
    }

    private static String hexHead(byte[] buf) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(12, buf.length); i++) {
            sb.append(String.format("%02x ", buf[i]));
        }
        return sb.toString().trim();
    }

    /** 统计 Annex B 流中各 NAL 类型出现次数（起始码 00 00 01 / 00 00 00 01）。 */
    static Map<Integer, Integer> analyzeAnnexB(String path) throws Exception {
        Map<Integer, Integer> counts = new java.util.HashMap<>();
        byte[] data = java.nio.file.Files.readAllBytes(java.nio.file.Path.of(path));
        int i = 0;
        while (i + 4 < data.length) {
            int startCodeLen = 0;
            if (data[i] == 0 && data[i + 1] == 0 && data[i + 2] == 1) {
                startCodeLen = 3;
            } else if (i + 4 < data.length && data[i] == 0 && data[i + 1] == 0 && data[i + 2] == 0 && data[i + 3] == 1) {
                startCodeLen = 4;
            }
            if (startCodeLen > 0) {
                int nalType = data[i + startCodeLen] & 0x1F;
                counts.merge(nalType, 1, Integer::sum);
                i += startCodeLen;
            } else {
                i++;
            }
        }
        return counts;
    }
}
