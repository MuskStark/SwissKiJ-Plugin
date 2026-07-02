package plugin.swisskit.offlinepython.ui.panel;

import fan.summer.api.component.UiUtils;
import fan.summer.api.i18n.I18n;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import plugin.swisskit.offlinepython.ui.LogConsole;
import plugin.swisskit.offlinepython.ui.OpbStyle;
import plugin.swisskit.offlinepython.ui.ProjectContext;
import plugin.swisskit.offlinepython.ui.control.EmptyState;
import plugin.swisskit.offlinepython.ui.control.PanelHeader;

public class ProjectPanel extends CommandPanel {
    public ProjectPanel(LogConsole log, ProjectContext project, Runnable onNew, Runnable onOpen) {
        super(log, project);
        PanelHeader header = new PanelHeader(I18n.get("opb.init.title"));
        getChildren().add(header);

        var dir = project.getProjectDir();
        if (dir == null) {
            EmptyState empty = new EmptyState("folder-off-outline", I18n.get("opb.project.empty"));
            HBox actions = new HBox(8);
            Button newBtn = UiUtils.glassBtn(I18n.get("opb.project.new"), true);
            Button openBtn = UiUtils.glassBtn(I18n.get("opb.project.open"), false);
            newBtn.setOnAction(e -> onNew.run());
            openBtn.setOnAction(e -> onOpen.run());
            actions.getChildren().addAll(newBtn, openBtn);
            empty.setActions(actions);
            getChildren().add(empty);
        } else {
            HBox card = new HBox(12);
            card.setStyle(OpbStyle.card() + " -fx-padding: 14; -fx-alignment: CENTER_LEFT;");
            Label path = new Label(dir.toString());
            path.setStyle("-fx-text-fill: " + OpbStyle.TEXT_SECONDARY + "; -fx-font-family: monospace; -fx-font-size: 12px;");
            HBox.setHgrow(path, Priority.ALWAYS);
            Button open = UiUtils.glassBtn("打开", false);
            open.setOnAction(e -> onOpen.run());
            card.getChildren().addAll(path, open);
            getChildren().add(card);

            Label initTitle = UiUtils.subLabel("初始化会生成");
            getChildren().add(initTitle);
            getChildren().add(UiUtils.subLabel("• config.json  • requirements.txt  • README.md"));
        }
    }

    @Override public String title() { return I18n.get("opb.init.title"); }
}
