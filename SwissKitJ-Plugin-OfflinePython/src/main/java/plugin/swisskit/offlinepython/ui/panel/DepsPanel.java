package plugin.swisskit.offlinepython.ui.panel;

import fan.summer.api.component.GlassNotification;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import plugin.swisskit.offlinepython.domain.DependencySpec;
import plugin.swisskit.offlinepython.domain.RequirementsFile;
import plugin.swisskit.offlinepython.ui.LogConsole;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

public class DepsPanel extends CommandPanel {
    private final ListView<DependencySpec> list = new ListView<>();
    private Path requirementsFile;

    public DepsPanel(LogConsole log) {
        super(log);
        getChildren().add(new Label(title()));
        Button open = new Button("Open requirements.txt");
        open.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            File f = fc.showOpenDialog(getScene().getWindow());
            if (f == null) return;
            requirementsFile = f.toPath();
            load();
        });
        HBox addRow = new HBox(8,
                labeled("Package", new TextField()),
                labeled("Version", new TextField()));
        Button save = new Button("Save");
        save.setOnAction(e -> save());
        getChildren().addAll(open, list, addRow, save);
    }

    private void load() {
        try {
            list.getItems().setAll(RequirementsFile.parse(Files.readString(requirementsFile)));
            log.log("Loaded " + list.getItems().size() + " dependencies");
        } catch (Exception ex) {
            log.log("ERROR load: " + ex.getMessage());
        }
    }

    private void save() {
        if (requirementsFile == null) {
            GlassNotification.toast(this, GlassNotification.Type.WARNING, "Open a requirements.txt first");
            return;
        }
        try {
            Files.writeString(requirementsFile, RequirementsFile.write(new ArrayList<>(list.getItems())));
            log.log("Saved " + list.getItems().size() + " dependencies");
            GlassNotification.toast(this, GlassNotification.Type.SUCCESS, "Saved");
        } catch (Exception ex) {
            log.log("ERROR save: " + ex.getMessage());
        }
    }

    private HBox labeled(String text, TextField field) {
        HBox h = new HBox(6, new Label(text), field);
        return h;
    }

    @Override public String title() { return "Dependencies"; }
}
