package plugin.swisskit.offlinepython.ui.panel;

import fan.summer.api.component.SkNotification;
import fan.summer.api.component.UiUtils;
import fan.summer.api.host.PluginHost;
import fan.summer.api.host.TaskHandle;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import plugin.swisskit.offlinepython.command.BuildService;
import plugin.swisskit.offlinepython.command.PackageService;
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
import plugin.swisskit.offlinepython.infra.OpbLogger;
import plugin.swisskit.offlinepython.infra.ProcessRunner;
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
    private final ComboBox<String> pyVersionCombo = new ComboBox<>();
    private final ToggleGroup scopeGroup = new ToggleGroup();
    private final VBox report = new VBox(6);
    private final Label conclusion = new Label();
    private TaskHandle buildHandle;
    private ProcessRunner runner;
    private final Button packageBtn = UiUtils.glassBtn("📦 打包成 ZIP", false);

    public BuildVerifyPanel(OpbLogger log, ProjectContext project, PluginHost host) {
        super(log, project, host);
        PanelHeader header = new PanelHeader(host.i18n().get("opb.build.title"));
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

        // 目标 Python 版本选择
        pyVersionCombo.getItems().addAll("3.8.10", "3.9.13", "3.10.11", "3.11.9", "3.12.10", "3.13.1");
        pyVersionCombo.setStyle(UiUtils.comboStyle());
        Label pyLabel = UiUtils.subLabel("目标 Python");
        HBox pyRow = new HBox(8, pyLabel, pyVersionCombo);
        pyRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        buildSection.getChildren().add(pyRow);

        build = UiUtils.glassBtn("▶ 构建", true);
        Button cancel = UiUtils.glassBtn("✕ 取消", false);
        // 未构建时进度条不动画(0%);构建中显示实际进度
        progress.setProgress(0);
        progress.setPrefHeight(6);
        progress.setMaxWidth(Double.MAX_VALUE);
        build.setOnAction(e -> start());
        cancel.setOnAction(e -> { if (runner != null) runner.cancel(); });
        HBox buildRow = new HBox(8, build, cancel, progress);
        HBox.setHgrow(progress, Priority.ALWAYS);
        buildSection.getChildren().add(buildRow);

        tiles.setHgap(8); tiles.setVgap(8);
        buildSection.getChildren().add(tiles);

        packageBtn.setVisible(false);
        packageBtn.setManaged(false);
        packageBtn.setOnAction(e -> runPackage());
        HBox pkgRow = new HBox(8, packageBtn);
        buildSection.getChildren().add(pkgRow);

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

    /** 每次切换到本页时调用:重新从磁盘读取依赖数/配置,刷新 banner。 */
    public void onShow() {
        if (project.getProjectDir() != null) project.reloadConfig();
        refreshBanner();
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
        String ver = project.getConfig() != null && project.getConfig().getPython() != null
                ? project.getConfig().getPython().getVersion() : "3.12.10";
        // 同步 Python 版本选择器
        if (pyVersionCombo.getValue() == null) pyVersionCombo.setValue(ver);
        banner.setText("📋 当前依赖:" + depCount + " 个直接  ·  目标 " + plat + "  ·  Python " + ver);
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
        if (dir == null) { host.notifications().toast(this, SkNotification.Type.WARNING, "先打开或新建项目"); return; }
        // 把选中的 Python 版本写回 config(影响 pip --python-version + wheelhouse 路径)
        String pyVer = pyVersionCombo.getValue();
        if (pyVer != null && !pyVer.isBlank() && project.getConfig() != null) {
            project.getConfig().getPython().setVersion(pyVer);
            project.saveConfig();
        }
        build.setDisable(true);
        progress.setProgress(0);
        progress.getStyleClass().removeAll("success", "danger");
        runner = new ProcessRunner();
        buildHandle = host.tasks().submit("opb-build",
            () -> {
                BuildConfig cfg = JsonStore.load(dir.resolve("config.json"), BuildConfig.class);
                var det = plugin.swisskit.offlinepython.infra.PythonDetector.detect(cfg.getPython().getExecutable());
                if (!det.ok()) throw new IllegalStateException("未检测到 Python — 请先安装");
                return new BuildService().build(dir, cfg, det.executable(),
                        line -> { log.log(line); updateProgressApprox(); }, runner);
            },
            s -> {  // FX thread
                renderTiles(s);
                log.log("构建完成:" + s.totalWheels() + " wheels · " + humanBytes(s.totalBytes())
                        + " · 耗时 " + (s.durationMs() / 1000) + "s · 缓存命中 " + s.cacheHits());
                host.notifications().toast(this, SkNotification.Type.SUCCESS, "构建完成");
                progress.setProgress(1);
                progress.getStyleClass().add("success");
                build.setDisable(false);
                packageBtn.setVisible(true);
                packageBtn.setManaged(true);
                // 自动打包
                if (project.getConfig() != null && project.getConfig().getBundle() != null
                        && project.getConfig().getBundle().isAutoPackage()) {
                    runPackage();
                }
            },
            error -> {  // FX thread
                log.log("ERROR: " + error.getMessage());
                host.notifications().toast(this, SkNotification.Type.ERROR, "构建失败");
                progress.setProgress(0);
                progress.getStyleClass().add("danger");
                build.setDisable(false);
            });
    }

    private void runPackage() {
        Path dir = project.getProjectDir();
        if (dir == null) return;
        packageBtn.setDisable(true);
        host.tasks().submit("opb-package",
            () -> {
                BuildConfig cfg = JsonStore.load(dir.resolve("config.json"), BuildConfig.class);
                return new PackageService(log).packageBundle(dir, cfg);
            },
            zip -> {  // FX thread
                log.log("打包完成: " + zip);
                host.notifications().toast(this, SkNotification.Type.SUCCESS, "已打包: " + zip.getFileName());
                packageBtn.setDisable(false);
            },
            error -> {  // FX thread
                log.log("打包失败: " + error.getMessage());
                host.notifications().toast(this, SkNotification.Type.ERROR, "打包失败");
                packageBtn.setDisable(false);
            });
    }

    /** 构建中粗略推进进度条(每个 pip 日志行推进一点,封顶 95%)。 */
    private void updateProgressApprox() {
        javafx.application.Platform.runLater(() -> {
            double cur = progress.getProgress();
            if (cur < 0) cur = 0;
            if (cur < 0.95) progress.setProgress(cur + 0.03);
        });
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
        if (dir == null) { host.notifications().toast(this, SkNotification.Type.WARNING, "先打开或新建项目"); return; }
        try {
            Manifest m = JsonStore.load(dir.resolve("manifest.json"), Manifest.class);
            VerifyResult r = new VerifyService().verify(dir, m, selectedScope());
            render(r);
        } catch (Exception ex) {
            log.log("校验失败: " + ex.getMessage());
            host.notifications().toast(this, SkNotification.Type.ERROR, "校验失败(未构建?)");
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
        if (buildHandle != null) buildHandle.cancel();
    }
    public boolean isRunning() { return buildHandle != null && buildHandle.isRunning(); }

    @Override public String title() { return host.i18n().get("opb.build.title"); }
}
