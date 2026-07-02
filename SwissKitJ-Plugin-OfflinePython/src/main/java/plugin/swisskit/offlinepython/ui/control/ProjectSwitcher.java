package plugin.swisskit.offlinepython.ui.control;

import fan.summer.api.MdiIconUtil;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import plugin.swisskit.offlinepython.ui.OpbStyle;

/**
 * 顶栏项目名显示:只读 Label(folder 图标 + 项目名)。
 * 不再支持新建/打开 —— 打开项目由 ConfigPanel 空状态负责。
 */
public class ProjectSwitcher extends HBox {
    private final Label nameLabel = new Label();

    public ProjectSwitcher() {
        super(8);
        setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        var icon = MdiIconUtil.createIcon("folder-outline", 14, "-fx-fill: " + OpbStyle.TEXT_PRIMARY + ";");
        nameLabel.setStyle("-fx-text-fill: " + OpbStyle.TEXT_PRIMARY + "; -fx-font-size: 13px;");
        getChildren().addAll(icon, nameLabel);
    }

    /** 更新显示的项目名;null/空时显示占位。 */
    public void updateName(String name) {
        nameLabel.setText(name == null || name.isBlank() ? "(未打开项目)" : name);
    }
}
