package plugin.swisskit.offlinepython.ui.panel;

import fan.summer.api.component.GlassNotification;
import fan.summer.api.component.UiUtils;
import fan.summer.api.i18n.I18n;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import plugin.swisskit.offlinepython.command.BuildService;
import plugin.swisskit.offlinepython.domain.BuildConfig;
import plugin.swisskit.offlinepython.domain.BuildSummary;
import plugin.swisskit.offlinepython.domain.RequirementsFile;
import plugin.swisskit.offlinepython.infra.JsonStore;
import plugin.swisskit.offlinepython.infra.ProcessRunner;
import plugin.swisskit.offlinepython.task.PluginTask;
import plugin.swisskit.offlinepython.ui.LogConsole;
import plugin.swisskit.offlinepython.ui.ProjectContext;

import java.nio.file.Files;
import java.nio.file.Path;

public class BuildPanel extends CommandPanel {
    private final ProgressBar progress = new ProgressBar();
    private final Button build;
    private final Label banner = new Label();
    private final GridPane tiles = new GridPane();
    private PluginTask<BuildSummary> task;
    private ProcessRunner runner;
    private int depCount = 0;

    public BuildPanel(LogConsole log, ProjectContext project) {
        super(log, project);
        getChildren().add(titleNode());

        banner.setStyle("-fx-background-color: " + plugin.swisskit.offlinepython.ui.OpbStyle.ACCENT_SOFT
                + "; -fx-text-fill: " + plugin.swisskit.offlinepython.ui.OpbStyle.ACCENT
                + "; -fx-background-radius: 10; -fx-padding: 9 12 9 12;");
        refreshBanner();

        build = UiUtils.glassBtn("▶ 构建", true);
        Button cancel = UiUtils.glassBtn("✕ 取消", false);
        progress.setProgress(-1);
        progress.setPrefHeight(6);
        build.setOnAction(e -> start());
        cancel.setOnAction(e -> { if (runner != null) runner.cancel(); });
        tiles.setHgap(8); tiles.setVgap(8);

        getChildren().addAll(banner, new HBox(8, build, cancel), progress, tiles);
    }

    private void refreshBanner() {
        Path dir = project.getProjectDir();
        depCount = countDeps(dir);
        String plat = project.getConfig() != null && project.getConfig().getPython() != null
                ? project.getConfig().getPython().getPrimaryPlatform() : "?";
        banner.setText("📋 当前依赖：" + depCount + " 个直接  ·  目标 " + plat
                + (project.getConfig() != null && project.getConfig().getPython() != null
                    ? "  ·  Python " + project.getConfig().getPython().getVersion() : ""));
    }

    private int countDeps(Path dir) {
        if (dir == null) return 0;
        try {
            if (Files.exists(dir.resolve("requirements.txt")))
                return RequirementsFile.parse(Files.readString(dir.resolve("requirements.txt")))
                        .stream().filter(d -> !d.name().isBlank()).toArray().length;
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
            log.log("构建完成：" + s.totalWheels() + " wheels · " + humanBytes(s.totalBytes())
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
        addTile(0, "已下载", s.totalWheels() + "");
        addTile(1, "耗时", (s.durationMs() / 1000) + "s");
        addTile(2, "大小", humanBytes(s.totalBytes()));
        addTile(3, "缓存命中", s.cacheHits() + "");
    }
    private void addTile(int col, String label, String value) {
        Label l = new Label(label); l.setStyle("-fx-text-fill: " + plugin.swisskit.offlinepython.ui.OpbStyle.TEXT_TERTIARY + "; -fx-font-size: 10px;");
        Label v = new Label(value); v.setStyle("-fx-text-fill: " + plugin.swisskit.offlinepython.ui.OpbStyle.TEXT_PRIMARY + "; -fx-font-size: 16px; -fx-font-weight: 600;");
        javafx.scene.layout.VBox box = new javafx.scene.layout.VBox(2, l, v);
        box.setStyle(plugin.swisskit.offlinepython.ui.OpbStyle.card() + " -fx-padding: 10; -fx-alignment: center;");
        tiles.add(box, col, 0);
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
