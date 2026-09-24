package com.hnscrcpy.ui;

import com.hnscrcpy.util.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.control.Button;
import javafx.scene.control.Separator;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;

import java.util.ArrayList;
import java.util.List;

/**
 * 右侧悬浮竖排工具条（白底圆角胶囊）：关闭 / 菜单 / 截图 / 旋转 / 电源 /
 * 音量± / 导航键（返回·主页·最近任务）。菜单键弹出带快捷键标注的下拉菜单。
 */
public final class SideToolbar extends VBox {

    /** 工具条与菜单共用的动作集。 */
    public record Actions(Runnable onClose, Runnable onScreenshot, Runnable onRotate,
                          Runnable onPower, Runnable onVolumeUp, Runnable onVolumeDown,
                          Runnable onBack, Runnable onHome, Runnable onRecent,
                          Runnable onToggleTop, Runnable onAbout) {
    }

    /** 菜单项：图标 + 文案 + 快捷键（可空）。MirrorWindow 用 shortcut 注册加速器。 */
    public record MenuEntry(String label, Group icon, KeyCombination shortcut,
                            String shortcutText, Runnable action) {
    }

    private static final double ICON = 19;
    private static final double MENU_ICON = 15;

    private final Actions actions;
    private final Popup menu = new Popup();

    public SideToolbar(Actions actions) {
        this.actions = actions;
        getStyleClass().add("tool-pill");
        setAlignment(Pos.CENTER);
        setSpacing(2);

        Button close = iconButton(ToolIcons.svg(ToolIcons.CLOSE, ICON), "关闭窗口");
        close.setOnAction(e -> actions.onClose().run());
        Button menuBtn = iconButton(ToolIcons.svg(ToolIcons.MENU, ICON), "菜单");
        menuBtn.setOnAction(e -> showMenu(menuBtn));
        Button shot = iconButton(ToolIcons.svg(ToolIcons.CAMERA, ICON, true), "截图");
        shot.setOnAction(e -> actions.onScreenshot().run());
        Button rotate = iconButton(ToolIcons.svg(ToolIcons.ROTATE, ICON), "旋转");
        rotate.setOnAction(e -> actions.onRotate().run());
        Button power = iconButton(ToolIcons.svg(ToolIcons.POWER, ICON), "电源");
        power.setOnAction(e -> actions.onPower().run());
        Button volUp = iconButton(ToolIcons.svg(ToolIcons.VOL_UP, ICON), "音量加");
        volUp.setOnAction(e -> actions.onVolumeUp().run());
        Button volDown = iconButton(ToolIcons.svg(ToolIcons.VOL_DOWN, ICON), "音量减");
        volDown.setOnAction(e -> actions.onVolumeDown().run());
        Button back = iconButton(ToolIcons.navBack(ICON), "返回");
        back.setOnAction(e -> actions.onBack().run());
        Button home = iconButton(ToolIcons.navHome(ICON), "主页");
        home.setOnAction(e -> actions.onHome().run());
        Button recent = iconButton(ToolIcons.navRecent(ICON), "最近任务");
        recent.setOnAction(e -> actions.onRecent().run());

        getChildren().addAll(close, menuBtn, hSep(), shot, rotate, power,
                volUp, volDown, hSep(), back, home, recent);
    }

    /** 菜单项列表（与下拉菜单、场景加速器共用同一定义）。 */
    public List<MenuEntry> menuEntries() {
        List<MenuEntry> entries = new ArrayList<>();
        entries.add(new MenuEntry("窗口置顶", ToolIcons.svg(ToolIcons.PIN, MENU_ICON),
                sc(KeyCode.T), null, actions.onToggleTop()));
        entries.add(new MenuEntry("截图", ToolIcons.svg(ToolIcons.CAMERA, MENU_ICON, true),
                sc(KeyCode.S, KeyCombination.SHIFT_DOWN), null, actions.onScreenshot()));
        entries.add(new MenuEntry("旋转", ToolIcons.svg(ToolIcons.ROTATE, MENU_ICON),
                sc(KeyCode.R), null, actions.onRotate()));
        entries.add(new MenuEntry("音量加", ToolIcons.svg(ToolIcons.VOL_UP, MENU_ICON),
                sc(KeyCode.EQUALS, KeyCombination.SHIFT_DOWN), null, actions.onVolumeUp()));
        entries.add(new MenuEntry("音量减", ToolIcons.svg(ToolIcons.VOL_DOWN, MENU_ICON),
                sc(KeyCode.MINUS), null, actions.onVolumeDown()));
        entries.add(new MenuEntry("返回", ToolIcons.navBack(MENU_ICON),
                sc(KeyCode.BACK_SPACE), null, actions.onBack()));
        entries.add(new MenuEntry("主页", ToolIcons.navHome(MENU_ICON),
                sc(KeyCode.H, KeyCombination.SHIFT_DOWN), null, actions.onHome()));
        entries.add(new MenuEntry("最近任务", ToolIcons.navRecent(MENU_ICON),
                sc(KeyCode.O, KeyCombination.SHIFT_DOWN), null, actions.onRecent()));
        entries.add(new MenuEntry("电源", ToolIcons.svg(ToolIcons.POWER, MENU_ICON),
                null, null, actions.onPower()));
        entries.add(new MenuEntry("关于 hnscrcpy", ToolIcons.svg(ToolIcons.INFO, MENU_ICON),
                null, null, actions.onAbout()));
        // 快捷键展示文本由 KeyCombination 生成，保持与注册一致
        return entries.stream()
                .map(e -> new MenuEntry(e.label(), e.icon(), e.shortcut(),
                        e.shortcut() != null ? displayText((KeyCodeCombination) e.shortcut()) : null,
                        e.action()))
                .toList();
    }

    private void showMenu(Button anchor) {
        if (menu.isShowing()) {
            menu.hide();
            return;
        }
        VBox box = new VBox();
        box.getStyleClass().add("menu-panel");
        boolean[] first = {true};
        for (MenuEntry entry : menuEntries()) {
            if (!first[0]) {
                Separator sep = new Separator();
                sep.getStyleClass().add("menu-sep");
                box.getChildren().add(sep);
            }
            first[0] = false;
            box.getChildren().add(menuRow(entry));
        }
        menu.getContent().setAll(box);
        menu.setAutoHide(true);
        var pos = anchor.localToScreen(anchor.getLayoutBounds().getMaxX() + 6, 0);
        menu.show(anchor.getScene().getWindow(), pos.getX(), pos.getY());
    }

    private HBox menuRow(MenuEntry entry) {
        var label = new javafx.scene.control.Label(entry.label());
        label.getStyleClass().add("menu-label");
        HBox row = new HBox(entry.icon(), label);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setSpacing(10);
        row.getStyleClass().add("menu-row");
        if (entry.shortcutText() != null) {
            var sc = new javafx.scene.control.Label(entry.shortcutText());
            sc.getStyleClass().add("menu-shortcut");
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            row.getChildren().addAll(spacer, sc);
        }
        row.setOnMouseClicked(e -> {
            menu.hide();
            entry.action().run();
        });
        return row;
    }

    private static Button iconButton(Group icon, String tooltip) {
        Button b = new Button();
        b.setGraphic(icon);
        b.getStyleClass().add("tool-btn");
        b.setTooltip(new javafx.scene.control.Tooltip(tooltip));
        b.setMinSize(36, 36);
        b.setPrefSize(36, 36);
        return b;
    }

    private static Separator hSep() {
        Separator sep = new Separator();
        sep.setPrefWidth(22);
        sep.getStyleClass().add("tool-sep");
        VBox.setMargin(sep, new Insets(3, 0, 3, 0));
        return sep;
    }

    /** 所有菜单快捷键都以 Shortcut（⌘/Ctrl）为基 modifier，与截图样式一致。 */
    private static KeyCombination sc(KeyCode code, KeyCombination.Modifier... mods) {
        KeyCombination.Modifier[] all = new KeyCombination.Modifier[mods.length + 1];
        all[0] = KeyCombination.SHORTCUT_DOWN;
        System.arraycopy(mods, 0, all, 1, mods.length);
        return new KeyCodeCombination(code, all);
    }

    /** macOS 用 ⌘⇧⌫ 符号，其他平台用 Ctrl+Shift 文本。 */
    static String displayText(KeyCodeCombination kc) {
        boolean mac = Platform.isMac();
        StringBuilder sb = new StringBuilder();
        if (mac) {
            if (kc.getShift() == KeyCombination.ModifierValue.DOWN) sb.append('⇧');
            if (kc.getShortcut() == KeyCombination.ModifierValue.DOWN) sb.append('⌘');
            if (kc.getAlt() == KeyCombination.ModifierValue.DOWN) sb.append('⌥');
        } else {
            if (kc.getShortcut() == KeyCombination.ModifierValue.DOWN) sb.append("Ctrl+");
            if (kc.getShift() == KeyCombination.ModifierValue.DOWN) sb.append("Shift+");
            if (kc.getAlt() == KeyCombination.ModifierValue.DOWN) sb.append("Alt+");
        }
        sb.append(switch (kc.getCode()) {
            case BACK_SPACE -> mac ? "⌫" : "Backspace";
            case EQUALS -> "=";
            case MINUS -> "-";
            default -> kc.getCode().getName();
        });
        return sb.toString();
    }

    /** 供测试与外部查询：当前菜单是否弹出。 */
    public boolean isMenuShowing() {
        return menu.isShowing();
    }
}
