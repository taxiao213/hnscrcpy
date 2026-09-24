package com.hnscrcpy.tools;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * M0 spike：用 JavaCV 解码 hosScrcpy 抓取的裸 H.264 (Annex B) 流。
 * 成功标准：解出全部帧、宽高正确、能导出 PNG 目检。
 *
 * 用法: DecodeSpike <input.h264> <output.png>
 */
public final class DecodeSpike {

    public static void main(String[] args) throws Exception {
        String input = args[0];
        String output = args[1];

        FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(new File(input));
        grabber.setFormat("h264");
        grabber.start();

        System.out.println("[spike] opened: " + grabber.getImageWidth() + "x" + grabber.getImageHeight()
                + " format=" + grabber.getFormat() + " length=" + grabber.getLengthInFrames());

        Java2DFrameConverter converter = new Java2DFrameConverter();
        int frames = 0;
        Frame frame;
        BufferedImage last = null;
        long start = System.currentTimeMillis();
        while ((frame = grabber.grabImage()) != null) {
            frames++;
            last = converter.convert(frame);
        }
        long elapsed = System.currentTimeMillis() - start;

        System.out.println("[spike] decoded " + frames + " frames in " + elapsed + "ms ("
                + (frames * 1000.0 / Math.max(1, elapsed)) + " fps decode rate)");
        if (last != null) {
            ImageIO.write(last, "png", new File(output));
            System.out.println("[spike] wrote last frame -> " + output + " (" + last.getWidth() + "x" + last.getHeight() + ")");
        }
        grabber.stop();
        grabber.release();
        System.exit(0);
    }
}
