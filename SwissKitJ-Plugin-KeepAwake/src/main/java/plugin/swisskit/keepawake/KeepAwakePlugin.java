package plugin.swisskit.keepawake;

import fan.summer.api.IconStyle;
import fan.summer.api.SwissKitJPlugin;
import fan.summer.api.ToolCategory;
import fan.summer.api.host.PluginHost;
import javafx.scene.Node;
import plugin.swisskit.keepawake.service.KeepAwakeService;
import plugin.swisskit.keepawake.ui.KeepAwakeUi;

public class KeepAwakePlugin implements SwissKitJPlugin {

    private PluginHost host;
    private KeepAwakeUi ui;

    @Override
    public void init(PluginHost host) {
        this.host = host;
    }

    @Override
    public String getId() {
        return "plugin.swisskit.keepawake";
    }

    @Override
    public String getName() {
        return host.i18n().get("plugin.keepawake.name");
    }

    @Override
    public String getDescription() {
        return host.i18n().get("plugin.keepawake.desc");
    }

    @Override
    public ToolCategory getCategory() {
        return ToolCategory.OTHER;
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getMdiIcon() {
        return "coffee";
    }

    @Override
    public IconStyle getIconStyle() {
        return IconStyle.PURPLE;
    }

    @Override
    public Node createView() {
        host.i18n().registerBundle("i18n.messages");
        ui = new KeepAwakeUi(host);
        return ui.getView();
    }

    @Override
    public void onActivate() {
        if (ui != null) ui.resumeUi();
    }

    @Override
    public void onDeactivate() {
        if (ui != null) ui.suspendUi();
    }

    @Override
    public boolean hasRunningTasks() {
        return KeepAwakeService.getInstance().isRunning();
    }

    @Override
    public void onBackground() {
        if (ui != null) ui.suspendUi();
    }

    @Override
    public void onForeground() {
        if (ui != null) ui.resumeUi();
    }

    @Override
    public void onUnload() {
        KeepAwakeService.getInstance().stop();
    }
}
