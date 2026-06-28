package plugin.swisskit.offlinepython.ui.panel;

import fan.summer.api.component.UiUtils;
import fan.summer.api.i18n.I18n;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import plugin.swisskit.offlinepython.command.DoctorService;
import plugin.swisskit.offlinepython.ui.LogConsole;
import plugin.swisskit.offlinepython.ui.OpbStyle;
import plugin.swisskit.offlinepython.ui.ProjectContext;

public class DoctorPanel extends CommandPanel {
    private final GridPane grid = new GridPane();

    public DoctorPanel(LogConsole log, ProjectContext project) {
        super(log, project);
        getChildren().add(titleNode());
        Button run = UiUtils.glassBtn("▶ 运行诊断", true);
        run.setOnAction(e -> {
            grid.getChildren().clear();
            int row = 0;
            for (var c : new DoctorService().run(project.getConfig() != null
                    ? project.getConfig().getPython().getExecutable() : null)) {
                Label key = UiUtils.subLabel(c.name());
                Label val = new Label(c.value());
                val.setStyle("-fx-text-fill: " + (c.ok() ? OpbStyle.SUCCESS : OpbStyle.DANGER) + ";");
                Label mark = new Label(c.ok() ? "✓" : "✕");
                mark.setStyle("-fx-text-fill: " + (c.ok() ? OpbStyle.SUCCESS : OpbStyle.DANGER)
                        + "; -fx-font-weight: bold;");
                grid.add(key, 0, row); grid.add(val, 1, row); grid.add(mark, 2, row);
                row++;
            }
            log.log("诊断完成");
        });
        grid.setHgap(14); grid.setVgap(6);
        getChildren().addAll(run, grid);
    }

    @Override public String title() { return I18n.get("opb.doctor.title"); }
}
