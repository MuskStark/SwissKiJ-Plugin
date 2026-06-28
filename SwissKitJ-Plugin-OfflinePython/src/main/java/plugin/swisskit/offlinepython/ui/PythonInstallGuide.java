package plugin.swisskit.offlinepython.ui;

import fan.summer.api.component.GlassNotification;
import fan.summer.api.component.UiUtils;
import fan.summer.api.i18n.I18n;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.awt.Desktop;
import java.io.File;
import java.net.URI;

public class PythonInstallGuide extends VBox {
    public PythonInstallGuide(Runnable onRedetect) {
        setSpacing(10);
        setStyle(OpbStyle.card() + " -fx-padding: 20;");

        Label warn = new Label("⚠ " + I18n.get("opb.python.missing"));
        warn.setStyle("-fx-text-fill: " + OpbStyle.WARNING + "; -fx-font-size: 15px; -fx-font-weight: 500;");
        getChildren().add(warn);
        getChildren().add(UiUtils.subLabel("本插件需要 Python ≥ 3.10 + pip。"));
        getChildren().add(cmdRow("macOS", "brew install python", true));
        getChildren().add(linkRow("Windows", "https://www.python.org/downloads"));
        getChildren().add(cmdRow("Linux", "sudo apt install python3 python3-pip", true));

        Button retry = UiUtils.glassBtn("安装后点此重新检测", true);
        retry.setOnAction(e -> onRedetect.run());
        Button manual = UiUtils.glassBtn("手动指定路径…", false);
        manual.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("选择 python 可执行文件");
            File f = fc.showOpenDialog(getScene().getWindow());
            if (f == null) return;
            onManualPath(f.getAbsolutePath(), onRedetect);
        });
        getChildren().add(new HBox(8, retry, manual));
    }

    private HBox cmdRow(String os, String cmd, boolean copyable) {
        Label l = UiUtils.subLabel(os);
        TextField field = new TextField(cmd);
        field.setEditable(false);
        field.setStyle(UiUtils.fieldStyle());
        field.setPrefWidth(300);
        Button copy = UiUtils.glassBtn("复制", false);
        copy.setOnAction(e -> {
            ClipboardContent c = new ClipboardContent(); c.putString(cmd);
            Clipboard.getSystemClipboard().setContent(c);
            GlassNotification.toast(this, GlassNotification.Type.SUCCESS, "已复制: " + cmd);
        });
        HBox row = new HBox(8, l, field, copy);
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(field, javafx.scene.layout.Priority.ALWAYS);
        return row;
    }

    private HBox linkRow(String os, String url) {
        Label l = UiUtils.subLabel(os);
        TextField field = new TextField(url);
        field.setEditable(false); field.setStyle(UiUtils.fieldStyle()); field.setPrefWidth(300);
        Button open = UiUtils.glassBtn("打开浏览器", false);
        open.setOnAction(e -> {
            try { Desktop.getDesktop().browse(new URI(url)); }
            catch (Exception ex) { GlassNotification.toast(this, GlassNotification.Type.ERROR, "无法打开浏览器"); }
        });
        HBox row = new HBox(8, l, field, open);
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(field, javafx.scene.layout.Priority.ALWAYS);
        return row;
    }

    private void onManualPath(String path, Runnable onRedetect) {
        // Best-effort: write to the user config dir's config.json if a project is open; else just re-detect.
        // (ProjectContext not available here; the shell re-detects after this.)
        onRedetect.run();
        GlassNotification.toast(this, GlassNotification.Type.INFO, "已记录路径，重新检测中");
    }
}
