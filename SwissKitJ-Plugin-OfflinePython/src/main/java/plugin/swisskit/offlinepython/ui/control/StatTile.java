package plugin.swisskit.offlinepython.ui.control;

import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import plugin.swisskit.offlinepython.ui.OpbStyle;

/**
 * 结果统计瓦片:小标题(sk-t3, 11px)+ 大值(sk-t1, 15px,规范上限)。
 * Replaces the former hand-written stat VBox (originally violated the 16px font-scale).
 */
public class StatTile extends VBox {
    public StatTile(String label, String value) {
        super(2);
        Label l = new Label(label);
        l.setStyle("-fx-text-fill: " + OpbStyle.TEXT_TERTIARY + "; -fx-font-size: 11px;");
        Label v = new Label(value);
        v.setStyle("-fx-text-fill: " + OpbStyle.TEXT_PRIMARY + "; -fx-font-size: 15px; -fx-font-weight: 600;");
        getChildren().addAll(l, v);
        setStyle(OpbStyle.statTile() + " -fx-padding: 10;");
    }

    public void setValue(String value) {
        ((Label) getChildren().get(1)).setText(value);
    }
}
