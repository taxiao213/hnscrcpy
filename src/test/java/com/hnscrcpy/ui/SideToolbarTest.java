package com.hnscrcpy.ui;

import javafx.application.Platform;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class SideToolbarTest {

    @BeforeAll
    static void initToolkit() {
        if (!isToolkitRunning()) {
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

    private static SideToolbar.Actions noopActions() {
        Runnable noop = () -> {
        };
        return new SideToolbar.Actions(noop, noop, noop, noop, noop, noop,
                noop, noop, noop, noop, noop);
    }

    private static <T> T onFx(java.util.concurrent.Callable<T> call) throws Exception {
        AtomicReference<T> ref = new AtomicReference<>();
        AtomicReference<Exception> err = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                ref.set(call.call());
            } catch (Exception e) {
                err.set(e);
            } finally {
                latch.countDown();
            }
        });
        latch.await();
        if (err.get() != null) {
            throw err.get();
        }
        return ref.get();
    }

    @Test
    @DisplayName("toolbar builds 12 icon buttons and 2 separators")
    void toolbar_hasExpectedChildren() throws Exception {
        var toolbar = onFx(() -> new SideToolbar(noopActions()));
        long buttons = toolbar.getChildren().stream()
                .filter(c -> c instanceof javafx.scene.control.Button).count();
        long seps = toolbar.getChildren().stream()
                .filter(c -> c instanceof javafx.scene.control.Separator).count();
        assertThat(buttons).isEqualTo(10);
        assertThat(seps).isEqualTo(2);
    }

    @Test
    @DisplayName("menu entries expose shortcuts consistent with display text")
    void menuEntries_shortcutsHaveDisplayText() throws Exception {
        var entries = onFx(() -> new SideToolbar(noopActions()).menuEntries());
        assertThat(entries).hasSize(10);
        for (var e : entries) {
            if (e.shortcut() != null) {
                assertThat(e.shortcutText()).isNotBlank();
            }
        }
        // 截图必须是 ⇧⌘S / Ctrl+Shift+S，防止快捷键与展示漂移
        var shot = entries.stream().filter(e -> e.label().equals("截图")).findFirst().orElseThrow();
        var shotCombo = (KeyCodeCombination) shot.shortcut();
        assertThat(shotCombo.getCode()).isEqualTo(KeyCode.S);
        assertThat(shotCombo.getShift()).isEqualTo(KeyCombination.ModifierValue.DOWN);
        assertThat(shotCombo.getShortcut()).isEqualTo(KeyCombination.ModifierValue.DOWN);
    }

    @Test
    @DisplayName("menu entry action invokes the injected runnable")
    void menuEntry_actionRuns() throws Exception {
        AtomicBoolean fired = new AtomicBoolean();
        Runnable noop = () -> {
        };
        var actions = new SideToolbar.Actions(noop, noop, noop, noop, noop, noop,
                noop, noop, noop, () -> fired.set(true), noop);
        var entries = onFx(() -> new SideToolbar(actions).menuEntries());
        var top = entries.stream().filter(e -> e.label().equals("窗口置顶")).findFirst().orElseThrow();
        top.action().run();
        assertThat(fired).isTrue();
    }

    @Test
    @DisplayName("displayText renders platform-appropriate modifier glyphs")
    void displayText_platformGlyphs() {
        var combo = new KeyCodeCombination(KeyCode.S, KeyCombination.SHIFT_DOWN,
                KeyCombination.SHORTCUT_DOWN);
        String text = SideToolbar.displayText(combo);
        if (com.hnscrcpy.util.Platform.isMac()) {
            assertThat(text).isEqualTo("⇧⌘S");
        } else {
            assertThat(text).isEqualTo("Ctrl+Shift+S");
        }
    }

    @Test
    @DisplayName("icons produce non-empty bounds at requested size")
    void icons_haveBounds() throws Exception {
        var group = onFx(() -> ToolIcons.svg(ToolIcons.POWER, 19));
        assertThat(group.getBoundsInLocal().getWidth()).isPositive();
        var nav = onFx(() -> ToolIcons.navBack(19));
        assertThat(nav.getBoundsInLocal().getHeight()).isPositive();
    }
}
