package plugin.swisskit.offlinepython.ui;

import plugin.swisskit.offlinepython.domain.Status;

/**
 * Centralized SwissKitJ host design tokens and small style helpers for the
 * OfflinePython plugin.
 *
 * <p>All colors are <strong>{@code -sk-*} looked-up color tokens</strong> (resolved
 * per-theme against {@code .theme-dark}/.theme-light} on the scene root) — never
 * inline hex or {@code rgba()} literals, which would freeze on one theme. The
 * plugin's view is embedded in the host scene (and the common stylesheet is
 * explicitly added in {@code CommandShell}), so the tokens resolve correctly.
 *
 * <p>Token values mirror {@code swisskit-common.css} and
 * {@code fan.summer.api.component.UiUtils} so the plugin reads as part of the host
 * shell.
 */
public final class OpbStyle {

    private OpbStyle() {}

    // Host glass tokens — -sk-* looked-up colors (resolve per-theme on the scene root)
    public static final String ACCENT          = "-sk-accent";
    public static final String ACCENT_SOFT     = "-sk-accent-soft";
    public static final String GLASS_BG        = "-sk-bg-elevated";
    public static final String GLASS_BG_HOVER  = "-sk-bg-hover";
    public static final String GLASS_BG_SOFT   = "-sk-bg";
    public static final String GLASS_BORDER    = "-sk-border";
    public static final String BORDER_STRONG   = "-sk-border-strong";
    public static final String TEXT_PRIMARY    = "-sk-text";
    public static final String TEXT_SECONDARY  = "-sk-text-secondary";
    public static final String TEXT_TERTIARY   = "-sk-text-disabled";
    public static final String SUCCESS         = "-sk-success";
    public static final String SUCCESS_SOFT    = "-sk-success-soft";
    public static final String WARNING         = "-sk-warning";
    public static final String WARNING_SOFT    = "-sk-warning-soft";
    public static final String DANGER          = "-sk-danger";
    public static final String DANGER_SOFT     = "-sk-danger-soft";
    public static final String BG_SELECTED     = "-sk-bg-selected";
    public static final String LOG_INNER_BG    = "-sk-bg";

    public static final int CARD_RADIUS   = 8;   // 规范 §3.4:卡片/表格 8px(原 12 越界)
    public static final int NAV_RADIUS    = 6;   // 规范 §3.4:控件 6px(原 8)
    public static final int SIDEBAR_WIDTH      = 200;   // 原 220,4 倍数
    public static final int LOG_DRAWER_WIDTH   = 240;
    public static final int LOG_DRAWER_COLLAPSED_WIDTH = 40;

    /** Glass card surface: elevated fill + hairline border + 8px radius. */
    public static String card() {
        return "-fx-background-color: " + GLASS_BG + ";"
             + "-fx-background-radius: " + CARD_RADIUS + ";"
             + "-fx-border-color: " + GLASS_BORDER + ";"
             + "-fx-border-radius: " + CARD_RADIUS + ";";
    }

    /**
     * Nav item style per spec S1: idle = transparent + secondary text;
     * hover = -sk-bg-hover + -sk-text; selected = neutral -sk-bg-selected fill
     * + 3px LEFT -sk-accent border + -sk-text text. Never blue-flood.
     */
    public static String navItem(boolean selected, boolean hover) {
        String bg = selected ? BG_SELECTED : (hover ? GLASS_BG_HOVER : "transparent");
        String fg = selected ? TEXT_PRIMARY : (hover ? TEXT_PRIMARY : TEXT_SECONDARY);
        String border = selected
                ? "-fx-border-color: transparent transparent transparent " + ACCENT + ";"
                  + " -fx-border-width: 0 0 0 3;"
                : "-fx-border-color: transparent; -fx-border-width: 0;";
        return "-fx-background-color: " + bg + ";"
             + " -fx-text-fill: " + fg + ";"
             + " " + border
             + " -fx-background-radius: " + NAV_RADIUS + ";"
             + " -fx-cursor: hand;";
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

    /** Small count badge (deps 角标). */
    public static String countBadge() {
        return "-fx-background-color: " + ACCENT + "; -fx-text-fill: white;"
             + " -fx-background-radius: 6; -fx-padding: 0 6 0 6; -fx-font-size: 10px;";
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

    /** TopBar container: base bg + bottom 1px border + 8/16 padding(网格). */
    public static String topBar() {
        return "-fx-background-color: " + GLASS_BG_SOFT + ";"
             + "-fx-border-color: transparent transparent " + GLASS_BORDER + " transparent;"
             + "-fx-border-width: 0 0 1 0;";
    }

    /** Project switcher (MenuButton) control style. */
    public static String projectSwitcher() {
        return "-fx-background-color: " + GLASS_BG_HOVER + ";"
             + "-fx-text-fill: " + TEXT_PRIMARY + ";"
             + "-fx-border-color: " + GLASS_BORDER + ";"
             + "-fx-background-radius: " + NAV_RADIUS + "; -fx-border-radius: " + NAV_RADIUS + ";"
             + "-fx-cursor: hand;";
    }

    /** Nav item icon fill color: secondary when idle, primary when active. */
    public static String navItemIconColor(boolean active) {
        return active ? TEXT_PRIMARY : TEXT_SECONDARY;
    }

    /** LogDrawer container (expanded): base bg + left 1px border. */
    public static String logDrawerStyle() {
        return "-fx-background-color: " + GLASS_BG_SOFT + ";"
             + "-fx-border-color: transparent transparent transparent " + GLASS_BORDER + ";"
             + "-fx-border-width: 0 0 0 1;";
    }

    /** Log level filter pill: accent-soft when on, hover-tier when off. */
    public static String logPillStyle(boolean on) {
        return "-fx-background-color: " + (on ? ACCENT_SOFT : GLASS_BG_HOVER) + ";"
             + "-fx-text-fill: " + (on ? ACCENT : TEXT_SECONDARY) + ";"
             + "-fx-background-radius: " + NAV_RADIUS + "; -fx-cursor: hand;";
    }

    /** Stat tile surface (equivalent to card; semantic alias). */
    public static String statTile() {
        return "-fx-background-color: " + GLASS_BG_SOFT + ";"
             + "-fx-border-color: " + GLASS_BORDER + ";"
             + "-fx-background-radius: " + CARD_RADIUS + "; -fx-border-radius: " + CARD_RADIUS + ";"
             + "-fx-alignment: center;";
    }

    /** Segmented control item style. */
    public static String segStyle(boolean selected) {
        return "-fx-background-color: " + (selected ? BG_SELECTED : "transparent") + ";"
             + "-fx-text-fill: " + (selected ? TEXT_PRIMARY : TEXT_SECONDARY) + ";"
             + "-fx-background-radius: " + NAV_RADIUS + "; -fx-cursor: hand;";
    }

    /** Section sub-title (e.g. 构建/校验 within merged page). */
    public static String subSectionTitle() {
        return "-fx-text-fill: " + TEXT_SECONDARY + "; -fx-font-size: 11px; -fx-font-weight: bold;"
             + " -fx-label-padding: 0 0 8 0;";
    }
}
