package plugin.swisskit.keepawake;

import fan.summer.api.preview.PluginPreviewWindow;
import javafx.application.Platform;

public class DevLauncher {
    public static void main(String[] args) {
        Platform.startup(() -> {
            PluginPreviewWindow.configure().withPlugin(new KeepAwakePlugin()).launch();
        });
    }
}
