package com.hnscrcpy.render;

import com.hnscrcpy.decode.VideoFrame;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelBuffer;
import javafx.scene.image.WritableImage;

import java.nio.IntBuffer;

/**
 * 零拷贝渲染：WritableImage + PixelBuffer，每帧仅一次数组拷贝进 IntBuffer。
 * 必须在 JavaFX 应用线程调用。
 */
public final class FrameRenderer {

    private final ImageView view = new ImageView();
    private WritableImage image;
    private PixelBuffer<IntBuffer> pixelBuffer;
    private IntBuffer buffer;

    public ImageView getView() {
        return view;
    }

    /** 渲染一帧；尺寸变化时重建缓冲。 */
    public void render(VideoFrame frame) {
        int w = frame.width();
        int h = frame.height();
        if (image == null || image.getWidth() != w || image.getHeight() != h) {
            buffer = IntBuffer.allocate(w * h);
            pixelBuffer = new PixelBuffer<>(w, h, buffer, javafx.scene.image.PixelFormat.getIntArgbPreInstance());
            image = new WritableImage(pixelBuffer);
            view.setImage(image);
        }
        buffer.clear();
        buffer.put(frame.pixels());
        pixelBuffer.updateBuffer(b -> null);
    }

    /** 渲染区域尺寸（未初始化返回 0x0）。 */
    public double renderedWidth() {
        return image == null ? 0 : image.getWidth();
    }

    public double renderedHeight() {
        return image == null ? 0 : image.getHeight();
    }
}
