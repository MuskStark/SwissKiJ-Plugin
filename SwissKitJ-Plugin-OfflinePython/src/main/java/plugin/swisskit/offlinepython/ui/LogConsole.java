package plugin.swisskit.offlinepython.ui;

import javafx.application.Platform;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;

import java.util.EnumSet;
import java.util.Set;

/**
 * Shared, collapsible log console. Lines are tagged with a {@link LogLevel} and filtered
 * by the visible-level set. Older convenience method {@link #log(String)} defaults to INFO.
 */
public class LogConsole extends BorderPane {
    private final TextArea area = new TextArea();
    private final Set<LogLevel> visible = EnumSet.allOf(LogLevel.class);
    private volatile boolean collapsed;

    public LogConsole() {
        area.setEditable(false);
        area.setWrapText(true);
        area.setStyle(OpbStyle.logTextAreaStyle());
        getStyleClass().add("content-scroll");
        setCenter(area);
        setPrefHeight(168);
    }

    /** Append a line at INFO level (back-compat for existing callers). */
    public void log(String line) { log(LogLevel.INFO, line); }

    /** Append a line at the given level, if that level is currently visible. */
    public void log(LogLevel level, String line) {
        String ts = java.time.LocalTime.now().withNano(0).toString();
        String prefix = switch (level) {
            case INFO -> "";
            case WARN -> "[WARN] ";
            case ERROR -> "[ERROR] ";
            case DEBUG -> "[DEBUG] ";
        };
        String rendered = "[" + ts + "] " + prefix + line + "\n";
        Platform.runLater(() -> {
            if (!visible.contains(level)) return;
            area.appendText(rendered);
            area.setScrollTop(Double.MAX_VALUE);
        });
    }

    public void setVisibleLevels(Set<LogLevel> levels) {
        this.visible.clear();
        this.visible.addAll(levels);
    }

    public void setCollapsed(boolean collapsed) {
        this.collapsed = collapsed;
        Platform.runLater(() -> setManaged(!collapsed));
    }

    public boolean isCollapsed() { return collapsed; }
}
