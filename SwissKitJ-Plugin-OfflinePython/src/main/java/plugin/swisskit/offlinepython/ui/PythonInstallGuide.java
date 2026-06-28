package plugin.swisskit.offlinepython.ui;

import fan.summer.api.component.GlassNotification;
import fan.summer.api.component.UiUtils;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public class PythonInstallGuide extends VBox {
    public PythonInstallGuide(Runnable onRedetect) {
        setSpacing(10);
        setStyle(OpbStyle.card() + " -fx-padding: 18;");

        Label warn = new Label("⚠ Python not detected");
        warn.setStyle("-fx-text-fill: " + OpbStyle.WARNING + "; -fx-font-size: 15px; -fx-font-weight: 500;");
        getChildren().add(warn);
        getChildren().add(UiUtils.subLabel("This plugin needs Python ≥ 3.10 + pip. Install it, then retry."));
        getChildren().add(cmdRow("macOS", "brew install python", this));
        getChildren().add(cmdRow("Linux", "sudo apt install python3 python3-pip", this));
        Button retry = UiUtils.glassBtn("Re-detect", true);
        retry.setOnAction(e -> onRedetect.run());
        getChildren().add(retry);
    }

    private HBox cmdRow(String os, String cmd, PythonInstallGuide self) {
        Label l = UiUtils.subLabel(os);
        TextField field = new TextField(cmd);
        field.setEditable(false);
        field.setStyle(UiUtils.fieldStyle());
        field.setPrefWidth(280);
        Button copy = UiUtils.glassBtn("Copy", false);
        copy.setOnAction(e -> {
            ClipboardContent c = new ClipboardContent();
            c.putString(cmd);
            Clipboard.getSystemClipboard().setContent(c);
            GlassNotification.toast(self, GlassNotification.Type.SUCCESS, "Copied: " + cmd);
        });
        HBox row = new HBox(8, l, field, copy);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }
}
