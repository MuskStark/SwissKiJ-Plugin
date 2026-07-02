package plugin.swisskit.offlinepython.ui.control;

import javafx.scene.control.Label;
import plugin.swisskit.offlinepython.domain.Status;
import plugin.swisskit.offlinepython.ui.OpbStyle;

/**
 * 状态徽章:[PASS]/[WARN]/[FAIL],颜色走 soft+实色令牌对。
 */
public class StatusBadge extends Label {
    public StatusBadge(Status s) {
        super("[" + s + "]");
        setStyle("-fx-text-fill: " + OpbStyle.statusColor(s)
                + "; -fx-background-color: " + soft(s) + ";"
                + " -fx-background-radius: 6; -fx-padding: 1 8 1 8;"
                + " -fx-font-weight: bold; -fx-font-size: 10px;");
    }

    private static String soft(Status s) {
        return switch (s) {
            case PASS -> OpbStyle.SUCCESS_SOFT;
            case WARN -> OpbStyle.WARNING_SOFT;
            case FAIL -> OpbStyle.DANGER_SOFT;
        };
    }
}
