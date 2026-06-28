package plugin.swisskit.offlinepython.ui;

import fan.summer.api.component.GlassNotification;
import fan.summer.api.component.UiUtils;
import fan.summer.api.i18n.I18n;
import fan.summer.api.theme.Themes;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import plugin.swisskit.offlinepython.infra.PythonDetector;
import plugin.swisskit.offlinepython.ui.panel.BuildPanel;
import plugin.swisskit.offlinepython.ui.panel.DepsPanel;
import plugin.swisskit.offlinepython.ui.panel.DoctorPanel;
import plugin.swisskit.offlinepython.ui.panel.InitPanel;
import plugin.swisskit.offlinepython.ui.panel.VerifyPanel;

import java.io.File;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;

public class CommandShell {
    // V1 commands are active; V2/V3 are shown disabled per spec §9 two-group nav.
    private static final String[][] GROUPS = {
        {"init", "deps", "build", "verify"},                       // 仓库操作 (V1 active)
        {"doctor"}                                                   // 查看与工具 (V1 active)
    };
    private static final Map<String, String> LABELS = new LinkedHashMap<>();
    static {
        LABELS.put("init",   "初始化 init");
        LABELS.put("deps",   "依赖配置 deps");
        LABELS.put("build",  "构建 build");
        LABELS.put("verify", "校验 verify");
        LABELS.put("update", "增量 update");   // V2
        LABELS.put("clean",  "清理 clean");    // V2
        LABELS.put("pack",   "打包 pack");     // V3
        LABELS.put("export", "导出 export");   // V3
        LABELS.put("list",   "列表 list");     // V2
        LABELS.put("info",   "信息 info");     // V2
        LABELS.put("cache",  "缓存 cache");    // V2
        LABELS.put("doctor", "诊断 doctor");
    }

    private final BorderPane root = new BorderPane();
    private final HBox topBar = new HBox(10);
    private final VBox contentWrap = new VBox();
    private final LogConsole logConsole = new LogConsole();
    private final Label pyBadge = new Label();
    private final Label projectLabel = new Label();
    private final ProjectContext project = new ProjectContext();
    private final Map<String, Button> navButtons = new LinkedHashMap<>();
    private String current = "init";
    private BuildPanel buildPanel;

    public CommandShell() {
        root.getStylesheets().add(Themes.commonStylesheetUrl());
        root.setStyle("-fx-background-color: transparent;");
        root.setTop(buildTopBar());
        root.setLeft(buildNav());
        contentWrap.setStyle("-fx-background-color: transparent;");
        contentWrap.setPadding(new javafx.geometry.Insets(0));
        root.setCenter(contentWrap);
        root.setBottom(buildLogDock());
        root.addEventHandler(NavEvent.NAV, e -> select(e.target()));
        refreshPython();
        select("init");
    }

    private Node buildTopBar() {
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new javafx.geometry.Insets(9, 14, 9, 14));
        topBar.setStyle("-fx-background-color: rgba(255,255,255,0.035); -fx-border-color: transparent transparent rgba(255,255,255,0.08) transparent;");

        projectLabel.setStyle("-fx-background-color: rgba(255,255,255,0.06); -fx-border-color: rgba(255,255,255,0.12);"
                + " -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 5 10 5 10;"
                + " -fx-text-fill: rgba(255,255,255,0.85);");
        projectLabel.setText("项目: (未打开) ▾");
        projectLabel.setOnMouseClicked(e -> openExisting());
        Button newBtn = UiUtils.glassBtn("＋ 新建", false);
        newBtn.setOnAction(e -> createNew());
        Button openBtn = UiUtils.glassBtn("📂 打开", false);
        openBtn.setOnAction(e -> openExisting());
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        pyBadge.setStyle(OpbStyle.badge(true));
        topBar.getChildren().addAll(projectLabel, newBtn, openBtn, spacer, pyBadge);
        return topBar;
    }

    private Node buildNav() {
        VBox nav = new VBox(4);
        nav.setPrefWidth(OpbStyle.SIDEBAR_WIDTH);
        nav.setMinWidth(Region.USE_PREF_SIZE);
        nav.setPadding(new javafx.geometry.Insets(10, 8, 10, 8));
        nav.setStyle("-fx-border-color: transparent rgba(255,255,255,0.08) transparent transparent;"
                + " -fx-background-color: rgba(0,0,0,0.18);");

        addGroup(nav, "仓库操作", new String[]{"init","deps","build","update","verify","clean","pack","export"});
        addGroup(nav, "查看与工具", new String[]{"list","info","cache","doctor"});
        return nav;
    }

    private void addGroup(VBox nav, String title, String[] keys) {
        Label g = new Label(title);
        g.setStyle(OpbStyle.groupLabel());
        g.setPadding(new javafx.geometry.Insets(8, 8, 4, 8));
        nav.getChildren().add(g);
        for (String key : keys) {
            boolean active = isActive(key);
            Button b = new Button(LABELS.get(key));
            b.setMaxWidth(Double.MAX_VALUE);
            b.setAlignment(Pos.CENTER_LEFT);
            b.setMnemonicParsing(false);
            if (active) {
                applyNavStyle(b, key.equals(current), false);
                b.setOnMouseEntered(e -> { if (!key.equals(current)) applyNavStyle(b, false, true); });
                b.setOnMouseExited(e ->  { if (!key.equals(current)) applyNavStyle(b, false, false); });
                b.setOnAction(e -> select(key));
                if (key.equals("deps")) {
                    Label badge = new Label("0");
                    badge.setStyle(OpbStyle.countBadge());
                    b.setGraphic(badge);
                    badge.textProperty().bind(javafx.beans.binding.Bindings.createStringBinding(
                        () -> String.valueOf(countDeps()), project.projectDirProperty()));
                }
            } else {
                b.setStyle(OpbStyle.navItemDisabled());
                b.setDisable(false);
                Label tag = new Label(versionTag(key));
                tag.setStyle("-fx-text-fill: rgba(255,255,255,0.25); -fx-font-size: 9px;");
                b.setGraphic(tag);
            }
            navButtons.put(key, b);
            nav.getChildren().add(b);
        }
    }

    private boolean isActive(String key) {
        for (String[] g : GROUPS) for (String k : g) if (k.equals(key)) return true;
        return false;
    }
    private String versionTag(String key) {
        return switch (key) { case "update","clean","list","info","cache" -> "V2"; case "pack","export" -> "V3"; default -> ""; };
    }
    private void applyNavStyle(Button b, boolean selected, boolean hover) {
        b.setStyle(OpbStyle.navItem(selected, hover) + " -fx-padding: 7 12 7 12;");
    }

    private Node buildLogDock() {
        VBox dock = new VBox();
        dock.setStyle("-fx-background-color: rgba(0,0,0,0.25); -fx-border-color: rgba(255,255,255,0.08) transparent transparent transparent;");
        HBar bar = new HBar(8);
        bar.setPadding(new javafx.geometry.Insets(6, 14, 6, 14));
        Label title = new Label("日志控制台 ▾");
        title.setStyle("-fx-text-fill: rgba(255,255,255,0.85); -fx-cursor: hand;");
        title.setOnMouseClicked(e -> { boolean c = !logConsole.isCollapsed(); logConsole.setCollapsed(c); title.setText(c ? "日志控制台 ▸" : "日志控制台 ▾"); });
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        bar.getChildren().addAll(title, sp);
        for (LogLevel lv : new LogLevel[]{LogLevel.DEBUG, LogLevel.INFO, LogLevel.WARN, LogLevel.ERROR}) {
            Label pill = new Label(lv.name());
            pill.setStyle("-fx-background-color: rgba(255,255,255,0.06); -fx-text-fill: rgba(255,255,255,0.6);"
                    + " -fx-background-radius: 6; -fx-padding: 2 9;");
            pill.setUserData(Boolean.TRUE);
            pill.setOnMouseClicked(e -> {
                boolean on = !(Boolean) pill.getUserData();
                pill.setUserData(on);
                pill.setStyle("-fx-background-color: " + (on ? "rgba(91,140,247,0.20)" : "rgba(255,255,255,0.06)")
                        + "; -fx-text-fill: " + (on ? "#9cc0ff" : "rgba(255,255,255,0.5)")
                        + "; -fx-background-radius: 6; -fx-padding: 2 9;");
                rebuildVisibleLevels(bar);
            });
            bar.getChildren().add(pill);
        }
        dock.getChildren().addAll(bar, logConsole);
        return dock;
    }

    private void rebuildVisibleLevels(HBar bar) {
        EnumSet<LogLevel> visible = EnumSet.noneOf(LogLevel.class);
        for (Node n : bar.getChildren()) {
            if (n instanceof Label l && l.getUserData() == Boolean.TRUE && levelOf(l.getText()) != null)
                visible.add(levelOf(l.getText()));
        }
        logConsole.setVisibleLevels(visible);
    }
    private LogLevel levelOf(String name) {
        try { return LogLevel.valueOf(name); } catch (Exception e) { return null; }
    }

    private void select(String key) {
        if (!isActive(key)) return;
        current = key;
        navButtons.forEach((k, b) -> {
            if (isActive(k)) applyNavStyle(b, k.equals(key), false);
        });
        Node panel = switch (key) {
            case "init"   -> new InitPanel(logConsole, project);
            case "deps"   -> new DepsPanel(logConsole, project);
            case "build"  -> buildPanel != null ? buildPanel : (buildPanel = new BuildPanel(logConsole, project));
            case "verify" -> new VerifyPanel(logConsole, project);
            case "doctor" -> new DoctorPanel(logConsole, project);
            default -> new Label("—");
        };
        contentWrap.getChildren().setAll(panel);
        // ensure the log dock stays visible below content by keeping root.bottom set
    }

    private void openExisting() {
        DirectoryChooser dc = new DirectoryChooser();
        File dir = dc.showDialog(root.getScene().getWindow());
        if (dir == null) return;
        project.openExisting(dir.toPath());
        projectLabel.setText("项目: " + dir.getAbsolutePath() + " ▾");
        logConsole.log("已打开项目: " + dir);
    }

    private void createNew() {
        DirectoryChooser dc = new DirectoryChooser();
        File dir = dc.showDialog(root.getScene().getWindow());
        if (dir == null) return;
        try { project.createNew(dir.toPath()); projectLabel.setText("项目: " + dir.getAbsolutePath() + " ▾");
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
            if (java.nio.file.Files.exists(req))
                return (int) plugin.swisskit.offlinepython.domain.RequirementsFile
                        .parse(java.nio.file.Files.readString(req)).stream()
                        .filter(d -> !d.name().isBlank()).count();
        } catch (Exception ignored) {}
        return 0;
    }

    public void refreshPython() {
        var d = PythonDetector.detect(project.getConfig() != null ? project.getConfig().getPython().getExecutable() : null);
        boolean ok = d.ok();
        pyBadge.setText(ok
                ? I18n.get("opb.python.detected", d.pythonVersion(), d.pipVersion() == null ? "?" : d.pipVersion())
                : I18n.get("opb.python.missing"));
        pyBadge.setStyle(OpbStyle.badge(ok));
        if (!ok && !"init".equals(current)) contentWrap.getChildren().setAll(new PythonInstallGuide(this::refreshPython));
    }

    public Node getView() { return root; }
    public boolean hasRunningTasks() { return buildPanel != null && buildPanel.isRunning(); }
    public void onBackground() {}
    public void onForeground() { refreshPython(); }
    public void onUnload() { if (buildPanel != null) buildPanel.cancel(); }

    /** Simple HBox subclass for the log dock bar (named to avoid import clutter). */
    private static class HBar extends HBox {
        HBar(double spacing) { super(spacing); setAlignment(Pos.CENTER_LEFT); }
    }
}
