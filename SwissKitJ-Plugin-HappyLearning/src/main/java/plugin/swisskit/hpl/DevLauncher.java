package plugin.swisskit.hpl;

import fan.summer.api.preview.PluginPreviewWindow;
import javafx.application.Platform;


public class DevLauncher {
    public static void main(String[] args) {
        Platform.startup(() -> {
            PluginPreviewWindow.configure().withPlugin(new HappyLearnPlugin()).launch();
        });
    }
}
