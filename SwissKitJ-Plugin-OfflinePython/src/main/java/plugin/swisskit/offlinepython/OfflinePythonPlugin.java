package plugin.swisskit.offlinepython;

import fan.summer.api.IconStyle;
import fan.summer.api.SwissKitJPlugin;
import fan.summer.api.ToolCategory;
import fan.summer.api.i18n.I18n;
import javafx.scene.Node;
import javafx.scene.control.Label;

public class OfflinePythonPlugin implements SwissKitJPlugin {

    @Override public String getId()          { return "plugin.swisskit.offlinepython"; }
    @Override public String getName()        { return "Offline Python Builder"; }
    @Override public String getDescription() { return "Build offline Python install repositories with all dependencies"; }
    @Override public ToolCategory getCategory() { return ToolCategory.DEV; }
    @Override public String getVersion()     { return "1.0.0"; }
    @Override public String getMdiIcon()     { return "language-python"; }
    @Override public IconStyle getIconStyle(){ return IconStyle.BLUE; }

    @Override
    public Node createView() {
        I18n.registerPluginBundle("i18n.messages", getClass().getClassLoader());
        // TODO Task 12: replace with new CommandShell()
        return new Label("Offline Python Builder — scaffold");
    }
}
