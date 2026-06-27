package plugin.swisskit.offlinepython.ui.panel;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import plugin.swisskit.offlinepython.command.DoctorService;
import plugin.swisskit.offlinepython.ui.LogConsole;

public class DoctorPanel extends CommandPanel {
    private final GridPane grid = new GridPane();

    public DoctorPanel(LogConsole log) {
        super(log);
        getChildren().add(new Label(title()));
        Button run = new Button("Run diagnostics");
        run.setOnAction(e -> {
            grid.getChildren().clear();
            int row = 0;
            for (var c : new DoctorService().run(null)) {
                grid.add(new Label(c.name()), 0, row);
                grid.add(new Label((c.ok() ? "✓ " : "✕ ") + c.value()), 1, row);
                row++;
            }
            log.log("Diagnostics complete");
        });
        grid.setHgap(16); grid.setVgap(6);
        getChildren().addAll(run, grid);
    }

    @Override public String title() { return "Environment Doctor"; }
}
