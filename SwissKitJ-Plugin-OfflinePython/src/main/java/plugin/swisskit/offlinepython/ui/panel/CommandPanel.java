package plugin.swisskit.offlinepython.ui.panel;

import fan.summer.api.component.UiUtils;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import plugin.swisskit.offlinepython.ui.LogConsole;
import plugin.swisskit.offlinepython.ui.OpbStyle;
import plugin.swisskit.offlinepython.ui.ProjectContext;

/** Base for command panels: glass card + shared log + shared project context + section title. */
public abstract class CommandPanel extends VBox {
    protected final LogConsole log;
    protected final ProjectContext project;

    protected CommandPanel(LogConsole log, ProjectContext project) {
        this.log = log;
        this.project = project;
        setSpacing(14);
        setStyle(OpbStyle.card() + " -fx-padding: 18;");
    }

    protected Label titleNode() { return UiUtils.sectionTitle(title()); }

    public abstract String title();
}
