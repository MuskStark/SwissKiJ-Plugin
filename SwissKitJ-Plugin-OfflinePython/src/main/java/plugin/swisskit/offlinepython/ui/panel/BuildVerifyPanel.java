package plugin.swisskit.offlinepython.ui.panel;

import fan.summer.api.component.GlassNotification;
import fan.summer.api.component.UiUtils;
import fan.summer.api.i18n.I18n;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import plugin.swisskit.offlinepython.command.BuildService;
import plugin.swisskit.offlinepython.command.VerifyService;
import plugin.swisskit.offlinepython.domain.BuildConfig;
import plugin.swisskit.offlinepython.domain.BuildSummary;
import plugin.swisskit.offlinepython.domain.CheckResult;
import plugin.swisskit.offlinepython.domain.Manifest;
import plugin.swisskit.offlinepython.domain.RequirementsFile;
import plugin.swisskit.offlinepython.domain.Status;
import plugin.swisskit.offlinepython.domain.VerifyResult;
import plugin.swisskit.offlinepython.domain.VerifyScope;
import plugin.swisskit.offlinepython.infra.JsonStore;
import plugin.swisskit.offlinepython.infra.ProcessRunner;
import plugin.swisskit.offlinepython.task.PluginTask;
import plugin.swisskit.offlinepython.ui.LogConsole;
import plugin.swisskit.offlinepython.ui.OpbStyle;
import plugin.swisskit.offlinepython.ui.ProjectContext;
import plugin.swisskit.offlinepython.ui.control.PanelHeader;
import plugin.swisskit.offlinepython.ui.control.StatTile;
import plugin.swisskit.offlinepython.ui.control.StatusBadge;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class BuildVerifyPanel extends CommandPanel {
    private final ProgressBar progress = new ProgressBar();
    private final Button build;
    private final Label banner = new Label();
    private final GridPane tiles = new GridPane();
    private final ToggleGroup scopeGroup = new ToggleGroup();
    private final VBox report = new VBox(6);
    private final Label conclusion = new Label();
    private PluginTask<BuildSummary> task;
    private ProcessRunner runner;

    public BuildVerifyPanel(LogConsole log, ProjectContext project) {
        super(log, project);
        PanelHeader header = new PanelHeader(I18n.get("opb.build.title"));
        getChildren().add(header);

        // ── 构建区 ──
        VBox buildSection = new VBox(10);
        buildSection.setStyle(OpbStyle.card() + " -fx-padding: 14;");
        Label bTitle = new Label("构建");
        bTitle.setStyle(OpbStyle.subSectionTitle());
        buildSection.getChildren().add(bTitle);

        banner.setStyle("-fx-background-color: " + OpbStyle.ACCENT_SOFT + "; -fx-text-fill: " + OpbStyle.ACCENT
                + "; -fx-background-radius: 6; -fx-padding: 8 12 8 12; -fx-font-size: 12px;");
        buildSection.getChildren().add(banner);

        build = UiUtils.glassBtn("▶ 构建", true);
        Button cancel = UiUtils.glassBtn("✕ 取消", false);
        progress.setProgress(-1);
        progress.setPrefHeight(6);
        build.setOnAction(e -> start());
        cancel.setOnAction(e -> { if (runner != null) runner.cancel(); });
        HBox buildRow = new HBox(8, build, cancel, progress);
        HBox.setHgrow(progress, Priority.ALWAYS);
        buildSection.getChildren().add(buildRow);

        tiles.setHgap(8); tiles.setVgap(8);
        buildSection.getChildren().add(tiles);
        getChildren().add(buildSection);

        // ── 校验区 ──
        VBox verifySection = new VBox(10);
        verifySection.setStyle(OpbStyle.card() + " -fx-padding: 14;");
        Label vTitle = new Label("校验");
        vTitle.setStyle(OpbStyle.subSectionTitle());
        verifySection.getChildren().add(vTitle);

        ToggleButton all = seg("全量", VerifyScope.ALL, true);
        ToggleButton integ = seg("仅完整性", VerifyScope.INTEGRITY, false);
        ToggleButton sha = seg("仅 SHA256", VerifyScope.SHA256, false);
        HBox segs = new HBox(0, all, integ, sha);
        Button run = UiUtils.glassBtn("▶ 开始校验", true);
        Region spring = new Region(); HBox.setHgrow(spring, Priority.ALWAYS);
        HBox verifyRow = new HBox(8, segs, spring, run);
        verifySection.getChildren().add(verifyRow);
        report.setStyle("-fx-padding: 4 0 0 0;");
        verifySection.getChildren().addAll(report, conclusion);
        getChildren().add(verifySection);

        run.setOnAction(e -> doVerify());
        refreshBanner();
    }

    private ToggleButton seg(String text, VerifyScope scope, boolean selected) {
        ToggleButton b = new ToggleButton(text);
        b.setToggleGroup(scopeGroup);
        b.setUserData(scope);
        b.setSelected(selected);
        b.setStyle(OpbStyle.segStyle(selected) + " -fx-padding: 5 14;");
        b.selectedProperty().addListener((o, ov, nv) ->
                b.setStyle(OpbStyle.segStyle(nv) + " -fx-padding: 5 14;"));
        return b;
    }

    private VerifyScope selectedScope() {
        Toggle t = scopeGroup.getSelectedToggle();
        return t == null ? VerifyScope.ALL : (VerifyScope) t.getUserData();
    }

    private void refreshBanner() {
        Path dir = project.getProjectDir();
        long depCount = countDeps(dir);
        String plat = project.getConfig() != null && project.getConfig().getPython() != null
                ? project.getConfig().getPython().getPrimaryPlatform() : "?";
        banner.setText("📋 当前依赖:" + depCount + " 个直接  ·  目标 " + plat
                + (project.getConfig() != null && project.getConfig().getPython() != null
                    ? "  ·  Python " + project.getConfig().getPython().getVersion() : ""));
    }

    private long countDeps(Path dir) {
        if (dir == null) return 0;
        try {
            if (Files.exists(dir.resolve("requirements.txt")))
                return RequirementsFile.parse(Files.readString(dir.resolve("requirements.txt")))
                        .stream().filter(d -> !d.name().isBlank()).count();
        } catch (Exception ignored) {}
        return 0;
    }

    private void start() {
        if (isRunning()) return;
        Path dir = project.getProjectDir();
        if (dir == null) { GlassNotification.toast(this, GlassNotification.Type.WARNING, "先打开或新建项目"); return; }
        build.setDisable(true);
        runner = new ProcessRunner();
        task = new PluginTask<>() {
            @Override protected BuildSummary call() throws Exception {
                BuildConfig cfg = JsonStore.load(dir.resolve("config.json"), BuildConfig.class);
                var det = plugin.swisskit.offlinepython.infra.PythonDetector.detect(cfg.getPython().getExecutable());
                if (!det.ok()) throw new IllegalStateException("未检测到 Python — 请先安装");
                return new BuildService().build(dir, cfg, det.executable(), log::log, runner);
            }
        };
        task.setOnSucceeded(e -> Platform.runLater(() -> {
            BuildSummary s = task.getValue();
            renderTiles(s);
            log.log("构建完成:" + s.totalWheels() + " wheels · " + humanBytes(s.totalBytes())
                    + " · 耗时 " + (s.durationMs() / 1000) + "s · 缓存命中 " + s.cacheHits());
            GlassNotification.toast(this, GlassNotification.Type.SUCCESS, "构建完成");
            progress.setProgress(1);
            progress.getStyleClass().removeAll("success", "danger");
            progress.getStyleClass().add("success");
            build.setDisable(false);
        }));
        task.setOnFailed(e -> Platform.runLater(() -> {
            log.log("ERROR: " + task.getException().getMessage());
            GlassNotification.toast(this, GlassNotification.Type.ERROR, "构建失败");
            progress.setProgress(0);
            progress.getStyleClass().removeAll("success", "danger");
            progress.getStyleClass().add("danger");
            build.setDisable(false);
        }));
        Thread t = new Thread(task, "OfflinePython-Build");
        t.setDaemon(true);
        t.start();
    }

    private void renderTiles(BuildSummary s) {
        tiles.getChildren().clear();
        tiles.add(makeTile("已下载", s.totalWheels() + ""), 0, 0);
        tiles.add(makeTile("耗时", (s.durationMs() / 1000) + "s"), 1, 0);
        tiles.add(makeTile("大小", humanBytes(s.totalBytes())), 2, 0);
        tiles.add(makeTile("缓存命中", s.cacheHits() + ""), 3, 0);
    }

    private StatTile makeTile(String label, String value) {
        StatTile t = new StatTile(label, value);
        GridPane.setHgrow(t, Priority.ALWAYS);
        t.setMaxWidth(Double.MAX_VALUE);
        return t;
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
            GlassNotification.toast(this, GlassNotification.Type.ERROR, "校验失败(未构建?)");
        }
    }

    private void render(VerifyResult r) {
        report.getChildren().clear();
        boolean fail = false; boolean warn = false;
        for (CheckResult c : presentChecks(r)) {
            if (c == null) continue;
            fail |= c.status() == Status.FAIL;
            warn |= c.status() == Status.WARN;
            report.getChildren().add(new HBox(10, new StatusBadge(c.status()), UiUtils.subLabel(c.detail())));
        }
        if (fail) {
            conclusion.setText("⚠ 仓库存在问题");
            conclusion.setStyle("-fx-background-color: " + OpbStyle.DANGER_SOFT + "; -fx-text-fill: " + OpbStyle.DANGER
                    + "; -fx-background-radius: 8; -fx-padding: 10 14; -fx-font-weight: 500;");
        } else if (warn) {
            conclusion.setText("✓ 仓库可用(含警告)");
            conclusion.setStyle("-fx-background-color: " + OpbStyle.WARNING_SOFT + "; -fx-text-fill: " + OpbStyle.WARNING
                    + "; -fx-background-radius: 8; -fx-padding: 10 14; -fx-font-weight: 500;");
        } else {
            conclusion.setText("✓ Repository OK");
            conclusion.setStyle("-fx-background-color: " + OpbStyle.SUCCESS_SOFT + "; -fx-text-fill: " + OpbStyle.SUCCESS
                    + "; -fx-background-radius: 8; -fx-padding: 10 14; -fx-font-weight: 500;");
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

    private static String humanBytes(long b) {
        if (b < 1024 * 1024) return (b / 1024) + " KB";
        return String.format("%.0f MB", b / (1024.0 * 1024));
    }

    public void cancel() {
        if (runner != null) runner.cancel();
        if (task != null) task.cancel(false);
    }
    public boolean isRunning() { return task != null && task.isRunningTask(); }

    @Override public String title() { return I18n.get("opb.build.title"); }
}
