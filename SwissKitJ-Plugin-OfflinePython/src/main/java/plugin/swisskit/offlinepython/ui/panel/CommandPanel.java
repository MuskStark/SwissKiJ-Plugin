package plugin.swisskit.offlinepython.ui.panel;

import javafx.scene.layout.VBox;
import plugin.swisskit.offlinepython.ui.LogConsole;

/** Base for command panels: provides title + log access. */
public abstract class CommandPanel extends VBox {
    protected final LogConsole log;
    protected CommandPanel(LogConsole log) {
        this.log = log;
        setSpacing(10);
        setStyle("-fx-padding: 18;");
    }
    public abstract String title();
}
