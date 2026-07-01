package plugin.swisskit.offlinepython;

import fan.summer.api.IconStyle;
import fan.summer.api.SwissKitJPlugin;
import fan.summer.api.ToolCategory;
import fan.summer.api.i18n.I18n;
import javafx.scene.Node;
import plugin.swisskit.offlinepython.ui.CommandShell;

public class OfflinePythonPlugin implements SwissKitJPlugin {

    private CommandShell shell;

    @Override public String getId()          { return "plugin.swisskit.offlinepython"; }
    @Override public String getName()        { return I18n.get("opb.name"); }
    @Override public String getDescription() { return I18n.get("opb.desc"); }
    @Override public ToolCategory getCategory() { return ToolCategory.DEV; }
    @Override public String getVersion()     { return "1.0.0"; }
    @Override public String getMdiIcon()     { return "language-python"; }
    @Override public IconStyle getIconStyle(){ return IconStyle.BLUE; }

    @Override
    public Node createView() {
        I18n.registerPluginBundle("i18n.messages", getClass().getClassLoader());
        shell = new CommandShell();
        return shell.getView();
    }

    @Override public boolean hasRunningTasks() {
        return shell != null && shell.hasRunningTasks();
    }

    @Override public void onBackground()  { if (shell != null) shell.onBackground(); }
    @Override public void onForeground()  { if (shell != null) shell.onForeground(); }
    @Override public void onUnload()      { if (shell != null) shell.onUnload(); }
}
