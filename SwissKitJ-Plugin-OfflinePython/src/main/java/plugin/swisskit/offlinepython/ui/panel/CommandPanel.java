package plugin.swisskit.offlinepython.ui.panel;

import fan.summer.api.component.UiUtils;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import plugin.swisskit.offlinepython.ui.LogConsole;
import plugin.swisskit.offlinepython.ui.OpbStyle;

/** Base for command panels: glass card surface + log access + a section-title node. */
public abstract class CommandPanel extends VBox {
    protected final LogConsole log;

    protected CommandPanel(LogConsole log) {
        this.log = log;
        setSpacing(14);
        setStyle(OpbStyle.card() + " -fx-padding: 18;");
    }

    /** A host-styled section-header label for this panel's title. */
    protected Label titleNode() {
        return UiUtils.sectionTitle(title());
    }

    public abstract String title();
}
