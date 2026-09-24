package com.hnscrcpy.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 子进程执行封装：并发消费 stdout/stderr、超时强杀、结果不可变。
 * 所有 hdc 调用都经过这里。
 */
public final class ProcessRunner {

    private static final Logger log = LoggerFactory.getLogger(ProcessRunner.class);
    private static final ExecutorService GOBBLERS = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "process-gobbler");
        t.setDaemon(true);
        return t;
    });

    private ProcessRunner() {
    }

    public record Result(int exitCode, String stdout, String stderr, boolean timedOut, long durationMs) {
        public boolean ok() {
            return exitCode == 0 && !timedOut;
        }

        /** stdout 与 stderr 的合并视图（stdout 在前）。 */
        public String output() {
            return stderr.isBlank() ? stdout : stdout + "\n" + stderr;
        }
    }

    public static Result run(List<String> command, long timeout, TimeUnit unit) {
        long start = System.currentTimeMillis();
        String cmdLine = String.join(" ", command);
        log.debug("exec: {}", cmdLine);
        Process process = null;
        try {
            process = new ProcessBuilder(command).start();
            Process p = process;
            Future<String> outFuture = GOBBLERS.submit(() -> readAll(p.getInputStream()));
            Future<String> errFuture = GOBBLERS.submit(() -> readAll(p.getErrorStream()));
            boolean finished = process.waitFor(timeout, unit);
            long duration = System.currentTimeMillis() - start;
            if (!finished) {
                process.destroyForcibly();
                log.warn("exec timeout ({} {}): {}", timeout, unit, cmdLine);
                return new Result(-1, "", "", true, duration);
            }
            String stdout = getQuietly(outFuture);
            String stderr = getQuietly(errFuture);
            return new Result(process.exitValue(), stdout, stderr, false, duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (process != null) {
                process.destroyForcibly();
            }
            return new Result(-1, "", "interrupted", true, System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.warn("exec failed: {} -> {}", cmdLine, e.toString());
            return new Result(-1, "", String.valueOf(e), false, System.currentTimeMillis() - start);
        }
    }

    public static Result run(List<String> command) {
        return run(command, 30, TimeUnit.SECONDS);
    }

    private static String readAll(java.io.InputStream in) throws java.io.IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }

    private static String getQuietly(Future<String> future) {
        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "";
        } catch (ExecutionException | TimeoutException e) {
            return "";
        }
    }
}
