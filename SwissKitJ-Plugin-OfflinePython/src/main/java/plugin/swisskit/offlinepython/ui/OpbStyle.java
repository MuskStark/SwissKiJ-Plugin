package plugin.swisskit.offlinepython.ui;

import plugin.swisskit.offlinepython.domain.Status;

/**
 * Centralized SwissKitJ host "glass" design tokens and small style helpers for the
 * OfflinePython plugin. Token values mirror swisskit-common.css and
 * fan.summer.api.component.UiUtils so the plugin reads as part of the host shell.
 * Concrete values are used (not JavaFX looked-up color variables) because the plugin
 * root may not carry the .root style class.
 */
public final class OpbStyle {

    private OpbStyle() {}

    // Host glass tokens (mirror swisskit-common.css)
    public static final String ACCENT          = "#5b8cf7";
    public static final String ACCENT_SOFT     = "rgba(91,140,247,0.18)";
    public static final String GLASS_BG        = "rgba(255,255,255,0.055)";
    public static final String GLASS_BG_HOVER  = "rgba(255,255,255,0.09)";
    public static final String GLASS_BORDER    = "rgba(255,255,255,0.10)";
    public static final String TEXT_PRIMARY    = "rgba(255,255,255,0.92)";
    public static final String TEXT_SECONDARY  = "rgba(255,255,255,0.50)";
    public static final String TEXT_TERTIARY   = "rgba(255,255,255,0.40)";
    public static final String SUCCESS         = "#4cd97b";
    public static final String SUCCESS_SOFT    = "rgba(76,217,123,0.16)";
    public static final String WARNING         = "#f5a623";
    public static final String DANGER          = "#f25c5c";
    public static final String DANGER_SOFT     = "rgba(242,92,92,0.16)";
    public static final String LOG_INNER_BG    = "rgba(0,0,0,0.25)";

    public static final int CARD_RADIUS   = 12;
    public static final int NAV_RADIUS    = 8;
    public static final int SIDEBAR_WIDTH = 220;

    /** Glass card surface: translucent fill + hairline border + 12px radius. */
    public static String card() {
        return "-fx-background-color: " + GLASS_BG + ";"
             + "-fx-background-radius: " + CARD_RADIUS + ";"
             + "-fx-border-color: " + GLASS_BORDER + ";"
             + "-fx-border-radius: " + CARD_RADIUS + ";";
    }

    /** Nav item style for the given selection/hover state. */
    public static String navItem(boolean selected, boolean hover) {
        String bg = selected ? ACCENT_SOFT : (hover ? GLASS_BG_HOVER : "transparent");
        String fg = selected ? ACCENT : TEXT_SECONDARY;
        return "-fx-background-color: " + bg + ";"
             + "-fx-text-fill: " + fg + ";"
             + "-fx-background-radius: " + NAV_RADIUS + ";"
             + "-fx-cursor: hand;";
    }

    /** Python badge capsule style; green when ok, red when missing. */
    public static String badge(boolean ok) {
        return "-fx-background-color: " + (ok ? SUCCESS_SOFT : DANGER_SOFT) + ";"
             + "-fx-text-fill: " + (ok ? SUCCESS : DANGER) + ";"
             + "-fx-background-radius: 10;"
             + "-fx-padding: 4 10 4 10;";
    }

    /** Foreground color for a verify/doctor Status. */
    public static String statusColor(Status s) {
        if (s == null) return TEXT_SECONDARY;
        return switch (s) {
            case PASS -> SUCCESS;
            case WARN -> WARNING;
            case FAIL -> DANGER;
        };
    }

    /** Inline style for the log console TextArea. */
    public static String logTextAreaStyle() {
        return "-fx-control-inner-background: " + LOG_INNER_BG + ";"
             + "-fx-text-fill: " + TEXT_PRIMARY + ";"
             + "-fx-font-size: 12px;";
    }

    /** Small uppercase group-label (仓库操作 / 查看与工具). */
    public static String groupLabel() {
        return "-fx-text-fill: " + TEXT_TERTIARY + "; -fx-font-size: 10px; -fx-font-weight: bold;";
    }

    /** Disabled (V2/V3) nav item style. */
    public static String navItemDisabled() {
        return "-fx-background-color: transparent; -fx-text-fill: " + TEXT_TERTIARY
             + "; -fx-background-radius: " + NAV_RADIUS + ";";
    }

    /** Small count badge (deps 角标). */
    public static String countBadge() {
        return "-fx-background-color: " + ACCENT + "; -fx-text-fill: white;"
             + " -fx-background-radius: 9; -fx-padding: 0 7 0 7; -fx-font-size: 10px;";
    }

    /** Section-header title (for panel headers). */
    public static String sectionHeader() {
        return "-fx-text-fill: " + TEXT_PRIMARY + "; -fx-font-size: 15px; -fx-font-weight: 500;";
    }

    /** Data TableCell text style: 13px, given text color, optional bold / monospace, cell padding. */
    public static String tableCellStyle(String textColor, boolean bold, boolean mono) {
        String s = "-fx-text-fill: " + textColor + "; -fx-font-size: 13px; -fx-padding: 4 8 4 8;";
        if (bold) s += " -fx-font-weight: bold;";
        if (mono) s += " -fx-font-family: monospace;";
        return s;
    }

    /** TableRow background: zebra by index parity, accent-soft when selected. */
    public static String tableRowStyle(boolean odd, boolean selected) {
        String bg = selected ? ACCENT_SOFT : (odd ? GLASS_BG_HOVER : GLASS_BG);
        return "-fx-background-color: " + bg + ";";
    }

    /** Table header label style: secondary, 11px, bold. */
    public static String tableHeaderStyle() {
        return "-fx-text-fill: " + TEXT_SECONDARY + "; -fx-font-size: 11px; -fx-font-weight: bold;";
    }
}
