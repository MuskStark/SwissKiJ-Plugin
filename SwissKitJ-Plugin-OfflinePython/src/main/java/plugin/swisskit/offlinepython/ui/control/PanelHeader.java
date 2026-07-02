package plugin.swisskit.offlinepython.ui.control;

import fan.summer.api.component.UiUtils;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.Priority;
import plugin.swisskit.offlinepython.ui.OpbStyle;

/**
 * 统一面板头:左侧标题(sk-t1, 15px)+ 右侧操作区(HBox,可空)。
 * 替换各面板手写的 titleNode()。
 */
public class PanelHeader extends HBox {
    private final HBox actions = new HBox(8);

    public PanelHeader(String title) {
        super(12);
        var t = UiUtils.sectionTitle(title);   // 15px, sk-t1
        getChildren().add(t);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        getChildren().addAll(spacer, actions);
    }

    /** 注入右侧操作按钮(可多次调用追加)。 */
    public void addActions(javafx.scene.Node... nodes) {
        actions.getChildren().addAll(nodes);
    }
}
