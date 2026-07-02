package plugin.swisskit.offlinepython;

import org.junit.jupiter.api.Test;
import plugin.swisskit.offlinepython.domain.Status;
import plugin.swisskit.offlinepython.ui.OpbStyle;

import static org.junit.jupiter.api.Assertions.*;

class OpbStyleTest {

    @Test
    void exposesHostAccentToken() {
        // Colors are -sk-* looked-up color tokens (never inline hex/rgba), so they
        // track the host theme. Assert the token strings, not frozen color values.
        assertEquals("-sk-accent", OpbStyle.ACCENT);
        assertEquals("-sk-bg-elevated", OpbStyle.GLASS_BG);
        assertEquals("-sk-border", OpbStyle.GLASS_BORDER);
        assertEquals("-sk-success", OpbStyle.SUCCESS);
        assertEquals("-sk-danger", OpbStyle.DANGER);
    }

    @Test
    void cardStyleUsesGlassTokensAndRadius() {
        String s = OpbStyle.card();
        assertTrue(s.contains(OpbStyle.GLASS_BG));
        assertTrue(s.contains(OpbStyle.GLASS_BORDER));
        assertEquals(8, OpbStyle.CARD_RADIUS);
        assertTrue(s.contains(String.valueOf(OpbStyle.CARD_RADIUS)));
    }

    @Test
    void navItemSelectedUsesNeutralFillAndLeftAccentStrip() {
        String sel = OpbStyle.navItem(true, false);
        // 规范 S1:选中态 = 中性 -sk-bg-selected 填充 + 左 3px -sk-accent border + -sk-text 文字
        assertTrue(sel.contains(OpbStyle.BG_SELECTED), "选中态背景须为中性 -sk-bg-selected");
        assertTrue(sel.contains(OpbStyle.ACCENT), "须含 -sk-accent(左侧条)");
        assertTrue(sel.contains(OpbStyle.TEXT_PRIMARY), "选中文字须升至 -sk-text");
        assertTrue(sel.contains("-fx-border-width"), "须有 border-width 实现左侧条");
        assertTrue(sel.contains("0 0 0 3"), "3px 必须在左侧 (TRBL)");
        assertFalse(sel.contains(OpbStyle.ACCENT_SOFT), "禁止蓝填充(规范反模式)");
    }

    @Test
    void navItemIdleIsTransparentAndHoverUsesGlassHover() {
        assertTrue(OpbStyle.navItem(false, false).contains("transparent"));
        assertTrue(OpbStyle.navItem(false, true).contains(OpbStyle.GLASS_BG_HOVER));
    }

    @Test
    void badgeColorFollowsOkFlag() {
        assertTrue(OpbStyle.badge(true).contains(OpbStyle.SUCCESS));
        assertTrue(OpbStyle.badge(false).contains(OpbStyle.DANGER));
    }

    @Test
    void statusColorMapsEachStatus() {
        assertEquals(OpbStyle.SUCCESS, OpbStyle.statusColor(Status.PASS));
        assertEquals(OpbStyle.WARNING, OpbStyle.statusColor(Status.WARN));
        assertEquals(OpbStyle.DANGER, OpbStyle.statusColor(Status.FAIL));
    }

    @Test
    void tableCellStyleAppliesFontSizeAndOptions() {
        String s = OpbStyle.tableCellStyle(OpbStyle.TEXT_PRIMARY, true, true);
        assertTrue(s.contains("-fx-font-size: 13px"));
        assertTrue(s.contains(OpbStyle.TEXT_PRIMARY));
        assertTrue(s.contains("-fx-font-weight: bold"));
        assertTrue(s.contains("monospace"));
        assertTrue(s.contains("-fx-padding"));
    }

    @Test
    void tableRowStyleZebraAndSelection() {
        assertTrue(OpbStyle.tableRowStyle(true, false).contains(OpbStyle.GLASS_BG_HOVER));
        assertTrue(OpbStyle.tableRowStyle(false, false).contains(OpbStyle.GLASS_BG));
        assertTrue(OpbStyle.tableRowStyle(false, true).contains(OpbStyle.ACCENT_SOFT));
    }

    @Test
    void tableHeaderStyleIsSecondaryBoldSmall() {
        String s = OpbStyle.tableHeaderStyle();
        assertTrue(s.contains(OpbStyle.TEXT_SECONDARY));
        assertTrue(s.contains("11px"));
        assertTrue(s.contains("bold"));
    }

    @Test
    void newLayoutHelpersUseTokensNotHex() {
        assertTrue(OpbStyle.topBar().contains(OpbStyle.GLASS_BORDER));
        assertTrue(OpbStyle.projectSwitcher().contains(OpbStyle.GLASS_BG_HOVER));
        assertTrue(OpbStyle.logPillStyle(true).contains(OpbStyle.ACCENT_SOFT));
        assertTrue(OpbStyle.logPillStyle(false).contains(OpbStyle.GLASS_BG_HOVER));
        assertTrue(OpbStyle.segStyle(true).contains(OpbStyle.BG_SELECTED));
        assertTrue(OpbStyle.statTile().contains(OpbStyle.GLASS_BORDER));
    }

    @Test
    void drawerWidthsAreGridAligned() {
        assertEquals(240, OpbStyle.LOG_DRAWER_WIDTH);
        assertEquals(40, OpbStyle.LOG_DRAWER_COLLAPSED_WIDTH);
    }
}
