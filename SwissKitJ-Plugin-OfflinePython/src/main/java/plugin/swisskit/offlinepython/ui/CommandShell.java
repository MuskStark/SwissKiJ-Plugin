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
import plugin.swisskit.offlinepython.infra.PythonDetector;
import plugin.swisskit.offlinepython.ui.control.LogDrawer;
import plugin.swisskit.offlinepython.ui.control.ProjectSwitcher;
import plugin.swisskit.offlinepython.ui.panel.BuildVerifyPanel;
import plugin.swisskit.offlinepython.ui.panel.ConfigPanel;
import plugin.swisskit.offlinepython.ui.panel.DoctorPanel;
import plugin.swisskit.offlinepython.ui.panel.ProjectPanel;

import java.io.File;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * OfflinePython command shell: a 4-region layout (top bar / left nav / center
 * content / right log drawer) with a 4-item nav (project / config / build / doctor).
 *
 * <p>This view is embedded in the host shell and consumes the controls built in
 * earlier tasks ({@link ProjectSwitcher}, {@link LogDrawer}) plus the four
 * content panels created in Tasks 6-7 ({@code ProjectPanel}, {@code ConfigPanel},
 * {@code BuildVerifyPanel}, {@link DoctorPanel}). The public API
 * ({@link #getView()}, {@link #hasRunningTasks()}, lifecycle hooks and
 * {@link #refreshPython()}) is consumed by {@code OfflinePythonPlugin}.
 */
public class CommandShell {
    private final BorderPane root = new BorderPane();
    private final VBox contentWrap = new VBox();
    private final LogConsole logConsole = new LogConsole();
    private final LogDrawer logDrawer = new LogDrawer(logConsole);
    private final Label pyBadge = new Label();
    private final ProjectContext project = new ProjectContext();
    private final ProjectSwitcher switcher = new ProjectSwitcher(this::createNew, this::openExisting);
    private final Map<String, Button> navButtons = new LinkedHashMap<>();
    private final Map<String, String> navLabels = new LinkedHashMap<>();
    private String current = "project";
    private BuildVerifyPanel buildVerifyPanel;

    public CommandShell() {
        root.getStylesheets().add(Themes.commonStylesheetUrl());
        root.setStyle("-fx-background-color: transparent;");
        navLabels.put("project", I18n.get("opb.nav.project"));
        navLabels.put("config",   I18n.get("opb.nav.config"));
        navLabels.put("build",    I18n.get("opb.nav.build"));
        navLabels.put("doctor",   I18n.get("opb.nav.doctor"));

        root.setTop(buildTopBar());
        root.setLeft(buildNav());
        contentWrap.setStyle("-fx-background-color: transparent;");
        root.setCenter(contentWrap);
        root.setRight(logDrawer);
        refreshPython();
        select("project");
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
        navEntry(nav, "project", "folder-outline", false);
        navEntry(nav, "config",  "package-variant-closed", true);
        navEntry(nav, "build",   "hammer-wrench", false);
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
            case "project" -> new ProjectPanel(logConsole, project, this::createNew, this::openExisting);
            case "config"  -> new ConfigPanel(logConsole, project);
            case "build"   -> buildVerifyPanel != null ? buildVerifyPanel : (buildVerifyPanel = new BuildVerifyPanel(logConsole, project));
            case "doctor"  -> new DoctorPanel(logConsole, project);
            default -> new Label("—");
        };
        contentWrap.getChildren().setAll(panel);
    }

    private void openExisting() {
        DirectoryChooser dc = new DirectoryChooser();
        File dir = dc.showDialog(root.getScene().getWindow());
        if (dir == null) return;
        project.openExisting(dir.toPath());
        switcher.updateName(dir.getName());
        logConsole.log("已打开项目: " + dir);
    }

    private void createNew() {
        DirectoryChooser dc = new DirectoryChooser();
        File dir = dc.showDialog(root.getScene().getWindow());
        if (dir == null) return;
        try {
            project.createNew(dir.toPath());
            switcher.updateName(dir.getName());
            logConsole.log("已新建项目: " + dir);
            GlassNotification.toast(root, GlassNotification.Type.SUCCESS, "项目已初始化");
        } catch (Exception e) {
            GlassNotification.toast(root, GlassNotification.Type.ERROR, "新建失败");
        }
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
        if (!ok && !"project".equals(current)) {
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
