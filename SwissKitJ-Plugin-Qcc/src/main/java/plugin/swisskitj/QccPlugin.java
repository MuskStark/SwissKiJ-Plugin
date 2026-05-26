package plugin.swisskitj;

import fan.summer.api.IconStyle;
import fan.summer.api.SwissKitJPlugin;
import fan.summer.api.ToolCategory;
import fan.summer.api.i18n.I18n;
import javafx.scene.Node;
import plugin.swisskitj.ui.QccUi;

public class QccPlugin implements SwissKitJPlugin {

    private QccUi ui;

    @Override
    public String getId() {
        return "plugin.swisskit.qcc";
    }

    @Override
    public String getName() {
        return "QccToExcel";
    }

    @Override
    public String getDescription() {
        return "Convert Qichacha CSV data to styled Excel";
    }

    @Override
    public ToolCategory getCategory() {
        return ToolCategory.DEV;
    }

    @Override
    public String getVersion() {
        return "3.0.0";
    }

    @Override
    public String getMdiIcon() {
        return "file-excel";
    }

    @Override
    public IconStyle getIconStyle() {
        return IconStyle.TEAL;
    }

    @Override
    public Node createView() {
        I18n.registerPluginBundle("i18n.messages", getClass().getClassLoader());
        ui = new QccUi();
        return ui.getView();
    }
}
