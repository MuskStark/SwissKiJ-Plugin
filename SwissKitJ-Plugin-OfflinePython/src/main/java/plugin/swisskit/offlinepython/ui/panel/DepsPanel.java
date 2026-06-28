package plugin.swisskit.offlinepython.ui.panel;

import fan.summer.api.component.GlassNotification;
import fan.summer.api.component.UiUtils;
import fan.summer.api.i18n.I18n;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.FileChooser;
import plugin.swisskit.offlinepython.domain.DependencySpec;
import plugin.swisskit.offlinepython.domain.RequirementsFile;
import plugin.swisskit.offlinepython.ui.LogConsole;
import plugin.swisskit.offlinepython.ui.OpbStyle;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

public class DepsPanel extends CommandPanel {
    private final ListView<DependencySpec> list = new ListView<>();
    private Path requirementsFile;

    public DepsPanel(LogConsole log) {
        super(log);
        list.setStyle(OpbStyle.card());
        list.setMinHeight(120);

        getChildren().add(titleNode());

        Button open = UiUtils.glassBtn("Open requirements.txt", false);
        open.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            File f = fc.showOpenDialog(getScene().getWindow());
            if (f == null) return;
            requirementsFile = f.toPath();
            load();
        });

        final TextField pkgField = new TextField();
        pkgField.setStyle(UiUtils.fieldStyle());
        pkgField.setPromptText("numpy");
        final TextField verField = new TextField();
        verField.setStyle(UiUtils.fieldStyle());
        verField.setPromptText("==1.26.4");
        Button add = UiUtils.glassBtn("Add", false);
        add.setOnAction(e -> {
            String n = pkgField.getText().trim();
            if (n.isEmpty()) return;
            String v = verField.getText().trim();
            list.getItems().add(new DependencySpec(n, v, null));
            pkgField.clear();
            verField.clear();
        });
        HBox addRow = new HBox(8,
                fieldGroup("Package", pkgField),
                fieldGroup("Version", verField),
                add);

        Button save = UiUtils.glassBtn("Save", true);
        save.setOnAction(e -> save());

        getChildren().addAll(open, list, addRow, save);
    }

    private HBox fieldGroup(String text, TextField field) {
        HBox h = new HBox(6, UiUtils.subLabel(text), field);
        HBox.setHgrow(field, Priority.ALWAYS);
        return h;
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

    @Override public String title() { return I18n.get("opb.deps.title"); }
}
