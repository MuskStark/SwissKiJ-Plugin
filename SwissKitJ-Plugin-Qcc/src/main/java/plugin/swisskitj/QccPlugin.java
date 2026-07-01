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
        return I18n.get("plugin.qcc.name");
    }

    @Override
    public String getDescription() {
        return I18n.get("plugin.qcc.desc");
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

    @Override
    public boolean hasRunningTasks() {
        return ui != null && ui.isRunning();
    }

    @Override
    public void onUnload() {
        if (ui != null) ui.cancel();
    }
}
