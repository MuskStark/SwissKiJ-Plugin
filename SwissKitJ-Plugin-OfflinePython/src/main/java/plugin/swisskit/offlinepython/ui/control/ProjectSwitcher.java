package plugin.swisskit.offlinepython.ui.control;

import fan.summer.api.MdiIconUtil;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import plugin.swisskit.offlinepython.ui.OpbStyle;

/**
 * 顶栏项目切换器:MenuButton,菜单含「新建…」「打开…」。
 * 显示当前项目名 + ▾。未打开时显示占位。
 */
public class ProjectSwitcher extends MenuButton {
    private final MenuItem newItem = new MenuItem("＋ 新建项目…");
    private final MenuItem openItem = new MenuItem("📂 打开项目…");

    public ProjectSwitcher(Runnable onNew, Runnable onOpen) {
        super("(未打开项目)");
        newItem.setOnAction(e -> onNew.run());
        openItem.setOnAction(e -> onOpen.run());
        getItems().addAll(newItem, openItem);
        setStyle(OpbStyle.projectSwitcher());
        setGraphic(MdiIconUtil.createIcon("folder", 14, "-fx-fill: " + OpbStyle.TEXT_PRIMARY + ";"));
        // MenuButton 默认 graphic/text 同显;graphic 放左,text 是项目名
        setMnemonicParsing(false);
    }

    public void updateName(String name) {
        setText(name + " ▾");
    }
}
