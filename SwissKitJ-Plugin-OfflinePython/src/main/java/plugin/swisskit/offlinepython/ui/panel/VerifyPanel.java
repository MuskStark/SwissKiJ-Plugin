package plugin.swisskit.offlinepython.ui.panel;

import fan.summer.api.component.GlassNotification;
import fan.summer.api.component.UiUtils;
import fan.summer.api.i18n.I18n;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import plugin.swisskit.offlinepython.command.VerifyService;
import plugin.swisskit.offlinepython.domain.CheckResult;
import plugin.swisskit.offlinepython.domain.Manifest;
import plugin.swisskit.offlinepython.domain.VerifyResult;
import plugin.swisskit.offlinepython.infra.JsonStore;
import plugin.swisskit.offlinepython.ui.LogConsole;
import plugin.swisskit.offlinepython.ui.OpbStyle;

import java.io.File;

public class VerifyPanel extends CommandPanel {
    private final VBox report = new VBox(6);

    public VerifyPanel(LogConsole log) {
        super(log);
        getChildren().add(titleNode());
        Button verify = UiUtils.glassBtn("Verify", true);
        verify.setOnAction(e -> {
            DirectoryChooser dc = new DirectoryChooser();
            File dir = dc.showDialog(getScene().getWindow());
            if (dir == null) return;
            try {
                Manifest m = JsonStore.load(dir.toPath().resolve("manifest.json"), Manifest.class);
                VerifyResult r = new VerifyService().verify(dir.toPath(), m);
                render(r);
                log.log(r.isOk() ? "Repository OK" : "Verification found problems");
                GlassNotification.toast(this, r.isOk() ? GlassNotification.Type.SUCCESS : GlassNotification.Type.WARNING,
                        r.isOk() ? "Repository OK" : "Problems found");
            } catch (Exception ex) {
                log.log("ERROR: " + ex.getMessage());
                GlassNotification.toast(this, GlassNotification.Type.ERROR, "Verify failed");
            }
        });
        getChildren().addAll(verify, report);
    }

    private void render(VerifyResult r) {
        report.getChildren().clear();
        for (CheckResult c : new CheckResult[]{r.sha256(), r.fileIntegrity(), r.wheels(), r.requirements(), r.manifest()}) {
            Label badge = new Label("[" + c.status() + "]");
            badge.setStyle("-fx-text-fill: " + OpbStyle.statusColor(c.status()) + "; -fx-font-weight: bold;");
            report.getChildren().add(new HBox(8, badge, UiUtils.subLabel(c.detail())));
        }
    }

    @Override public String title() { return I18n.get("opb.verify.title"); }
}
