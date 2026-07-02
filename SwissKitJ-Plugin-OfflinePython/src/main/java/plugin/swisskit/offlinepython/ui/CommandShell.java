package plugin.swisskit.offlinepython.ui;

import fan.summer.api.MdiIconUtil;
import fan.summer.api.component.GlassNotification;
import fan.summer.api.i18n.I18n;
import fan.summer.api.theme.Themes;
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.DirectoryChooser;
import plugin.swisskit.offlinepython.infra.OpbLogger;
import plugin.swisskit.offlinepython.infra.PythonDetector;
import plugin.swisskit.offlinepython.ui.control.ProjectSwitcher;
import plugin.swisskit.offlinepython.ui.panel.BuildVerifyPanel;
import plugin.swisskit.offlinepython.ui.panel.ConfigPanel;
import plugin.swisskit.offlinepython.ui.panel.DeployPanel;
import plugin.swisskit.offlinepython.ui.panel.DoctorPanel;

import java.io.File;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * OfflinePython command shell: a 3-region layout (top bar / left nav / center
 * content) with a 3-item nav (config / build / doctor).
 *
 * <p>The top bar shows the currently-open project name (read-only). Opening a
 * project is done from the {@link ConfigPanel} empty-state. Logs are written to
 * a file ({@code <project>/.offline-python.log}) via {@link OpbLogger} — there
 * is no on-screen console. The public API ({@link #getView()},
 * {@link #hasRunningTasks()}, lifecycle hooks and {@link #refreshPython()}) is
 * consumed by {@code OfflinePythonPlugin}.
 */
public class CommandShell {
    private final BorderPane root = new BorderPane();
    private final VBox contentWrap = new VBox();
    private final OpbLogger logger = new OpbLogger();
    private final Label pyBadge = new Label();
    private final ProjectContext project = new ProjectContext();
    private final ProjectSwitcher switcher = new ProjectSwitcher();
    private final Map<String, Button> navButtons = new LinkedHashMap<>();
    private final Map<String, String> navLabels = new LinkedHashMap<>();
    private String current = "config";
    private ConfigPanel configPanel;
    private BuildVerifyPanel buildVerifyPanel;
    private DeployPanel deployPanel;

    public CommandShell() {
        root.getStylesheets().add(Themes.commonStylesheetUrl());
        root.setStyle("-fx-background-color: transparent;");
        navLabels.put("config",   I18n.get("opb.nav.config"));
        navLabels.put("build",    I18n.get("opb.nav.build"));
        navLabels.put("doctor",   I18n.get("opb.nav.doctor"));
        navLabels.put("deploy",   I18n.get("opb.nav.deploy"));

        root.setTop(buildTopBar());
        root.setLeft(buildNav());
        contentWrap.setStyle("-fx-background-color: transparent;");
        root.setCenter(contentWrap);
        refreshPython();
        select("config");
    }

    private Node buildTopBar() {
        HBox bar = new HBox(12);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new javafx.geometry.Insets(8, 16, 8, 16));
        bar.setStyle(OpbStyle.topBar());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        pyBadge.setOnMouseClicked(e -> select("doctor"));
        bar.getChildren().addAll(switcher, spacer, pyBadge);
        return bar;
    }

    private Node buildNav() {
        VBox nav = new VBox(4);
        nav.setPrefWidth(OpbStyle.SIDEBAR_WIDTH);
        nav.setMinWidth(Region.USE_PREF_SIZE);
        nav.setPadding(new javafx.geometry.Insets(8));
        nav.setStyle("-fx-background-color: " + OpbStyle.GLASS_BG_SOFT + ";"
                + " -fx-border-color: transparent " + OpbStyle.GLASS_BORDER + " transparent transparent;"
                + " -fx-border-width: 0 1 0 0;");
        navEntry(nav, "config",  "package-variant-closed", true);
        navEntry(nav, "build",   "hammer-wrench", false);
        navEntry(nav, "deploy",  "download", false);
        navEntry(nav, "doctor",  "stethoscope", false);
        return nav;
    }

    /**
     * Build one nav entry. The button's graphic is an {@link HBox} holding
     * (icon {@link Text} + label {@link Label} + spacer + optional badge) so a
     * single node can carry an icon, a left-aligned label and a right-aligned
     * count badge (used by the config entry's dependency count).
     */
    private void navEntry(VBox nav, String key, String icon, boolean hasBadge) {
        Button b = new Button();
        b.setMaxWidth(Double.MAX_VALUE);
        b.setMnemonicParsing(false);
        HBox content = new HBox(10);
        content.setAlignment(Pos.CENTER_LEFT);
        Text iconNode = MdiIconUtil.createIcon(icon, 14,
                "-fx-fill: " + OpbStyle.navItemIconColor(key.equals(current)) + ";");
        iconNode.setUserData("icon");
        Label text = new Label(navLabels.get(key));
        text.setUserData("text");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        content.getChildren().addAll(iconNode, text, spacer);
        if (hasBadge) {
            Label badge = new Label("0");
            badge.setStyle(OpbStyle.countBadge());
            badge.setUserData("badge");
            badge.textProperty().bind(Bindings.createStringBinding(
                    () -> String.valueOf(countDeps()), project.projectDirProperty()));
            content.getChildren().add(badge);
        }
        b.setGraphic(content);
        applyNavStyle(b, key.equals(current), false);
        b.setOnMouseEntered(e -> { if (!key.equals(current)) applyNavStyle(b, false, true); });
        b.setOnMouseExited(e ->  { if (!key.equals(current)) applyNavStyle(b, false, false); });
        b.setOnAction(e -> select(key));
        navButtons.put(key, b);
        nav.getChildren().add(b);
    }

    /**
     * Refresh a nav item's style: button background/border + the icon fill color
     * (the icon is the first child of the HBox graphic, a {@link Text}).
     */
    private void applyNavStyle(Button b, boolean selected, boolean hover) {
        b.setStyle(OpbStyle.navItem(selected, hover) + " -fx-padding: 8 12 8 9;");
        if (b.getGraphic() instanceof HBox h && !h.getChildren().isEmpty()
                && h.getChildren().get(0) instanceof Text t) {
            t.setStyle("-fx-fill: " + OpbStyle.navItemIconColor(selected || hover) + ";");
        }
    }

    private void select(String key) {
        current = key;
        navButtons.forEach((k, b) -> applyNavStyle(b, k.equals(key), false));
        Node panel = switch (key) {
            case "config"  -> configPanel != null ? configPanel : (configPanel = new ConfigPanel(logger, project, this::openExisting, this::createNew, this::closeProject, () -> select("build")));
            case "build"   -> {
                if (buildVerifyPanel == null) buildVerifyPanel = new BuildVerifyPanel(logger, project);
                buildVerifyPanel.onShow();  // 每次进入都刷新依赖数/配置(可能刚在 config 页保存)
                yield buildVerifyPanel;
            }
            case "deploy"  -> {
                if (deployPanel == null) deployPanel = new DeployPanel(logger);
                yield deployPanel;
            }
            case "doctor"  -> new DoctorPanel(logger, project);
            default -> new Label("—");
        };
        contentWrap.getChildren().setAll(panel);
        VBox.setVgrow(panel, Priority.ALWAYS);  // 面板撑满内容区高度,使空状态可垂直居中
    }

    private void openExisting() {
        DirectoryChooser dc = new DirectoryChooser();
        File dir = dc.showDialog(root.getScene().getWindow());
        if (dir == null) return;
        project.openExisting(dir.toPath());
        // 日志写入项目目录下的 .offline-python.log
        logger.setLogFile(dir.toPath().resolve(".offline-python.log"));
        switcher.updateName(dir.getName());
        logger.log("已打开项目: " + dir);
        // 重新加载配置页(刷新空状态 → 依赖表)
        if (configPanel != null) configPanel.reload();
        // 构建页缓存失效(项目已切换)
        buildVerifyPanel = null;
    }

    private void createNew() {
        DirectoryChooser dc = new DirectoryChooser();
        File dir = dc.showDialog(root.getScene().getWindow());
        if (dir == null) return;
        try {
            project.createNew(dir.toPath());
            logger.setLogFile(dir.toPath().resolve(".offline-python.log"));
            switcher.updateName(dir.getName());
            logger.log("已新建项目: " + dir);
            GlassNotification.toast(root, GlassNotification.Type.SUCCESS, "项目已初始化");
            if (configPanel != null) configPanel.reload();
            buildVerifyPanel = null;
        } catch (Exception e) {
            GlassNotification.toast(root, GlassNotification.Type.ERROR, "新建失败");
        }
    }

    /** 后退:关闭当前项目,返回新建/打开项目界面。 */
    private void closeProject() {
        project.openExisting(null);  // dir=null → 清空 projectDir + config
        logger.setLogFile(null);
        switcher.updateName(null);
        logger.log("已关闭项目");
        if (configPanel != null) configPanel.reload();
        buildVerifyPanel = null;
    }

    private int countDeps() {
        var dir = project.getProjectDir();
        if (dir == null) return 0;
        try {
            var req = dir.resolve("requirements.txt");
            if (Files.exists(req)) {
                return (int) plugin.swisskit.offlinepython.domain.RequirementsFile
                        .parse(Files.readString(req)).stream()
                        .filter(d -> !d.name().isBlank()).count();
            }
        } catch (Exception ignored) {}
        return 0;
    }

    public void refreshPython() {
        var d = PythonDetector.detect(project.getConfig() != null ? project.getConfig().getPython().getExecutable() : null);
        boolean ok = d.ok();
        pyBadge.setText(ok
                ? I18n.get("opb.python.detected", d.pythonVersion(), d.pipVersion() == null ? "?" : d.pipVersion())
                : I18n.get("opb.python.missing"));
        pyBadge.setStyle("-fx-cursor: hand;" + OpbStyle.badge(ok));
        if (!ok && !"config".equals(current)) {
            contentWrap.getChildren().setAll(new PythonInstallGuide(this::refreshPython));
        }
    }

    public Node getView() { return root; }

    public boolean hasRunningTasks() {
        return buildVerifyPanel != null && buildVerifyPanel.isRunning();
    }

    public void onBackground() {}

    public void onForeground() { refreshPython(); }

    public void onUnload() {
        if (buildVerifyPanel != null) buildVerifyPanel.cancel();
    }
}
