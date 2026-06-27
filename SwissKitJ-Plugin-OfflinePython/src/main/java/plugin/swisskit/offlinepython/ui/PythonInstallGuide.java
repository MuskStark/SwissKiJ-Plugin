package plugin.swisskit.offlinepython.ui;

import fan.summer.api.component.GlassNotification;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

public class PythonInstallGuide extends VBox {
    public PythonInstallGuide(Runnable onRedetect) {
        setSpacing(10);
        setStyle("-fx-padding: 24;");
        getChildren().add(new Label("⚠ Python not detected"));
        getChildren().add(new Label("This plugin needs Python ≥ 3.10 + pip. Install it, then retry."));
        getChildren().add(cmdRow("macOS", "brew install python", this));
        getChildren().add(cmdRow("Linux", "sudo apt install python3 python3-pip", this));
        Button retry = new Button("Re-detect");
        retry.setOnAction(e -> onRedetect.run());
        getChildren().add(retry);
    }

    private HBox cmdRow(String os, String cmd, PythonInstallGuide self) {
        HBox row = new HBox(8);
        Label l = new Label(os + ":  " + cmd);
        Button copy = new Button("Copy");
        copy.setOnAction(e -> {
            ClipboardContent c = new ClipboardContent();
            c.putString(cmd);
            Clipboard.getSystemClipboard().setContent(c);
            GlassNotification.toast(self, GlassNotification.Type.SUCCESS, "Copied: " + cmd);
        });
        row.getChildren().addAll(l, copy);
        return row;
    }
}
