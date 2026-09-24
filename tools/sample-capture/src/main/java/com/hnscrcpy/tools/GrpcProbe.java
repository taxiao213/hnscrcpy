package com.hnscrcpy.tools;

import com.huawei.hosscrcpy.protocol.Scrcpy;
import com.huawei.hosscrcpy.protocol.ScrcpyServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.ClientCalls;

import java.util.Iterator;
import java.util.concurrent.TimeUnit;

/**
 * M0 调研工具：绕过 HosRemoteDevice，直连 scrcpy_grpc_socket 转发端口，
 * 分别测试 unary(onEnd) 与 server-streaming(onStart) 调用，打印完整错误信息。
 *
 * 用法: GrpcProbe <hdcPath> <sn> <localPort>
 * 前置：设备侧 scrcpy server 已启动（extension-name libscreen_casting.z.so）
 */
public final class GrpcProbe {

    public static void main(String[] args) throws Exception {
        String hdcPath = args[0];
        String sn = args[1];
        int localPort = Integer.parseInt(args[2]);

        // 建立端口转发: tcp:<localPort> -> localabstract:scrcpy_grpc_socket
        exec(hdcPath, "-t", sn, "fport", "tcp:" + localPort, "localabstract:scrcpy_grpc_socket");
        try {
            ManagedChannel channel = ManagedChannelBuilder
                    .forTarget("dns:///127.0.0.1:" + localPort)
                    .usePlaintext()
                    .maxInboundMessageSize(0x6400000)
                    .build();
            ScrcpyServiceGrpc.c stub = ScrcpyServiceGrpc.a(channel);
            Scrcpy.Empty empty = Scrcpy.Empty.getDefaultInstance();

            // 1) unary onEnd
            try {
                Scrcpy.ReplyEndMessage resp = ClientCalls.blockingUnaryCall(
                        stub.getChannel(), ScrcpyServiceGrpc.b(), stub.getCallOptions(), empty);
                System.out.println("[probe] onEnd OK, bytes=" + resp.getSerializedSize());
            } catch (StatusRuntimeException e) {
                System.out.println("[probe] onEnd FAIL: code=" + e.getStatus().getCode()
                        + " desc=" + e.getStatus().getDescription()
                        + " cause=" + e.getStatus().getCause()
                        + " trailers=" + e.getTrailers());
            }

            // 2) unary onRequestIDRFrame
            try {
                Scrcpy.ReplyEndMessage resp = ClientCalls.blockingUnaryCall(
                        stub.getChannel(), ScrcpyServiceGrpc.c(), stub.getCallOptions(), empty);
                System.out.println("[probe] onRequestIDRFrame OK, bytes=" + resp.getSerializedSize());
            } catch (StatusRuntimeException e) {
                System.out.println("[probe] onRequestIDRFrame FAIL: code=" + e.getStatus().getCode()
                        + " desc=" + e.getStatus().getDescription());
            }

            // 3) server-streaming onStart
            try {
                Iterator<Scrcpy.ReplyMessage> it = ClientCalls.blockingServerStreamingCall(
                        stub.getChannel(), ScrcpyServiceGrpc.a(), stub.getCallOptions(), empty);
                int n = 0;
                while (it.hasNext() && n < 5) {
                    Scrcpy.ReplyMessage msg = it.next();
                    System.out.println("[probe] onStart msg#" + (++n) + " keys=" + msg.getPayloadMap().keySet()
                            + " dataSize=" + msg.getPayloadMap().get("data").getValBytes().size());
                }
                System.out.println("[probe] onStart stream ended after " + n + " msgs");
            } catch (StatusRuntimeException e) {
                System.out.println("[probe] onStart FAIL: code=" + e.getStatus().getCode()
                        + " desc=" + e.getStatus().getDescription()
                        + " cause=" + e.getStatus().getCause()
                        + " trailers=" + e.getTrailers());
            }

            channel.shutdownNow();
            channel.awaitTermination(2, TimeUnit.SECONDS);
        } finally {
            exec(hdcPath, "-t", sn, "fport", "rm", "tcp:" + localPort, "localabstract:scrcpy_grpc_socket");
        }
        System.exit(0);
    }

    private static void exec(String... cmd) throws Exception {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        p.waitFor(10, TimeUnit.SECONDS);
        System.out.println("[exec] " + String.join(" ", cmd) + " -> " + out.trim());
    }
}
