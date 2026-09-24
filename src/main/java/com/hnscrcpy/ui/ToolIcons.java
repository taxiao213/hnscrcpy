package com.hnscrcpy.ui;

import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.Shape;

/**
 * 图标工厂：Material 风格 24dp 矢量路径 + 导航键线框图形。
 * 所有图标按内容包围盒归一化缩放到目标边长，保证视觉尺寸一致。
 */
public final class ToolIcons {

    public static final Color ICON_COLOR = Color.web("#3a3a40");

    // ---- 24dp 路径数据（Material Design） ----
    public static final String CLOSE =
            "M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19"
                    + " 19 17.59 13.41 12z";
    public static final String MENU =
            "M3 18h18v-2H3v2zm0-5h18v-2H3v2zm0-7v2h18V6H3z";
    public static final String CAMERA = "M9 3L7.17 5H4a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h16a2 2 0 0 0"
            + " 2-2V7a2 2 0 0 0-2-2h-3.17L15 3H9zM12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8z";
    public static final String ROTATE =
            "M12 5V2L7 6l5 4V7a5 5 0 1 1-5 5H5a7 7 0 1 0 7-7z";
    public static final String POWER = "M13 3h-2v10h2V3zm4.83 2.17l-1.42 1.42C17.99 7.87 19 9.83 19"
            + " 12c0 3.87-3.13 7-7 7s-7-3.13-7-7c0-2.17 1.01-4.13 2.58-5.42L6.17 5.17C4.29 6.85 3 9.27 3"
            + " 12c0 4.97 4.03 9 9 9s9-4.03 9-9c0-2.73-1.29-5.15-3.17-6.83z";
    public static final String VOL_UP = "M3 9v6h4l5 5V4L7 9H3zm13.5 3A4.5 4.5 0 0 0 14 7.97v8.05A4.5"
            + " 4.5 0 0 0 16.5 12zM14 3.23v2.06A7 7 0 0 1 19 12a7 7 0 0 1-5 6.71v2.06A9 9 0 0 0 21 12a9 9"
            + " 0 0 0-7-8.77z";
    public static final String VOL_DOWN = "M16.5 12A4.5 4.5 0 0 0 14 7.97v8.05A4.5 4.5 0 0 0 16.5"
            + " 12zM3 9v6h4l5 5V4L7 9H3z";
    public static final String PIN = "M16 9V4h1a1 1 0 0 0 0-2H7a1 1 0 0 0 0 2h1v5a4 4 0 0 1-2 3.44V15h6v6l1"
            + " 2 1-2v-6h6v-2.56A4 4 0 0 1 16 9z";
    public static final String INFO =
            "M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20zm1 15h-2v-6h2v6zm0-8h-2V7h2v2z";

    private ToolIcons() {
    }

    /** SVG 路径图标；evenOdd=true 时子路径按奇偶规则挖孔（如相机镜头）。
     *  按内容实际包围盒归一化缩放（而非 24dp 设计稿外框），保证各图标视觉尺寸一致。 */
    public static Group svg(String pathData, double size, boolean evenOdd) {
        SVGPath p = new SVGPath();
        p.setContent(pathData);
        p.setFill(ICON_COLOR);
        if (evenOdd) {
            p.setFillRule(javafx.scene.shape.FillRule.EVEN_ODD);
        }
        normalize(p, size);
        return new Group(p);
    }

    public static Group svg(String pathData, double size) {
        return svg(pathData, size, false);
    }

    /** 返回键：左指三角形线框。 */
    public static Group navBack(double size) {
        Polygon tri = new Polygon(4, -7, 4, 7, -6, 0);
        return outline(tri, size);
    }

    /** 主页键：圆形线框。 */
    public static Group navHome(double size) {
        return outline(new Circle(0, 0, 6.5), size);
    }

    /** 最近任务键：圆角方形线框。 */
    public static Group navRecent(double size) {
        Rectangle r = new Rectangle(-6, -6, 12, 12);
        r.setArcWidth(4);
        r.setArcHeight(4);
        return outline(r, size);
    }

    private static Group outline(Shape shape, double size) {
        shape.setFill(Color.TRANSPARENT);
        shape.setStroke(ICON_COLOR);
        shape.setStrokeWidth(1.7);
        normalize(shape, size);
        return new Group(shape);
    }

    /** 等比缩放使内容包围盒的最长边等于 size；视觉尺寸跨图标一致。 */
    private static void normalize(Shape shape, double size) {
        var b = shape.getBoundsInLocal();
        double s = size / Math.max(b.getWidth(), b.getHeight());
        shape.setScaleX(s);
        shape.setScaleY(s);
    }
}
