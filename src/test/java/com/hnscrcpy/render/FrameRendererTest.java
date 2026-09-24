package com.hnscrcpy.render;

import com.hnscrcpy.decode.VideoFrame;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class FrameRendererTest {

    @BeforeAll
    static void initToolkit() {
        if (!Platform.isFxApplicationThread() && !isToolkitRunning()) {
            Platform.startup(() -> {
            });
        }
    }

    private static boolean isToolkitRunning() {
        try {
            Platform.runLater(() -> {
            });
            return true;
        } catch (IllegalStateException e) {
            return false;
        }
    }

    private static void onFxThread(Runnable r) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> err = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                r.run();
            } catch (Throwable t) {
                err.set(t);
            } finally {
                latch.countDown();
            }
        });
        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        if (err.get() != null) {
            throw new AssertionError(err.get());
        }
    }

    @Test
    @DisplayName("render publishes a WritableImage and reports dimensions")
    void render_publishesImage() throws Exception {
        FrameRenderer renderer = new FrameRenderer();
        int[] pixels = {0xFF112233, 0xFF445566, 0xFF778899, 0xFFAABBCC};
        onFxThread(() -> renderer.render(new VideoFrame(2, 2, pixels, 0, 1)));
        assertThat(renderer.renderedWidth()).isEqualTo(2);
        assertThat(renderer.renderedHeight()).isEqualTo(2);
        assertThat(renderer.getView().getImage()).isNotNull();
    }

    @Test
    @DisplayName("dimension change rebuilds the buffer")
    void render_dimensionChange_rebuilds() throws Exception {
        FrameRenderer renderer = new FrameRenderer();
        onFxThread(() -> {
            renderer.render(new VideoFrame(2, 2, new int[4], 0, 1));
            renderer.render(new VideoFrame(4, 2, new int[8], 0, 2));
        });
        assertThat(renderer.renderedWidth()).isEqualTo(4);
        assertThat(renderer.renderedHeight()).isEqualTo(2);
    }
}
