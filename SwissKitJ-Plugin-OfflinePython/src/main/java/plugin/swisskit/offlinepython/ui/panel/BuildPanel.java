package plugin.swisskit.offlinepython.ui.panel;

import fan.summer.api.component.GlassNotification;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.stage.DirectoryChooser;
import plugin.swisskit.offlinepython.command.BuildService;
import plugin.swisskit.offlinepython.domain.BuildConfig;
import plugin.swisskit.offlinepython.infra.JsonStore;
import plugin.swisskit.offlinepython.infra.ProcessRunner;
import plugin.swisskit.offlinepython.task.PluginTask;
import plugin.swisskit.offlinepython.ui.LogConsole;

import java.io.File;

public class BuildPanel extends CommandPanel {
    private final ProgressBar progress = new ProgressBar();
    private PluginTask<Integer> task;
    private ProcessRunner runner;

    public BuildPanel(LogConsole log) {
        super(log);
        getChildren().add(new Label(title()));
        Button build = new Button("Build");
        Button cancel = new Button("Cancel");
        progress.setProgress(-1);
        build.setOnAction(e -> start());
        cancel.setOnAction(e -> { if (runner != null) runner.cancel(); });
        getChildren().addAll(new HBox(8, build, cancel), progress);
    }

    private void start() {
        DirectoryChooser dc = new DirectoryChooser();
        File dir = dc.showDialog(getScene().getWindow());
        if (dir == null) return;
        runner = new ProcessRunner();
        task = new PluginTask<>() {
            @Override protected Integer call() throws Exception {
                BuildConfig cfg = JsonStore.load(dir.toPath().resolve("config.json"), BuildConfig.class);
                var det = plugin.swisskit.offlinepython.infra.PythonDetector.detect(cfg.getPython().getExecutable());
                if (!det.ok()) throw new IllegalStateException("Python not detected — install Python first");
                return new BuildService().build(dir.toPath(), cfg, det.executable(), log::log, runner);
            }
        };
        task.setOnSucceeded(e -> Platform.runLater(() -> {
            int code = task.getValue();
            log.log(code == 0 ? "Build OK" : "Build failed (exit " + code + ")");
            GlassNotification.toast(this, code == 0 ? GlassNotification.Type.SUCCESS : GlassNotification.Type.ERROR,
                    code == 0 ? "Build complete" : "Build failed");
            progress.setProgress(code == 0 ? 1 : 0);
        }));
        task.setOnFailed(e -> Platform.runLater(() -> {
            log.log("ERROR: " + task.getException().getMessage());
            GlassNotification.toast(this, GlassNotification.Type.ERROR, "Build failed");
        }));
        new Thread(task).start();
    }

    public boolean isRunning() { return task != null && task.isRunningTask(); }

    @Override public String title() { return "Build Repository"; }
}
