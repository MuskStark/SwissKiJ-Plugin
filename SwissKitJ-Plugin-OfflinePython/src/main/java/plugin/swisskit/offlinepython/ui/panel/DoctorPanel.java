package plugin.swisskit.offlinepython.ui.panel;

import fan.summer.api.component.UiUtils;
import fan.summer.api.i18n.I18n;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import plugin.swisskit.offlinepython.command.DoctorService;
import plugin.swisskit.offlinepython.ui.LogConsole;
import plugin.swisskit.offlinepython.ui.OpbStyle;

public class DoctorPanel extends CommandPanel {
    private final GridPane grid = new GridPane();

    public DoctorPanel(LogConsole log) {
        super(log);
        getChildren().add(titleNode());
        Button run = UiUtils.glassBtn("Run diagnostics", true);
        run.setOnAction(e -> {
            grid.getChildren().clear();
            int row = 0;
            for (var c : new DoctorService().run(null)) {
                Label key = UiUtils.subLabel(c.name());
                Label val = new Label((c.ok() ? "✓ " : "✕ ") + c.value());
                val.setStyle("-fx-text-fill: " + (c.ok() ? OpbStyle.SUCCESS : OpbStyle.DANGER) + ";");
                grid.add(key, 0, row);
                grid.add(val, 1, row);
                row++;
            }
            log.log("Diagnostics complete");
        });
        grid.setHgap(16);
        grid.setVgap(6);
        getChildren().addAll(run, grid);
    }

    @Override public String title() { return I18n.get("opb.doctor.title"); }
}
