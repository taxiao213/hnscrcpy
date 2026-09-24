package com.hnscrcpy.cli;

/**
 * 命令行选项（手动解析，保持零依赖）。
 * 用法见 usage()。
 */
public record CliOptions(String serial, Integer maxSize, Integer bitRate, Integer fps,
                         boolean noControl, boolean help, boolean version) {

    public static CliOptions defaults() {
        return new CliOptions(null, null, null, null, false, false, false);
    }

    public static CliOptions parse(String[] args) {
        String serial = null;
        Integer maxSize = null;
        Integer bitRate = null;
        Integer fps = null;
        boolean noControl = false;
        boolean help = false;
        boolean version = false;

        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            switch (a) {
                case "-s", "--serial" -> serial = requireValue(args, ++i, a);
                case "-m", "--max-size" -> maxSize = parseInt(requireValue(args, ++i, a), a);
                case "-b", "--bit-rate" -> bitRate = parseInt(requireValue(args, ++i, a), a);
                case "--fps" -> fps = parseInt(requireValue(args, ++i, a), a);
                case "--no-control" -> noControl = true;
                case "-h", "--help" -> help = true;
                case "-v", "--version" -> version = true;
                default -> throw new IllegalArgumentException("未知选项: " + a + "（--help 查看用法）");
            }
        }
        return new CliOptions(serial, maxSize, bitRate, fps, noControl, help, version);
    }

    private static String requireValue(String[] args, int i, String opt) {
        if (i >= args.length) {
            throw new IllegalArgumentException("选项缺少参数: " + opt);
        }
        return args[i];
    }

    private static int parseInt(String v, String opt) {
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("选项 " + opt + " 需要整数，收到: " + v);
        }
    }

    public static String usage() {
        return """
                hnscrcpy — 鸿蒙 NEXT 投屏工具
                用法: hnscrcpy [选项]
                  -s, --serial <SN>     指定设备序列号（默认自动选择唯一设备）
                  -m, --max-size <px>   视频最大边长（预留，当前服务端恒为原始分辨率）
                  -b, --bit-rate <Mbps> 码率，默认 30
                      --fps <N>         帧率，默认 60
                      --no-control      只看不控（禁用输入转发）
                  -h, --help            显示本帮助
                  -v, --version         显示版本""";
    }
}
