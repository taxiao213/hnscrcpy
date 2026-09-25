package com.hnscrcpy.render;

import com.hnscrcpy.decode.VideoFrame;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelBuffer;
import javafx.scene.image.WritableImage;

import java.nio.IntBuffer;

/**
 * 零拷贝渲染：WritableImage + PixelBuffer，每帧仅一次数组拷贝进 IntBuffer。
 * 必须在 JavaFX 应用线程调用。
 *
 * @Author: taxiao
 * WeChat：他晓
 * CSDN:http://blog.csdn.net/yin13753884368/article
 * Github:https://github.com/taxiao213
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
        // 必须在 updateBuffer 回调内写入并复位，上传按 position→limit 读取
        pixelBuffer.updateBuffer(pb -> {
            IntBuffer b = pb.getBuffer();
            b.clear();
            b.put(frame.pixels());
            b.rewind();
            return null;
        });
    }

    /** 渲染区域尺寸（未初始化返回 0x0）。 */
    public double renderedWidth() {
        return image == null ? 0 : image.getWidth();
    }

    public double renderedHeight() {
        return image == null ? 0 : image.getHeight();
    }

    /**
     * 建议的解码输出尺寸上限：视图实际显示尺寸 ×2（超采样保清晰，封顶解码原生）。
     * 视图尚未布局完成时返回 null（不限制）。
     */
    public int[] desiredOutputSize() {
        var b = view.getLayoutBounds();
        if (b.getWidth() < 1 || b.getHeight() < 1) {
            return null;
        }
        return new int[]{(int) Math.ceil(b.getWidth() * 2), (int) Math.ceil(b.getHeight() * 2)};
    }
}
