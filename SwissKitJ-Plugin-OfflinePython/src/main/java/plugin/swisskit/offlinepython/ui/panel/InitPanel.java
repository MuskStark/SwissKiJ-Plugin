package plugin.swisskit.offlinepython.ui.panel;

import fan.summer.api.component.GlassNotification;
import fan.summer.api.component.UiUtils;
import fan.summer.api.i18n.I18n;
import javafx.stage.DirectoryChooser;
import plugin.swisskit.offlinepython.command.InitService;
import plugin.swisskit.offlinepython.ui.LogConsole;
import java.io.File;

public class InitPanel extends CommandPanel {
    public InitPanel(LogConsole log) {
        super(log);
        getChildren().add(titleNode());
        var init = UiUtils.glassBtn("Initialize Project…", true);
        init.setOnAction(e -> {
            DirectoryChooser dc = new DirectoryChooser();
            File dir = dc.showDialog(getScene().getWindow());
            if (dir == null) return;
            try {
                new InitService().initialize(dir.toPath());
                log.log("Initialized project at " + dir);
                GlassNotification.toast(this, GlassNotification.Type.SUCCESS, "Project initialized");
            } catch (Exception ex) {
                log.log("ERROR init: " + ex.getMessage());
                GlassNotification.toast(this, GlassNotification.Type.ERROR, "Init failed");
            }
        });
        getChildren().add(init);
    }
    @Override public String title() { return I18n.get("opb.init.title"); }
}
