# OfflinePython UI Glass-Theme Alignment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restyle the OfflinePython plugin UI to the SwissKitJ host "glass" theme while preserving the left-nav + bottom-log layout and all current behavior.

**Architecture:** Introduce a single `OpbStyle` helper that centralizes the host glass design tokens (concrete values mirroring `swisskit-common.css`) and small style builders. Attach `Themes.commonStylesheetUrl()` to the shell root so host global selectors (scrollbars, progress bar) apply. Across the shell, log console, install guide, and five command panels, replace raw JavaFX nodes + ad-hoc inline CSS with `UiUtils` helpers (`glassBtn`, `sectionTitle`, `subLabel`, `fieldStyle`) and `OpbStyle` builders. No business-logic changes.

**Tech Stack:** Java 21, JavaFX 21 (`provided`), SwissKitJ-Api 3.1.0 (`provided`), Lombok (`provided`), JUnit 5 (test), Maven shade.

**Spec:** `docs/superpowers/specs/2026-06-28-offlinepython-ui-glass-design.md`

---

## Build tool note

This machine has **no `mvn` on PATH**. Run every `mvn …` command below from **IntelliJ's Maven tool window** (Maven → OfflinePython → Lifecycle / Plugins), or use **Build → Build Module** in the IDE, or `brew install maven` once. The IDE MCP `build_project` tool (targeting `SwissKitJ-Plugin-OfflinePython`) is the equivalent and is the primary mechanism in this environment. Expected output for compile steps is `BUILD SUCCESS`; for the test step, the listed test count passing.

UI nodes are not unit-tested headless (JavaFX). Only `OpbStyle` (pure strings + a `Status` switch) gets a JUnit test. All other tasks verify via **compile**, then a final **DevLauncher** visual check (Task 11).

---

## File Structure

```
SwissKitJ-Plugin-OfflinePython/src/main/java/plugin/swisskit/offlinepython/
├── ui/
│   ├── OpbStyle.java            # NEW — host glass token constants + style builders
│   ├── CommandShell.java        # MODIFY — glass nav, badge, host stylesheet on root
│   ├── LogConsole.java          # MODIFY — glass textarea style
│   ├── PythonInstallGuide.java  # MODIFY — glass card + glass buttons/fields
│   └── panel/
│       ├── CommandPanel.java    # MODIFY — glass card base + titleNode() helper
│       ├── InitPanel.java       # MODIFY — glass button + section title
│       ├── DepsPanel.java       # MODIFY — glass list/buttons/fields
│       ├── BuildPanel.java      # MODIFY — glass buttons + status-colored progress
│       ├── VerifyPanel.java     # MODIFY — glass button + status-colored badges
│       └── DoctorPanel.java     # MODIFY — glass button + status-colored rows
└── src/test/java/plugin/swisskit/offlinepython/
    └── OpbStyleTest.java         # NEW — locks the glass token contract
```

Unchanged: `OfflinePythonPlugin.java`, `domain/`, `infra/`, `command/`, `task/`, all existing tests, `pom.xml`, i18n bundles. Existing i18n keys used: `opb.init.title`, `opb.deps.title`, `opb.build.title`, `opb.verify.title`, `opb.doctor.title`, `opb.python.detected`, `opb.python.missing` (all already present in `messages.properties` / `messages_zh.properties`).

---

## Task 1: OpbStyle — host glass tokens + style builders (TDD)

**Files:**
- Create: `src/main/java/plugin/swisskit/offlinepython/ui/OpbStyle.java`
- Test: `src/test/java/plugin/swisskit/offlinepython/OpbStyleTest.java`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/plugin/swisskit/offlinepython/OpbStyleTest.java`:

```java
package plugin.swisskit.offlinepython;

import org.junit.jupiter.api.Test;
import plugin.swisskit.offlinepython.domain.Status;
import plugin.swisskit.offlinepython.ui.OpbStyle;

import static org.junit.jupiter.api.Assertions.*;

class OpbStyleTest {

    @Test
    void mirrorsHostAccentToken() {
        assertEquals("#5b8cf7", OpbStyle.ACCENT);
        assertEquals("rgba(255,255,255,0.055)", OpbStyle.GLASS_BG);
        assertEquals("rgba(255,255,255,0.10)", OpbStyle.GLASS_BORDER);
        assertEquals("#4cd97b", OpbStyle.SUCCESS);
        assertEquals("#f25c5c", OpbStyle.DANGER);
    }

    @Test
    void cardStyleUsesGlassTokensAndRadius() {
        String s = OpbStyle.card();
        assertTrue(s.contains(OpbStyle.GLASS_BG));
        assertTrue(s.contains(OpbStyle.GLASS_BORDER));
        assertTrue(s.contains(String.valueOf(OpbStyle.CARD_RADIUS)));
    }

    @Test
    void navItemSelectedUsesAccent() {
        String sel = OpbStyle.navItem(true, false);
        assertTrue(sel.contains(OpbStyle.ACCENT_SOFT));
        assertTrue(sel.contains(OpbStyle.ACCENT));
    }

    @Test
    void navItemIdleIsTransparentAndHoverUsesGlassHover() {
        assertTrue(OpbStyle.navItem(false, false).contains("transparent"));
        assertTrue(OpbStyle.navItem(false, true).contains(OpbStyle.GLASS_BG_HOVER));
    }

    @Test
    void badgeColorFollowsOkFlag() {
        assertTrue(OpbStyle.badge(true).contains(OpbStyle.SUCCESS));
        assertTrue(OpbStyle.badge(false).contains(OpbStyle.DANGER));
    }

    @Test
    void statusColorMapsEachStatus() {
        assertEquals(OpbStyle.SUCCESS, OpbStyle.statusColor(Status.PASS));
        assertEquals(OpbStyle.WARNING, OpbStyle.statusColor(Status.WARN));
        assertEquals(OpbStyle.DANGER, OpbStyle.statusColor(Status.FAIL));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl SwissKitJ-Plugin-OfflinePython test -Dtest=OpbStyleTest -B`
Expected: FAIL — `OpbStyle` class not found (compile error).

- [ ] **Step 3: Implement OpbStyle**

Create `src/main/java/plugin/swisskit/offlinepython/ui/OpbStyle.java`:

```java
package plugin.swisskit.offlinepython.ui;

import plugin.swisskit.offlinepython.domain.Status;

/**
 * Centralized SwissKitJ host "glass" design tokens and small style helpers for the
 * OfflinePython plugin. Token values mirror swisskit-common.css and
 * fan.summer.api.component.UiUtils so the plugin reads as part of the host shell.
 * Concrete values are used (not JavaFX looked-up color variables) because the plugin
 * root may not carry the .root style class.
 */
public final class OpbStyle {

    private OpbStyle() {}

    // Host glass tokens (mirror swisskit-common.css)
    public static final String ACCENT          = "#5b8cf7";
    public static final String ACCENT_SOFT     = "rgba(91,140,247,0.18)";
    public static final String GLASS_BG        = "rgba(255,255,255,0.055)";
    public static final String GLASS_BG_HOVER  = "rgba(255,255,255,0.09)";
    public static final String GLASS_BORDER    = "rgba(255,255,255,0.10)";
    public static final String TEXT_PRIMARY    = "rgba(255,255,255,0.92)";
    public static final String TEXT_SECONDARY  = "rgba(255,255,255,0.50)";
    public static final String SUCCESS         = "#4cd97b";
    public static final String SUCCESS_SOFT    = "rgba(76,217,123,0.16)";
    public static final String WARNING         = "#f5a623";
    public static final String DANGER          = "#f25c5c";
    public static final String DANGER_SOFT     = "rgba(242,92,92,0.16)";
    public static final String LOG_INNER_BG    = "rgba(0,0,0,0.25)";

    public static final int CARD_RADIUS   = 12;
    public static final int NAV_RADIUS    = 8;
    public static final int SIDEBAR_WIDTH = 220;

    /** Glass card surface: translucent fill + hairline border + 12px radius. */
    public static String card() {
        return "-fx-background-color: " + GLASS_BG + ";"
             + "-fx-background-radius: " + CARD_RADIUS + ";"
             + "-fx-border-color: " + GLASS_BORDER + ";"
             + "-fx-border-radius: " + CARD_RADIUS + ";";
    }

    /** Nav item style for the given selection/hover state. */
    public static String navItem(boolean selected, boolean hover) {
        String bg = selected ? ACCENT_SOFT : (hover ? GLASS_BG_HOVER : "transparent");
        String fg = selected ? ACCENT : TEXT_SECONDARY;
        return "-fx-background-color: " + bg + ";"
             + "-fx-text-fill: " + fg + ";"
             + "-fx-background-radius: " + NAV_RADIUS + ";"
             + "-fx-cursor: hand;";
    }

    /** Python badge capsule style; green when ok, red when missing. */
    public static String badge(boolean ok) {
        return "-fx-background-color: " + (ok ? SUCCESS_SOFT : DANGER_SOFT) + ";"
             + "-fx-text-fill: " + (ok ? SUCCESS : DANGER) + ";"
             + "-fx-background-radius: 10;"
             + "-fx-padding: 4 10 4 10;";
    }

    /** Foreground color for a verify/doctor Status. */
    public static String statusColor(Status s) {
        if (s == null) return TEXT_SECONDARY;
        return switch (s) {
            case PASS -> SUCCESS;
            case WARN -> WARNING;
            case FAIL -> DANGER;
        };
    }

    /** Inline style for the log console TextArea. */
    public static String logTextAreaStyle() {
        return "-fx-control-inner-background: " + LOG_INNER_BG + ";"
             + "-fx-text-fill: " + TEXT_PRIMARY + ";"
             + "-fx-font-size: 12px;";
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -pl SwissKitJ-Plugin-OfflinePython test -Dtest=OpbStyleTest -B`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add SwissKitJ-Plugin-OfflinePython/src/main/java/plugin/swisskit/offlinepython/ui/OpbStyle.java \
        SwissKitJ-Plugin-OfflinePython/src/test/java/plugin/swisskit/offlinepython/OpbStyleTest.java
git commit -m "feat(OfflinePython): add OpbStyle host glass-token helpers with tests"
```

---

## Task 2: CommandPanel base — glass card surface + titleNode helper

**Files:**
- Modify: `src/main/java/plugin/swisskit/offlinepython/ui/panel/CommandPanel.java`

- [ ] **Step 1: Replace the file**

Replace the entire contents of `CommandPanel.java` with:

```java
package plugin.swisskit.offlinepython.ui.panel;

import fan.summer.api.component.UiUtils;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import plugin.swisskit.offlinepython.ui.LogConsole;
import plugin.swisskit.offlinepython.ui.OpbStyle;

/** Base for command panels: glass card surface + log access + a section-title node. */
public abstract class CommandPanel extends VBox {
    protected final LogConsole log;

    protected CommandPanel(LogConsole log) {
        this.log = log;
        setSpacing(14);
        setStyle(OpbStyle.card() + " -fx-padding: 18;");
    }

    /** A host-styled section-header label for this panel's title. */
    protected Label titleNode() {
        return UiUtils.sectionTitle(title());
    }

    public abstract String title();
}
```

- [ ] **Step 2: Build to verify it compiles**

Run: `mvn -pl SwissKitJ-Plugin-OfflinePython compile -B`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add SwissKitJ-Plugin-OfflinePython/src/main/java/plugin/swisskit/offlinepython/ui/panel/CommandPanel.java
git commit -m "feat(OfflinePython): glass card surface + titleNode helper on CommandPanel base"
```

---

## Task 3: LogConsole — glass textarea style

**Files:**
- Modify: `src/main/java/plugin/swisskit/offlinepython/ui/LogConsole.java`

- [ ] **Step 1: Replace the file**

Replace the entire contents of `LogConsole.java` with:

```java
package plugin.swisskit.offlinepython.ui;

import javafx.application.Platform;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;

public class LogConsole extends BorderPane {
    private final TextArea area = new TextArea();

    public LogConsole() {
        area.setEditable(false);
        area.setWrapText(true);
        area.setStyle(OpbStyle.logTextAreaStyle());
        getStyleClass().add("content-scroll");
        setCenter(area);
        setPrefHeight(168);
    }

    public void log(String line) {
        String ts = java.time.LocalTime.now().withNano(0).toString();
        Platform.runLater(() -> {
            area.appendText("[" + ts + "] " + line + "\n");
            area.setScrollTop(Double.MAX_VALUE);
        });
    }
}
```

- [ ] **Step 2: Build to verify it compiles**

Run: `mvn -pl SwissKitJ-Plugin-OfflinePython compile -B`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add SwissKitJ-Plugin-OfflinePython/src/main/java/plugin/swisskit/offlinepython/ui/LogConsole.java
git commit -m "feat(OfflinePython): glass style for LogConsole textarea + thin scrollbars"
```

---

## Task 4: CommandShell — host stylesheet + glass nav + glass badge

**Files:**
- Modify: `src/main/java/plugin/swisskit/offlinepython/ui/CommandShell.java`

- [ ] **Step 1: Replace the file**

Replace the entire contents of `CommandShell.java` with:

```java
package plugin.swisskit.offlinepython.ui;

import fan.summer.api.i18n.I18n;
import fan.summer.api.theme.Themes;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import plugin.swisskit.offlinepython.infra.PythonDetector;
import plugin.swisskit.offlinepython.ui.panel.BuildPanel;
import plugin.swisskit.offlinepython.ui.panel.DepsPanel;
import plugin.swisskit.offlinepython.ui.panel.DoctorPanel;
import plugin.swisskit.offlinepython.ui.panel.InitPanel;
import plugin.swisskit.offlinepython.ui.panel.VerifyPanel;

import java.util.LinkedHashMap;
import java.util.Map;

public class CommandShell {
    private static final String[] NAV = {"init", "deps", "build", "verify", "doctor"};

    private final BorderPane root = new BorderPane();
    private final StackPane content = new StackPane();
    private final LogConsole logConsole = new LogConsole();
    private final Label pyBadge = new Label();
    private final Map<String, Button> navButtons = new LinkedHashMap<>();
    private String current = "init";
    private BuildPanel buildPanel;

    public CommandShell() {
        // Attach the host glass stylesheet to the subtree so global selectors
        // (thin scrollbars, default .progress-bar, looked-up tokens) apply regardless
        // of the Scene that hosts this plugin.
        root.getStylesheets().add(Themes.commonStylesheetUrl());
        root.setStyle("-fx-background-color: transparent;");

        root.setLeft(buildNav());
        content.setStyle("-fx-background-color: transparent;");
        root.setCenter(content);

        BorderPane bottomBar = new BorderPane();
        bottomBar.setCenter(logConsole);
        root.setBottom(bottomBar);

        BorderPane top = new BorderPane();
        top.setRight(pyBadge);
        root.setTop(top);

        refreshPython();
        select("init");
    }

    private Node buildNav() {
        VBox nav = new VBox(4);
        nav.setPrefWidth(OpbStyle.SIDEBAR_WIDTH);
        nav.setMinWidth(Region.USE_PREF_SIZE);
        nav.setStyle("-fx-padding: 10;");
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("init",   I18n.get("opb.init.title"));
        labels.put("deps",   I18n.get("opb.deps.title"));
        labels.put("build",  I18n.get("opb.build.title"));
        labels.put("verify", I18n.get("opb.verify.title"));
        labels.put("doctor", I18n.get("opb.doctor.title"));
        for (String key : NAV) {
            Button b = new Button(labels.get(key));
            b.setMaxWidth(Double.MAX_VALUE);
            b.setAlignment(Pos.CENTER_LEFT);
            b.setMnemonicParsing(false);
            applyNavStyle(b, key.equals(current), false);
            b.setOnMouseEntered(e -> { if (!key.equals(current)) applyNavStyle(b, false, true); });
            b.setOnMouseExited(e ->  { if (!key.equals(current)) applyNavStyle(b, false, false); });
            b.setOnAction(e -> select(key));
            navButtons.put(key, b);
            nav.getChildren().add(b);
        }
        return nav;
    }

    private void applyNavStyle(Button b, boolean selected, boolean hover) {
        b.setStyle(OpbStyle.navItem(selected, hover) + " -fx-padding: 9 12 9 12;");
    }

    private void select(String key) {
        current = key;
        navButtons.forEach((k, b) -> applyNavStyle(b, k.equals(key), false));
        Node panel = switch (key) {
            case "init"   -> new InitPanel(logConsole);
            case "deps"   -> new DepsPanel(logConsole);
            case "build"  -> buildPanel != null ? buildPanel : (buildPanel = new BuildPanel(logConsole));
            case "verify" -> new VerifyPanel(logConsole);
            case "doctor" -> new DoctorPanel(logConsole);
            default -> new Label("—");
        };
        content.getChildren().setAll(panel);
    }

    public void refreshPython() {
        var d = PythonDetector.detect(null);
        boolean ok = d.ok();
        pyBadge.setText(ok
                ? I18n.get("opb.python.detected", d.pythonVersion(), d.pipVersion() == null ? "?" : d.pipVersion())
                : I18n.get("opb.python.missing"));
        pyBadge.setStyle(OpbStyle.badge(ok));
        if (!ok) content.getChildren().setAll(new PythonInstallGuide(this::refreshPython));
    }

    public Node getView() { return root; }
    public boolean hasRunningTasks() { return buildPanel != null && buildPanel.isRunning(); }
    public void onBackground() {}
    public void onForeground() { refreshPython(); }
    public void onUnload() { if (buildPanel != null) buildPanel.cancel(); }
}
```

> Note: lifecycle/selection semantics are preserved from the current code — constructor calls `refreshPython()` then selects the first nav item; `onForeground` re-runs detection; `onUnload` cancels any in-flight build (unchanged from original).

- [ ] **Step 2: Build to verify it compiles**

Run: `mvn -pl SwissKitJ-Plugin-OfflinePython compile -B`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add SwissKitJ-Plugin-OfflinePython/src/main/java/plugin/swisskit/offlinepython/ui/CommandShell.java
git commit -m "feat(OfflinePython): glass nav + badge + host stylesheet on CommandShell root"
```

---

## Task 5: InitPanel — glass button + section title

**Files:**
- Modify: `src/main/java/plugin/swisskit/offlinepython/ui/panel/InitPanel.java`

- [ ] **Step 1: Replace the file**

```java
package plugin.swisskit.offlinepython.ui.panel;

import fan.summer.api.component.GlassNotification;
import fan.summer.api.component.UiUtils;
import fan.summer.api.i18n.I18n;
import javafx.stage.DirectoryChooser;
import plugin.swisskit.offlinepython.command.InitService;
import plugin.swisskit.offlinepython.ui.LogConsole;
import java.io.File;

public class InitPanel extends CommandPanel {
    public InitPanel(LogConsole log) {
        super(log);
        getChildren().add(titleNode());
        var init = UiUtils.glassBtn("Initialize Project…", true);
        init.setOnAction(e -> {
            DirectoryChooser dc = new DirectoryChooser();
            File dir = dc.showDialog(getScene().getWindow());
            if (dir == null) return;
            try {
                new InitService().initialize(dir.toPath());
                log.log("Initialized project at " + dir);
                GlassNotification.toast(this, GlassNotification.Type.SUCCESS, "Project initialized");
            } catch (Exception ex) {
                log.log("ERROR init: " + ex.getMessage());
                GlassNotification.toast(this, GlassNotification.Type.ERROR, "Init failed");
            }
        });
        getChildren().add(init);
    }
    @Override public String title() { return I18n.get("opb.init.title"); }
}
```

- [ ] **Step 2: Build to verify it compiles**

Run: `mvn -pl SwissKitJ-Plugin-OfflinePython compile -B`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add SwissKitJ-Plugin-OfflinePython/src/main/java/plugin/swisskit/offlinepython/ui/panel/InitPanel.java
git commit -m "feat(OfflinePython): glass InitPanel (primary button + section title)"
```

---

## Task 6: DepsPanel — glass list, buttons, fields

**Files:**
- Modify: `src/main/java/plugin/swisskit/offlinepython/ui/panel/DepsPanel.java`

- [ ] **Step 1: Replace the file**

```java
package plugin.swisskit.offlinepython.ui.panel;

import fan.summer.api.component.GlassNotification;
import fan.summer.api.component.UiUtils;
import fan.summer.api.i18n.I18n;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.FileChooser;
import plugin.swisskit.offlinepython.domain.DependencySpec;
import plugin.swisskit.offlinepython.domain.RequirementsFile;
import plugin.swisskit.offlinepython.ui.LogConsole;
import plugin.swisskit.offlinepython.ui.OpbStyle;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

public class DepsPanel extends CommandPanel {
    private final ListView<DependencySpec> list = new ListView<>();
    private Path requirementsFile;

    public DepsPanel(LogConsole log) {
        super(log);
        list.setStyle(OpbStyle.card());
        list.setMinHeight(120);

        getChildren().add(titleNode());

        Button open = UiUtils.glassBtn("Open requirements.txt", false);
        open.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            File f = fc.showOpenDialog(getScene().getWindow());
            if (f == null) return;
            requirementsFile = f.toPath();
            load();
        });

        final TextField pkgField = new TextField();
        pkgField.setStyle(UiUtils.fieldStyle());
        pkgField.setPromptText("numpy");
        final TextField verField = new TextField();
        verField.setStyle(UiUtils.fieldStyle());
        verField.setPromptText("==1.26.4");
        Button add = UiUtils.glassBtn("Add", false);
        add.setOnAction(e -> {
            String n = pkgField.getText().trim();
            if (n.isEmpty()) return;
            String v = verField.getText().trim();
            list.getItems().add(new DependencySpec(n, v, null));
            pkgField.clear();
            verField.clear();
        });
        HBox addRow = new HBox(8,
                fieldGroup("Package", pkgField),
                fieldGroup("Version", verField),
                add);

        Button save = UiUtils.glassBtn("Save", true);
        save.setOnAction(e -> save());

        getChildren().addAll(open, list, addRow, save);
    }

    private HBox fieldGroup(String text, TextField field) {
        HBox h = new HBox(6, UiUtils.subLabel(text), field);
        HBox.setHgrow(field, Priority.ALWAYS);
        return h;
    }

    private void load() {
        try {
            list.getItems().setAll(RequirementsFile.parse(Files.readString(requirementsFile)));
            log.log("Loaded " + list.getItems().size() + " dependencies");
        } catch (Exception ex) {
            log.log("ERROR load: " + ex.getMessage());
        }
    }

    private void save() {
        if (requirementsFile == null) {
            GlassNotification.toast(this, GlassNotification.Type.WARNING, "Open a requirements.txt first");
            return;
        }
        try {
            Files.writeString(requirementsFile, RequirementsFile.write(new ArrayList<>(list.getItems())));
            log.log("Saved " + list.getItems().size() + " dependencies");
            GlassNotification.toast(this, GlassNotification.Type.SUCCESS, "Saved");
        } catch (Exception ex) {
            log.log("ERROR save: " + ex.getMessage());
        }
    }

    @Override public String title() { return I18n.get("opb.deps.title"); }
}
```

- [ ] **Step 2: Build to verify it compiles**

Run: `mvn -pl SwissKitJ-Plugin-OfflinePython compile -B`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add SwissKitJ-Plugin-OfflinePython/src/main/java/plugin/swisskit/offlinepython/ui/panel/DepsPanel.java
git commit -m "feat(OfflinePython): glass DepsPanel (list/buttons/fields)"
```

---

## Task 7: BuildPanel — glass buttons + status-colored progress

**Files:**
- Modify: `src/main/java/plugin/swisskit/offlinepython/ui/panel/BuildPanel.java`

- [ ] **Step 1: Replace the file**

```java
package plugin.swisskit.offlinepython.ui.panel;

import fan.summer.api.component.GlassNotification;
import fan.summer.api.component.UiUtils;
import fan.summer.api.i18n.I18n;
import javafx.scene.control.Button;
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
    private final Button build;
    private PluginTask<Integer> task;
    private ProcessRunner runner;

    public BuildPanel(LogConsole log) {
        super(log);
        getChildren().add(titleNode());
        build = UiUtils.glassBtn("Build", true);
        Button cancel = UiUtils.glassBtn("Cancel", false);
        progress.setProgress(-1);
        build.setOnAction(e -> start());
        cancel.setOnAction(e -> { if (runner != null) runner.cancel(); });
        getChildren().addAll(new HBox(8, build, cancel), progress);
    }

    private void start() {
        if (isRunning()) return;
        build.setDisable(true);
        DirectoryChooser dc = new DirectoryChooser();
        File dir = dc.showDialog(getScene().getWindow());
        if (dir == null) { build.setDisable(false); return; }
        runner = new ProcessRunner();
        task = new PluginTask<>() {
            @Override protected Integer call() throws Exception {
                BuildConfig cfg = JsonStore.load(dir.toPath().resolve("config.json"), BuildConfig.class);
                var det = plugin.swisskit.offlinepython.infra.PythonDetector.detect(cfg.getPython().getExecutable());
                if (!det.ok()) throw new IllegalStateException("Python not detected — install Python first");
                return new BuildService().build(dir.toPath(), cfg, det.executable(), log::log, runner);
            }
        };
        task.setOnSucceeded(e -> {
            int code = task.getValue();
            log.log(code == 0 ? "Build OK" : "Build failed (exit " + code + ")");
            GlassNotification.toast(this, code == 0 ? GlassNotification.Type.SUCCESS : GlassNotification.Type.ERROR,
                    code == 0 ? "Build complete" : "Build failed");
            progress.setProgress(code == 0 ? 1 : 0);
            progress.getStyleClass().removeAll("success", "danger");
            progress.getStyleClass().add(code == 0 ? "success" : "danger");
            build.setDisable(false);
        });
        task.setOnFailed(e -> {
            log.log("ERROR: " + task.getException().getMessage());
            GlassNotification.toast(this, GlassNotification.Type.ERROR, "Build failed");
            progress.setProgress(0);
            progress.getStyleClass().removeAll("success", "danger");
            progress.getStyleClass().add("danger");
            build.setDisable(false);
        });
        Thread t = new Thread(task, "OfflinePython-Build");
        t.setDaemon(true);
        t.start();
    }

    /** Cancel any running build (called on plugin unload). */
    public void cancel() {
        if (runner != null) runner.cancel();
        if (task != null) task.cancel(false);
    }

    public boolean isRunning() { return task != null && task.isRunningTask(); }

    @Override public String title() { return I18n.get("opb.build.title"); }
}
```

- [ ] **Step 2: Build to verify it compiles**

Run: `mvn -pl SwissKitJ-Plugin-OfflinePython compile -B`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add SwissKitJ-Plugin-OfflinePython/src/main/java/plugin/swisskit/offlinepython/ui/panel/BuildPanel.java
git commit -m "feat(OfflinePython): glass BuildPanel + success/danger progress states"
```

---

## Task 8: VerifyPanel — glass button + status-colored result badges

**Files:**
- Modify: `src/main/java/plugin/swisskit/offlinepython/ui/panel/VerifyPanel.java`

- [ ] **Step 1: Replace the file**

```java
package plugin.swisskit.offlinepython.ui.panel;

import fan.summer.api.component.GlassNotification;
import fan.summer.api.component.UiUtils;
import fan.summer.api.i18n.I18n;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import plugin.swisskit.offlinepython.command.VerifyService;
import plugin.swisskit.offlinepython.domain.CheckResult;
import plugin.swisskit.offlinepython.domain.Manifest;
import plugin.swisskit.offlinepython.domain.VerifyResult;
import plugin.swisskit.offlinepython.infra.JsonStore;
import plugin.swisskit.offlinepython.ui.LogConsole;
import plugin.swisskit.offlinepython.ui.OpbStyle;

import java.io.File;

public class VerifyPanel extends CommandPanel {
    private final VBox report = new VBox(6);

    public VerifyPanel(LogConsole log) {
        super(log);
        getChildren().add(titleNode());
        Button verify = UiUtils.glassBtn("Verify", true);
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
            Label badge = new Label("[" + c.status() + "]");
            badge.setStyle("-fx-text-fill: " + OpbStyle.statusColor(c.status()) + "; -fx-font-weight: bold;");
            report.getChildren().add(new HBox(8, badge, UiUtils.subLabel(c.detail())));
        }
    }

    @Override public String title() { return I18n.get("opb.verify.title"); }
}
```

- [ ] **Step 2: Build to verify it compiles**

Run: `mvn -pl SwissKitJ-Plugin-OfflinePython compile -B`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add SwissKitJ-Plugin-OfflinePython/src/main/java/plugin/swisskit/offlinepython/ui/panel/VerifyPanel.java
git commit -m "feat(OfflinePython): glass VerifyPanel + status-colored result badges"
```

---

## Task 9: DoctorPanel — glass button + status-colored rows

**Files:**
- Modify: `src/main/java/plugin/swisskit/offlinepython/ui/panel/DoctorPanel.java`

- [ ] **Step 1: Replace the file**

```java
package plugin.swisskit.offlinepython.ui.panel;

import fan.summer.api.component.UiUtils;
import fan.summer.api.i18n.I18n;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import plugin.swisskit.offlinepython.command.DoctorService;
import plugin.swisskit.offlinepython.ui.LogConsole;
import plugin.swisskit.offlinepython.ui.OpbStyle;

public class DoctorPanel extends CommandPanel {
    private final GridPane grid = new GridPane();

    public DoctorPanel(LogConsole log) {
        super(log);
        getChildren().add(titleNode());
        Button run = UiUtils.glassBtn("Run diagnostics", true);
        run.setOnAction(e -> {
            grid.getChildren().clear();
            int row = 0;
            for (var c : new DoctorService().run(null)) {
                Label key = UiUtils.subLabel(c.name());
                Label val = new Label((c.ok() ? "✓ " : "✕ ") + c.value());
                val.setStyle("-fx-text-fill: " + (c.ok() ? OpbStyle.SUCCESS : OpbStyle.DANGER) + ";");
                grid.add(key, 0, row);
                grid.add(val, 1, row);
                row++;
            }
            log.log("Diagnostics complete");
        });
        grid.setHgap(16);
        grid.setVgap(6);
        getChildren().addAll(run, grid);
    }

    @Override public String title() { return I18n.get("opb.doctor.title"); }
}
```

- [ ] **Step 2: Build to verify it compiles**

Run: `mvn -pl SwissKitJ-Plugin-OfflinePython compile -B`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add SwissKitJ-Plugin-OfflinePython/src/main/java/plugin/swisskit/offlinepython/ui/panel/DoctorPanel.java
git commit -m "feat(OfflinePython): glass DoctorPanel + status-colored rows"
```

---

## Task 10: PythonInstallGuide — glass card + glass buttons/fields

**Files:**
- Modify: `src/main/java/plugin/swisskit/offlinepython/ui/PythonInstallGuide.java`

- [ ] **Step 1: Replace the file**

```java
package plugin.swisskit.offlinepython.ui;

import fan.summer.api.component.GlassNotification;
import fan.summer.api.component.UiUtils;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public class PythonInstallGuide extends VBox {
    public PythonInstallGuide(Runnable onRedetect) {
        setSpacing(10);
        setStyle(OpbStyle.card() + " -fx-padding: 18;");

        Label warn = new Label("⚠ Python not detected");
        warn.setStyle("-fx-text-fill: " + OpbStyle.WARNING + "; -fx-font-size: 15px; -fx-font-weight: 500;");
        getChildren().add(warn);
        getChildren().add(UiUtils.subLabel("This plugin needs Python ≥ 3.10 + pip. Install it, then retry."));
        getChildren().add(cmdRow("macOS", "brew install python", this));
        getChildren().add(cmdRow("Linux", "sudo apt install python3 python3-pip", this));
        Button retry = UiUtils.glassBtn("Re-detect", true);
        retry.setOnAction(e -> onRedetect.run());
        getChildren().add(retry);
    }

    private HBox cmdRow(String os, String cmd, PythonInstallGuide self) {
        Label l = UiUtils.subLabel(os);
        TextField field = new TextField(cmd);
        field.setEditable(false);
        field.setStyle(UiUtils.fieldStyle());
        field.setPrefWidth(280);
        Button copy = UiUtils.glassBtn("Copy", false);
        copy.setOnAction(e -> {
            ClipboardContent c = new ClipboardContent();
            c.putString(cmd);
            Clipboard.getSystemClipboard().setContent(c);
            GlassNotification.toast(self, GlassNotification.Type.SUCCESS, "Copied: " + cmd);
        });
        HBox row = new HBox(8, l, field, copy);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }
}
```

- [ ] **Step 2: Build to verify it compiles**

Run: `mvn -pl SwissKitJ-Plugin-OfflinePython compile -B`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add SwissKitJ-Plugin-OfflinePython/src/main/java/plugin/swisskit/offlinepython/ui/PythonInstallGuide.java
git commit -m "feat(OfflinePython): glass PythonInstallGuide card + buttons/fields"
```

---

## Task 11: Full regression + DevLauncher visual verification

**Files:** none (verification only).

- [ ] **Step 1: Run the full unit suite**

Run: `mvn -pl SwissKitJ-Plugin-OfflinePython test -B`
Expected: PASS — all existing tests plus the new `OpbStyleTest` (6). The UI changes must not affect the 8 existing pure-logic test classes.

- [ ] **Step 2: Resolve the known DevLauncher classpath issue (prerequisite)**

Before launching, in IntelliJ: **Maven tool window → Reload All Maven Projects** (so `SwissKitJ-Api:3.1.0` resolves onto the module classpath), then regenerate the `DevLauncher` run config by right-clicking `DevLauncher.main()` → **Run**. The launched `-classpath` must include `…/SwissKitJ-Api/3.1.0/SwissKitJ-Api-3.1.0.jar` (otherwise `NoClassDefFoundError: fan/summer/api/SwissKitJPlugin`).

- [ ] **Step 3: Launch DevLauncher and verify the glass look**

Launch `DevLauncher`. Manually verify:
- Left nav is glass-styled: 220px, i18n labels, selected item has accent-soft background + accent text; hover shows glass-hover background.
- Top-right Python badge is a colored capsule (green with "Python x.y · pip z" when detected; red "Python not detected" otherwise).
- Each panel renders as a glass card with a section-header title and glass buttons (primary blue, secondary translucent) and glass text fields where used.
- **Build** panel: progress bar renders as the host 6px accent bar; on success shows `.success` (green), on failure `.danger` (red).
- **Verify** panel: result rows show `[PASS]`/`[WARN]`/`[FAIL]` colored badges + detail.
- **Doctor** panel: rows show ✓ (green) / ✕ (red).
- **Log console**: dark glass inner background, readable text, thin scrollbars.
- If Python is not on PATH: the install-guide card shows (warning title, copy buttons toast).

- [ ] **Step 4: Final commit (if any polish)**

```bash
git add -A
git commit -m "chore(OfflinePython): glass UI verified via DevLauncher" || echo "nothing to commit"
```

---

## Self-Review (completed)

**1. Spec coverage** (vs `2026-06-28-offlinepython-ui-glass-design.md`):
- §5 Theming strategy (attach stylesheet to root + UiUtils + OpbStyle concrete values) → Tasks 1 (OpbStyle), 4 (root stylesheet), 2/5-10 (UiUtils). ✓
- §7.1 CommandShell (glass nav + badge + transparent root) → Task 4. ✓
- §7.2 LogConsole glass textarea + `.content-scroll` → Task 3. ✓
- §7.3 CommandPanel card + titleNode → Task 2. ✓
- §7.4 five panels (glassBtn, fieldStyle, sectionTitle, status colors, progress states) → Tasks 5-9. ✓
- §7.5 PythonInstallGuide glass card → Task 10. ✓
- §7.6 OpbStyle tokens + builders → Task 1. ✓
- §9 Behavior preserved (lifecycle, selection, log API, toasts, i18n) → all tasks keep logic intact; titles now wired to existing i18n keys (English output unchanged; zh now localized — an intended consistency gain, keys unchanged). ✓
- §10 Verification (compile + unit regression + DevLauncher) → Task 11. ✓

**2. Placeholder scan:** No TBD/TODO/"add appropriate". DepsPanel's optional `Region.USE_PREF_SIZE` line was removed (no `Region` import would have resolved). Every code step contains full file content. ✓

**3. Type/signature consistency:**
- `OpbStyle.card()/navItem(boolean,boolean)/badge(boolean)/statusColor(Status)/logTextAreaStyle()` — used identically in Tasks 1, 3, 4, 6, 8, 9, 10. ✓
- `UiUtils.glassBtn(String,boolean)/sectionTitle(String)/subLabel(String)/fieldStyle()` — signatures match the host API and are used consistently. ✓
- `CommandPanel.titleNode()` introduced in Task 2, consumed in Tasks 5-9. ✓
- `BuildPanel.cancel()`/`isRunning()` preserved; `CommandShell.hasRunningTasks()`/`onUnload()` unchanged. ✓
- `I18n.get(key)` / `I18n.get(key, args…)` used for existing keys only. ✓

---

## Out of scope (per spec §3, §12)

- Migrating HappyLearning / Qcc / KeepAwake to the host theme.
- Switching layout to tabs or `StepWizard`.
- New features, new i18n keys, or cancel-on-unload wiring (current `onUnload` left as-is).
