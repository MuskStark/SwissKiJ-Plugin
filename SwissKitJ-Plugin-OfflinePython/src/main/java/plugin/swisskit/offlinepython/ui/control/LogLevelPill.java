package plugin.swisskit.offlinepython.ui.control;

import javafx.scene.control.Label;
import plugin.swisskit.offlinepython.ui.LogLevel;
import plugin.swisskit.offlinepython.ui.OpbStyle;

/**
 * 日志级别筛选 pill:点击 toggle on/off,选中态 accent-soft。
 *
 * <p>Click wiring is intentionally left to the consumer (LogDrawer) so the host
 * shell controls interaction lifecycle rather than the pill self-binding.
 */
public class LogLevelPill extends Label {
    private boolean on;
    private final LogLevel level;

    public LogLevelPill(LogLevel level, boolean initialOn) {
        super(level.name());
        this.level = level;
        this.on = initialOn;
        applyStyle();
    }

    public boolean isOn() { return on; }
    public LogLevel level() { return level; }

    public void toggle() {
        on = !on;
        applyStyle();
    }

    private void applyStyle() {
        setStyle(OpbStyle.logPillStyle(on) + " -fx-padding: 2 9 2 9;");
    }
}
