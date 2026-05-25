package plugin.swisskit.hpl;

import fan.summer.api.IconStyle;
import fan.summer.api.SwissKitJPlugin;
import fan.summer.api.ToolCategory;
import fan.summer.api.i18n.I18n;
import javafx.scene.Node;
import plugin.swisskit.hpl.ui.HappyLearningUi;

public class HappyLearnPlugin implements SwissKitJPlugin {

    @Override
    public String getId() {
        return "plugin.swisskit.hpl";
    }

    @Override
    public String getName() {
        return "HappyLearn";
    }

    @Override
    public String getDescription() {
        return "HappyLearn";
    }

    @Override
    public ToolCategory getCategory() {
        return ToolCategory.OTHER;
    }

    @Override
    public String getVersion() {
        return "v1.1.2";
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
        return new HappyLearningUi().getView();
    }
}
