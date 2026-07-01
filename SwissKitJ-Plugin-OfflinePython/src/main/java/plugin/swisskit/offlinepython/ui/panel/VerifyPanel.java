package plugin.swisskit.offlinepython.ui.panel;

import fan.summer.api.component.GlassNotification;
import fan.summer.api.component.UiUtils;
import fan.summer.api.i18n.I18n;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import plugin.swisskit.offlinepython.command.VerifyService;
import plugin.swisskit.offlinepython.domain.CheckResult;
import plugin.swisskit.offlinepython.domain.Manifest;
import plugin.swisskit.offlinepython.domain.Status;
import plugin.swisskit.offlinepython.domain.VerifyResult;
import plugin.swisskit.offlinepython.domain.VerifyScope;
import plugin.swisskit.offlinepython.infra.JsonStore;
import plugin.swisskit.offlinepython.ui.LogConsole;
import plugin.swisskit.offlinepython.ui.OpbStyle;
import plugin.swisskit.offlinepython.ui.ProjectContext;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class VerifyPanel extends CommandPanel {
    private final ToggleGroup scopeGroup = new ToggleGroup();
    private final VBox report = new VBox(6);
    private final Label conclusion = new Label();

    public VerifyPanel(LogConsole log, ProjectContext project) {
        super(log, project);
        getChildren().add(titleNode());

        ToggleButton all = seg("全量", VerifyScope.ALL, true);
        ToggleButton integ = seg("仅完整性", VerifyScope.INTEGRITY, false);
        ToggleButton sha = seg("仅 SHA256", VerifyScope.SHA256, false);
        HBox segs = new HBox(0, all, integ, sha);
        segs.setStyle("-fx-background-color: " + OpbStyle.GLASS_BG_HOVER + "; -fx-background-radius: 8;");
        Button run = UiUtils.glassBtn("▶ 开始校验", true);
        HBox topbar = new HBox(8, segs);
        HBox spacerBox = new HBox(run);
        HBox.setHgrow(spacerBox, javafx.scene.layout.Priority.ALWAYS);
        spacerBox.setStyle("-fx-alignment: CENTER_RIGHT;");
        topbar.getChildren().add(spacerBox);
        run.setOnAction(e -> doVerify());

        getChildren().addAll(topbar, report, conclusion);
    }

    private ToggleButton seg(String text, VerifyScope scope, boolean selected) {
        ToggleButton b = new ToggleButton(text);
        b.setToggleGroup(scopeGroup);
        b.setUserData(scope);
        b.setSelected(selected);
        b.setStyle("-fx-text-fill: " + OpbStyle.TEXT_PRIMARY + "; -fx-background-radius: 8; -fx-cursor: hand;");
        return b;
    }

    private VerifyScope selectedScope() {
        Toggle t = scopeGroup.getSelectedToggle();
        return t == null ? VerifyScope.ALL : (VerifyScope) t.getUserData();
    }

    private void doVerify() {
        Path dir = project.getProjectDir();
        if (dir == null) { GlassNotification.toast(this, GlassNotification.Type.WARNING, "先打开或新建项目"); return; }
        try {
            Manifest m = JsonStore.load(dir.resolve("manifest.json"), Manifest.class);
            VerifyResult r = new VerifyService().verify(dir, m, selectedScope());
            render(r);
        } catch (Exception ex) {
            log.log("校验失败: " + ex.getMessage());
            GlassNotification.toast(this, GlassNotification.Type.ERROR, "校验失败（未构建？）");
        }
    }

    private void render(VerifyResult r) {
        report.getChildren().clear();
        boolean fail = false; boolean warn = false;
        for (CheckResult c : presentChecks(r)) {
            if (c == null) continue;
            fail |= c.status() == Status.FAIL;
            warn |= c.status() == Status.WARN;
            Label badge = new Label("[" + c.status() + "]");
            badge.setStyle("-fx-text-fill: " + OpbStyle.statusColor(c.status())
                    + "; -fx-background-color: " + soft(c.status()) + "; -fx-background-radius: 6;"
                    + " -fx-padding: 1 8 1 8; -fx-font-weight: bold; -fx-font-size: 10px;");
            report.getChildren().add(new HBox(10, badge, UiUtils.subLabel(c.detail())));
        }
        if (fail) {
            conclusion.setText("⚠ 仓库存在问题");
            conclusion.setStyle("-fx-background-color: " + OpbStyle.DANGER_SOFT + "; -fx-text-fill: " + OpbStyle.DANGER + ";"
                    + " -fx-background-radius: 10; -fx-padding: 10 14; -fx-font-weight: 500;");
        } else if (warn) {
            conclusion.setText("✓ 仓库可用（含警告）");
            conclusion.setStyle("-fx-background-color: " + OpbStyle.WARNING_SOFT + "; -fx-text-fill: " + OpbStyle.WARNING + ";"
                    + " -fx-background-radius: 10; -fx-padding: 10 14; -fx-font-weight: 500;");
        } else {
            conclusion.setText("✓ Repository OK");
            conclusion.setStyle("-fx-background-color: " + OpbStyle.SUCCESS_SOFT + "; -fx-text-fill: " + OpbStyle.SUCCESS + ";"
                    + " -fx-background-radius: 10; -fx-padding: 10 14; -fx-font-weight: 500;");
        }
    }

    private List<CheckResult> presentChecks(VerifyResult r) {
        List<CheckResult> out = new ArrayList<>();
        if (r.sha256() != null) out.add(r.sha256());
        if (r.fileIntegrity() != null) out.add(r.fileIntegrity());
        if (r.wheels() != null) out.add(r.wheels());
        if (r.requirements() != null) out.add(r.requirements());
        if (r.manifest() != null) out.add(r.manifest());
        return out;
    }

    private String soft(Status s) {
        return switch (s) {
            case PASS -> OpbStyle.SUCCESS_SOFT;
            case WARN -> OpbStyle.WARNING_SOFT;
            case FAIL -> OpbStyle.DANGER_SOFT;
        };
    }

    @Override public String title() { return I18n.get("opb.verify.title"); }
}
