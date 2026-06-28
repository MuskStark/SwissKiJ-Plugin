package plugin.swisskit.offlinepython;

import org.junit.jupiter.api.Test;
import plugin.swisskit.offlinepython.domain.Status;
import plugin.swisskit.offlinepython.ui.OpbStyle;

import static org.junit.jupiter.api.Assertions.*;

class OpbStyleTest {

    @Test
    void mirrorsHostAccentToken() {
        assertEquals("#5b8cf7", OpbStyle.ACCENT);
        assertEquals("rgba(255,255,255,0.055)", OpbStyle.GLASS_BG);
        assertEquals("rgba(255,255,255,0.10)", OpbStyle.GLASS_BORDER);
        assertEquals("#4cd97b", OpbStyle.SUCCESS);
        assertEquals("#f25c5c", OpbStyle.DANGER);
    }

    @Test
    void cardStyleUsesGlassTokensAndRadius() {
        String s = OpbStyle.card();
        assertTrue(s.contains(OpbStyle.GLASS_BG));
        assertTrue(s.contains(OpbStyle.GLASS_BORDER));
        assertTrue(s.contains(String.valueOf(OpbStyle.CARD_RADIUS)));
    }

    @Test
    void navItemSelectedUsesAccent() {
        String sel = OpbStyle.navItem(true, false);
        assertTrue(sel.contains(OpbStyle.ACCENT_SOFT));
        assertTrue(sel.contains(OpbStyle.ACCENT));
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
}
