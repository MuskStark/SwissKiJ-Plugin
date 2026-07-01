package plugin.swisskit.hpl;

import fan.summer.api.IconStyle;
import fan.summer.api.SwissKitJPlugin;
import fan.summer.api.ToolCategory;
import fan.summer.api.i18n.I18n;
import javafx.scene.Node;
import plugin.swisskit.hpl.service.HappyLearningService;
import plugin.swisskit.hpl.ui.HappyLearningUi;

public class HappyLearnPlugin implements SwissKitJPlugin {

    private HappyLearningUi ui;

    @Override
    public String getId() {
        return "plugin.swisskit.hpl";
    }

    @Override
    public String getName() {
        return I18n.get("plugin.hpl.name");
    }

    @Override
    public String getDescription() {
        return I18n.get("plugin.hpl.desc");
    }

    @Override
    public ToolCategory getCategory() {
        return ToolCategory.OTHER;
    }

    @Override
    public String getVersion() {
        return "1.1.2";
    }

    @Override
    public String getMdiIcon() {
        return "school";
    }

    @Override
    public IconStyle getIconStyle() {
        return IconStyle.BLUE;
    }

    @Override
    public Node createView() {
        I18n.registerPluginBundle("i18n.messages", getClass().getClassLoader());
        ui = new HappyLearningUi();
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
        return HappyLearningService.getInstance().isRunning();
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
        HappyLearningService.getInstance().stopLearning();
    }
}
