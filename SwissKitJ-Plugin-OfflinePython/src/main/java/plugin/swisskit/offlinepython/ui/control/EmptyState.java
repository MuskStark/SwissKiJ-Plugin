package plugin.swisskit.offlinepython.ui.control;

import fan.summer.api.MdiIconUtil;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import plugin.swisskit.offlinepython.ui.OpbStyle;

/**
 * 空状态:大图标(sk-t3)+ 文案 + 可选操作按钮行。
 */
public class EmptyState extends VBox {
    public EmptyState(String iconName, String message) {
        super(10);
        setAlignment(javafx.geometry.Pos.CENTER);
        var icon = MdiIconUtil.createIcon(iconName, 40, "-fx-fill: " + OpbStyle.TEXT_TERTIARY + ";");
        Label msg = new Label(message);
        msg.setStyle("-fx-text-fill: " + OpbStyle.TEXT_TERTIARY + "; -fx-font-size: 13px;");
        getChildren().addAll(icon, msg);
    }

    /** 追加操作按钮行(通常一个 HBox of buttons)。 */
    public void setActions(javafx.scene.Node actions) {
        getChildren().add(actions);
    }
}
