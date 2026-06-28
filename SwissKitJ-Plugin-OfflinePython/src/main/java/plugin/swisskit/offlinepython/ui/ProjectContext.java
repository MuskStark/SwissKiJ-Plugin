package plugin.swisskit.offlinepython.ui;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import plugin.swisskit.offlinepython.command.InitService;
import plugin.swisskit.offlinepython.domain.BuildConfig;
import plugin.swisskit.offlinepython.infra.JsonStore;

import java.nio.file.Path;

/**
 * Holds the currently-open project directory and its BuildConfig, observable to the shell
 * and panels. Replaces per-panel DirectoryChooser with a shared "current project".
 */
public class ProjectContext {

    private final ObjectProperty<Path> projectDir = new SimpleObjectProperty<>();
    private final ObjectProperty<BuildConfig> config = new SimpleObjectProperty<>();

    public ObjectProperty<Path> projectDirProperty() { return projectDir; }
    public ObjectProperty<BuildConfig> configProperty() { return config; }

    public Path getProjectDir() { return projectDir.get(); }
    public BuildConfig getConfig() { return config.get(); }

    public boolean hasProject() { return projectDir.get() != null; }

    /** Open an existing project: load config.json (or defaults if missing). */
    public void openExisting(Path dir) {
        projectDir.set(dir);
        reloadConfig();
    }

    /** Create a new project skeleton at dir, then open it. */
    public void createNew(Path dir) throws Exception {
        new InitService().initialize(dir);
        openExisting(dir);
    }

    public void reloadConfig() {
        Path dir = projectDir.get();
        if (dir == null) { config.set(null); return; }
        Path cfgFile = dir.resolve("config.json");
        try {
            config.set(java.nio.file.Files.exists(cfgFile)
                    ? JsonStore.load(cfgFile, BuildConfig.class)
                    : BuildConfig.defaults());
        } catch (Exception e) {
            config.set(BuildConfig.defaults());
        }
    }

    public void saveConfig() {
        Path dir = projectDir.get();
        BuildConfig cfg = config.get();
        if (dir == null || cfg == null) return;
        try { JsonStore.save(cfg, dir.resolve("config.json")); }
        catch (Exception ignored) { }
    }
}
