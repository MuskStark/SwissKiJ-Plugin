package plugin.swisskit.offlinepython.ui.panel;

import fan.summer.api.component.GlassNotification;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import plugin.swisskit.offlinepython.command.VerifyService;
import plugin.swisskit.offlinepython.domain.CheckResult;
import plugin.swisskit.offlinepython.domain.Manifest;
import plugin.swisskit.offlinepython.domain.VerifyResult;
import plugin.swisskit.offlinepython.infra.JsonStore;
import plugin.swisskit.offlinepython.ui.LogConsole;

import java.io.File;

public class VerifyPanel extends CommandPanel {
    private final VBox report = new VBox(6);

    public VerifyPanel(LogConsole log) {
        super(log);
        getChildren().add(new Label(title()));
        Button verify = new Button("Verify");
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
            report.getChildren().add(new Label("[" + c.status() + "] " + c.detail()));
        }
    }

    @Override public String title() { return "Verify Repository"; }
}
