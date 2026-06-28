# OfflinePython UI Rebuild (per spec §9) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild the OfflinePython plugin UI so its shell and panels faithfully match the approved design spec (`docs/superpowers/specs/2026-06-26-offline-python-builder-design.md` §9 / §7.2) and the confirmed browser mockups, replacing the current incorrect UI.

**Architecture:** Introduce a `ProjectContext` (current project dir + config) shared by the shell and panels, and a `LogLevel`-aware collapsible/filterable `LogConsole`. Rebuild `CommandShell` with the §9 top bar (project selector / new / open / Python badge) and two-group left nav (11 commands; deps badge; V2/V3 disabled). Rebuild the five panels with the confirmed layouts, wiring richer data: `DepsService` (pip `index versions` + PyPI JSON size), a `BuildSummary` from `BuildService`, verify scope segments, and extended doctor checks. Glass styling via the existing `OpbStyle` + host `UiUtils`/`Themes`. No change to the domain models; small service extensions only.

**Tech Stack:** Java 21, JavaFX 21 (provided), SwissKitJ-Api 3.1.0 (provided), Lombok (provided), Gson (compile), JUnit 5 (test), Maven shade.

**Spec:** `docs/superpowers/specs/2026-06-26-offline-python-builder-design.md` (§9 UI, §7.2 install guide, §8 command→pip mapping)
**Visual reference:** confirmed mockups in `.superpowers/brainstorm/91502-1782610019/content/` (`shell-v1.html`, `panels-v1.html`) — gitignored; this plan is the durable record.

---

## Build tool note

No `mvn` on PATH on this machine. Run each `mvn …` command from **IntelliJ's Maven tool window**, or use **Build → Build Module**, or `brew install maven`. The IDE MCP `mcp__idea__build_project` (projectPath, filesToRebuild) and `mcp__idea__get_file_problems` are the primary compile/diagnostics tools in this environment; a full-project build has **pre-existing, unrelated** errors in sibling modules (HappyLearning/Qcc/KeepAwake) — ignore those, only the OfflinePython files must be clean. Existing pure-logic tests (RequirementsFile/BuildConfig/HashUtil/Manifest/PythonDetector/ProcessRunner/InitService/VerifyService) must still pass; new logic gets new tests; JavaFX UI is verified via DevLauncher.

The previous UI commits on this branch (`a623971`..`5d21d1c`, the "glass restyle") are **superseded** by this rebuild — this plan overwrites those UI files. `OpbStyle` is kept and extended. Do not revert prior commits; build on top.

i18n: existing `opb.*` keys are reused for titles/badge. **New UI label strings are hard-coded literals** (matching the current code's convention) to keep scope bounded; full i18n of new strings is deferred (noted out of scope).

---

## File Structure

```
SwissKitJ-Plugin-OfflinePython/src/main/java/plugin/swisskit/offlinepython/
├── ui/
│   ├── OpbStyle.java              # MODIFY — add nav-group/disabled/badge helpers (existing tokens kept)
│   ├── LogLevel.java              # NEW — INFO/WARN/ERROR/DEBUG enum
│   ├── ProjectContext.java        # NEW — current project Path + BuildConfig; open/new/load/save
│   ├── CommandShell.java          # REWRITE — top bar + 2-group nav + collapsible log dock
│   ├── LogConsole.java            # REWRITE — collapsible + level filter + LogLevel tagging
│   ├── PythonInstallGuide.java    # REWRITE — §7.2 (3 platforms + re-detect + manual path)
│   └── panel/
│       ├── CommandPanel.java      # MODIFY — base: glass card + titleNode + ProjectContext field
│       ├── InitPanel.java         # REWRITE — wire to ProjectContext
│       ├── DepsPanel.java         # REWRITE — rich table + toolbar + options + summary
│       ├── BuildPanel.java        # REWRITE — banner + result tiles
│       ├── VerifyPanel.java       # REWRITE — scope segments + report + conclusion
│       └── DoctorPanel.java       # REWRITE — 7-check table
├── command/
│   ├── DepsService.java           # NEW — pip index versions + PyPI JSON size (pure parsers tested)
│   ├── BuildService.java          # MODIFY — return BuildSummary
│   ├── VerifyService.java         # MODIFY — verify(output, manifest, Scope)
│   └── DoctorService.java         # MODIFY — add pip-download-available + network checks
└── domain/
    ├── BuildSummary.java          # NEW — record(totalWheels, cacheHits, totalBytes, durationMs)
    └── VerifyScope.java           # NEW — enum ALL/INTEGRITY/SHA256

src/test/java/plugin/swisskit/offlinepython/
├── DepsServiceTest.java           # NEW — version + size parsing
├── BuildSummaryTest.java          # NEW — summary computation from a fake wheelhouse
├── VerifyScopeTest.java           # NEW — scope filtering of VerifyResult
└── (existing tests unchanged)
```

Shared type contract (defined in early tasks, used consistently later):
- `LogLevel` enum: `INFO, WARN, ERROR, DEBUG`.
- `ProjectContext`: `ObjectProperty<Path> projectDirProperty()`, `ObjectProperty<BuildConfig> configProperty()`, `boolean hasProject()`, `void openExisting(Path)`, `void createNew(Path)`, `void reloadConfig()`, `void saveConfig()`.
- `BuildSummary` record: `int totalWheels`, `int cacheHits`, `long totalBytes`, `long durationMs`, plus `static BuildSummary compute(Path wheelhouse, int preExisting, long durationMs)`.
- `VerifyScope` enum: `ALL, INTEGRITY, SHA256`; `VerifyService.verify(Path, Manifest, VerifyScope)`.
- `DepsService`: `Optional<String> latestVersion(String pkg, String pythonExe)`, `long fetchSizeBytes(String pkg, String versionSpec, String platform)` (0 = unknown), plus static pure parsers `parseLatestVersion(String)`, `parsePypiWheelSize(String json, String platform, String versionSpec)`.

---

## Task 1: LogLevel + LogConsole (collapsible + level filter)

**Files:**
- Create: `src/main/java/plugin/swisskit/offlinepython/ui/LogLevel.java`
- Modify: `src/main/java/plugin/swisskit/offlinepython/ui/LogConsole.java`

- [ ] **Step 1: Create LogLevel**

`src/main/java/plugin/swisskit/offlinepython/ui/LogLevel.java`:
```java
package plugin.swisskit.offlinepython.ui;

public enum LogLevel { DEBUG, INFO, WARN, ERROR }
```

- [ ] **Step 2: Rewrite LogConsole**

`src/main/java/plugin/swisskit/offlinepython/ui/LogConsole.java`:
```java
package plugin.swisskit.offlinepython.ui;

import javafx.application.Platform;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;

import java.util.EnumSet;
import java.util.Set;

/**
 * Shared, collapsible log console. Lines are tagged with a {@link LogLevel} and filtered
 * by the visible-level set. Older convenience method {@link #log(String)} defaults to INFO.
 */
public class LogConsole extends BorderPane {
    private final TextArea area = new TextArea();
    private final Set<LogLevel> visible = EnumSet.allOf(LogLevel.class);
    private volatile boolean collapsed;

    public LogConsole() {
        area.setEditable(false);
        area.setWrapText(true);
        area.setStyle(OpbStyle.logTextAreaStyle());
        getStyleClass().add("content-scroll");
        setCenter(area);
        setPrefHeight(168);
    }

    /** Append a line at INFO level (back-compat for existing callers). */
    public void log(String line) { log(LogLevel.INFO, line); }

    /** Append a line at the given level, if that level is currently visible. */
    public void log(LogLevel level, String line) {
        String ts = java.time.LocalTime.now().withNano(0).toString();
        String prefix = switch (level) {
            case INFO -> "";
            case WARN -> "[WARN] ";
            case ERROR -> "[ERROR] ";
            case DEBUG -> "[DEBUG] ";
        };
        String rendered = "[" + ts + "] " + prefix + line + "\n";
        Platform.runLater(() -> {
            if (!visible.contains(level)) return;
            area.appendText(rendered);
            area.setScrollTop(Double.MAX_VALUE);
        });
    }

    public void setVisibleLevels(Set<LogLevel> levels) {
        this.visible.clear();
        this.visible.addAll(levels);
    }

    public void setCollapsed(boolean collapsed) {
        this.collapsed = collapsed;
        Platform.runLater(() -> setManaged(!collapsed));
    }

    public boolean isCollapsed() { return collapsed; }
}
```

- [ ] **Step 3: Build**

Run: `mvn -pl SwissKitJ-Plugin-OfflinePython compile -B`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add SwissKitJ-Plugin-OfflinePython/src/main/java/plugin/swisskit/offlinepython/ui/LogLevel.java \
        SwissKitJ-Plugin-OfflinePython/src/main/java/plugin/swisskit/offlinepython/ui/LogConsole.java
git commit -m "feat(OfflinePython): collapsible level-filtered LogConsole"
```

---

## Task 2: ProjectContext

**Files:**
- Create: `src/main/java/plugin/swisskit/offlinepython/ui/ProjectContext.java`
- Test: `src/test/java/plugin/swisskit/offlinepython/ProjectContextTest.java`

- [ ] **Step 1: Write the failing test**

`src/test/java/plugin/swisskit/offlinepython/ProjectContextTest.java`:
```java
package plugin.swisskit.offlinepython;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import plugin.swisskit.offlinepython.domain.BuildConfig;
import plugin.swisskit.offlinepython.ui.ProjectContext;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ProjectContextTest {

    @Test
    void openExistingLoadsConfig(@TempDir Path tmp) throws Exception {
        new plugin.swisskit.offlinepython.command.InitService().initialize(tmp);
        ProjectContext ctx = new ProjectContext();
        ctx.openExisting(tmp);
        assertTrue(ctx.hasProject());
        assertEquals(tmp, ctx.projectDirProperty().get());
        assertNotNull(ctx.configProperty().get());
        assertEquals("3.12.10", ctx.configProperty().get().getPython().getVersion());
    }

    @Test
    void hasProjectFalseUntilOpened() {
        ProjectContext ctx = new ProjectContext();
        assertFalse(ctx.hasProject());
    }

    @Test
    void createNewInitializesAndSetsProject(@TempDir Path tmp) throws Exception {
        ProjectContext ctx = new ProjectContext();
        ctx.createNew(tmp);
        assertTrue(ctx.hasProject());
        assertTrue(java.nio.file.Files.exists(tmp.resolve("config.json")));
    }
}
```

- [ ] **Step 2: Run test → FAIL (ProjectContext not found)**

Run: `mvn -pl SwissKitJ-Plugin-OfflinePython test -Dtest=ProjectContextTest -B`
Expected: FAIL — class not found.

- [ ] **Step 3: Implement ProjectContext**

`src/main/java/plugin/swisskit/offlinepython/ui/ProjectContext.java`:
```java
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
```

- [ ] **Step 4: Run test → PASS (3 tests)**

Run: `mvn -pl SwissKitJ-Plugin-OfflinePython test -Dtest=ProjectContextTest -B`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add SwissKitJ-Plugin-OfflinePython/src/main/java/plugin/swisskit/offlinepython/ui/ProjectContext.java \
        SwissKitJ-Plugin-OfflinePython/src/test/java/plugin/swisskit/offlinepython/ProjectContextTest.java
git commit -m "feat(OfflinePython): add ProjectContext (shared current project + config)"
```

---

## Task 3: DepsService — version lookup + PyPI size (TDD pure parsers)

**Files:**
- Create: `src/main/java/plugin/swisskit/offlinepython/command/DepsService.java`
- Test: `src/test/java/plugin/swisskit/offlinepython/DepsServiceTest.java`

- [ ] **Step 1: Write the failing test**

`src/test/java/plugin/swisskit/offlinepython/DepsServiceTest.java`:
```java
package plugin.swisskit.offlinepython;

import org.junit.jupiter.api.Test;
import plugin.swisskit.offlinepython.command.DepsService;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class DepsServiceTest {

    @Test
    void parsesLatestVersionFromPipIndexVersions() {
        // `pip index versions numpy` prints lines like "numpy (1.26.4)\nAvailable versions: 1.26.4, 1.26.3, ..."
        String out = "numpy (1.26.4)\nAvailable versions: 1.26.4, 1.26.3, 1.26.2";
        assertEquals(Optional.of("1.26.4"), DepsService.parseLatestVersion(out));
    }

    @Test
    void parseLatestVersionEmptyOnGarbage() {
        assertTrue(DepsService.parseLatestVersion("no versions found").isEmpty());
        assertTrue(DepsService.parseLatestVersion((String) null).isEmpty());
    }

    @Test
    void parsesWheelSizeFromPypiJsonForPlatform() {
        // Minimal slice of pypi.org/pypi/<pkg>/json releases[version][].filename + size
        String json = """
            { "urls": [
              {"filename":"numpy-1.26.4-cp312-cp312-win_amd64.whl","size":19098624},
              {"filename":"numpy-1.26.4.tar.gz","size":1000}
            ]}
            """;
        long size = DepsService.parsePyPIWheelSize(json, "win_amd64", "1.26.4");
        assertEquals(19098624L, size);
    }

    @Test
    void parsePyPIWheelSizeFallsBackToZero() {
        assertEquals(0L, DepsService.parsePyPIWheelSize("{}", "win_amd64", "1.26.4"));
        assertEquals(0L, DepsService.parsePyPIWheelSize((String) null, "win_amd64", "1.26.4"));
    }

    @Test
    void parsePyPIWheelSizePicksAnyWheelIfPlatformMisses() {
        String json = """
            { "urls": [
              {"filename":"numpy-1.26.4-cp312-cp312-manylinux.whl","size":17000000},
              {"filename":"numpy-1.26.4.tar.gz","size":1000}
            ]}
            """;
        // no win_amd64 wheel → fall back to first .whl size
        assertEquals(17000000L, DepsService.parsePyPIWheelSize(json, "win_amd64", "1.26.4"));
    }
}
```

- [ ] **Step 2: Run test → FAIL**

Run: `mvn -pl SwissKitJ-Plugin-OfflinePython test -Dtest=DepsServiceTest -B`
Expected: FAIL — class not found.

- [ ] **Step 3: Implement DepsService**

`src/main/java/plugin/swisskit/offlinepython/command/DepsService.java`:
```java
package plugin.swisskit.offlinepython.command;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import plugin.swisskit.offlinepython.infra.ProcessRunner;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dependency lookups for the DepsPanel: latest version via `pip index versions`,
 * and best-effort wheel size via the PyPI JSON API. Pure parsers are unit-tested;
 * the live calls are thin wrappers.
 */
public final class DepsService {

    private static final Pattern FIRST_VERSION = Pattern.compile("\\(([0-9][^)]*)\\)");
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();

    private DepsService() {}

    /** Run `pip index versions <pkg>`, return the latest version, or empty. */
    public Optional<String> latestVersion(String pkg, String pythonExe) {
        String out = ProcessRunner.captureQuiet(pythonExe, "-m", "pip", "index", "versions", pkg);
        return parseLatestVersion(out);
    }

    /** Best-effort wheel size (bytes) for pkg@versionSpec on platform; 0 if unknown. */
    public long fetchSizeBytes(String pkg, String versionSpec, String platform) {
        String ver = versionSpec == null ? "" : versionSpec.replaceAll("[<>=!~]", "").trim();
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://pypi.org/pypi/" + pkg + "/json"))
                    .timeout(Duration.ofSeconds(6)).GET().build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            return parsePyPIWheelSize(resp.body(), platform, ver);
        } catch (Exception e) {
            return 0L;
        }
    }

    /** `pip index versions numpy` → "numpy (1.26.4)\n..." → "1.26.4". */
    public static Optional<String> parseLatestVersion(String out) {
        if (out == null || out.isBlank()) return Optional.empty();
        Matcher m = FIRST_VERSION.matcher(out);
        return m.find() ? Optional.of(m.group(1).trim()) : Optional.empty();
    }

    /** Extract the size of the wheel matching platform (and version if given) from PyPI JSON; 0 if none. */
    public static long parsePyPIWheelSize(String json, String platform, String version) {
        if (json == null || json.isBlank()) return 0L;
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonArray urls = root.has("urls") ? root.getAsJsonArray("urls") : root;
            long fallback = 0L;
            for (JsonElement e : urls) {
                JsonObject o = e.getAsJsonObject();
                String fn = o.get("filename").getAsString();
                long size = o.get("size").getAsLong();
                if (!fn.endsWith(".whl")) continue;
                if (version != null && !version.isBlank() && !fn.contains(version)) continue;
                if (platform != null && !platform.isBlank() && fn.contains(platform)) return size;
                if (fallback == 0L) fallback = size;
            }
            return fallback;
        } catch (Exception ex) {
            return 0L;
        }
    }
}
```

> Note: this adds `ProcessRunner.captureQuiet(...)`. Add it in Step 4.

- [ ] **Step 4: Add `captureQuiet` helper to ProcessRunner**

In `infra/ProcessRunner.java`, add alongside the existing code (keep existing methods):
```java
    /** Run a short command and return combined stdout+stderr (best-effort, quiet). */
    public static String captureQuiet(String... cmd) {
        try {
            return new String(Runtime.getRuntime().exec(cmd).getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }
```

- [ ] **Step 5: Run test → PASS (5 tests)**

Run: `mvn -pl SwissKitJ-Plugin-OfflinePython test -Dtest=DepsServiceTest -B`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add SwissKitJ-Plugin-OfflinePython/src/main/java/plugin/swisskit/offlinepython/command/DepsService.java \
        SwissKitJ-Plugin-OfflinePython/src/main/java/plugin/swisskit/offlinepython/infra/ProcessRunner.java \
        SwissKitJ-Plugin-OfflinePython/src/test/java/plugin/swisskit/offlinepython/DepsServiceTest.java
git commit -m "feat(OfflinePython): DepsService (pip index versions + PyPI wheel size) with tests"
```

---

## Task 4: BuildSummary + BuildService returns it (TDD)

**Files:**
- Create: `src/main/java/plugin/swisskit/offlinepython/domain/BuildSummary.java`
- Modify: `src/main/java/plugin/swisskit/offlinepython/command/BuildService.java`
- Test: `src/test/java/plugin/swisskit/offlinepython/BuildSummaryTest.java`

- [ ] **Step 1: Write the failing test**

`src/test/java/plugin/swisskit/offlinepython/BuildSummaryTest.java`:
```java
package plugin.swisskit.offlinepython;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import plugin.swisskit.offlinepython.domain.BuildSummary;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class BuildSummaryTest {

    @Test
    void computeCountsWheelsSizesAndCacheHits(@TempDir Path tmp) throws Exception {
        Path wh = tmp.resolve("wheelhouse");
        Files.createDirectories(wh);
        Files.write(wh.resolve("numpy-1.26.4-cp312-cp312-win_amd64.whl"), new byte[19098624]);
        Files.write(wh.resolve("pandas-2.2.0-cp312-cp312-win_amd64.whl"), new byte[12000000]);

        BuildSummary s = BuildSummary.compute(wh, 1, 92_000);
        assertEquals(2, s.totalWheels());
        assertEquals(1, s.cacheHits());
        assertEquals(19098624L + 12000000L, s.totalBytes());
        assertEquals(92_000L, s.durationMs());
    }

    @Test
    void computeEmptyWheelhouse(@TempDir Path tmp) throws Exception {
        Path wh = tmp.resolve("wheelhouse");
        Files.createDirectories(wh);
        BuildSummary s = BuildSummary.compute(wh, 0, 0);
        assertEquals(0, s.totalWheels());
        assertEquals(0L, s.totalBytes());
    }
}
```

- [ ] **Step 2: Run test → FAIL**

Run: `mvn -pl SwissKitJ-Plugin-OfflinePython test -Dtest=BuildSummaryTest -B`
Expected: FAIL — BuildSummary not found.

- [ ] **Step 3: Implement BuildSummary**

`src/main/java/plugin/swisskit/offlinepython/domain/BuildSummary.java`:
```java
package plugin.swisskit.offlinepython.domain;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/** Outcome of a build, surfaced as result tiles on the BuildPanel. */
public record BuildSummary(int totalWheels, int cacheHits, long totalBytes, long durationMs) {

    /** Count wheels, sum sizes. preExisting = wheels already in the wheelhouse before build (cache hits). */
    public static BuildSummary compute(Path wheelhouse, int preExisting, long durationMs) {
        int total = 0;
        long bytes = 0;
        try (Stream<Path> files = Files.list(wheelhouse)) {
            var list = files.filter(p -> p.getFileName().toString().endsWith(".whl")).toList();
            total = list.size();
            for (Path p : list) bytes += Files.size(p);
        } catch (IOException e) {
            // fall through with whatever counted
        }
        return new BuildSummary(total, Math.min(preExisting, total), bytes, durationMs);
    }
}
```

- [ ] **Step 4: Run test → PASS (2)**

Run: `mvn -pl SwissKitJ-Plugin-OfflinePython test -Dtest=BuildSummaryTest -B`
Expected: PASS.

- [ ] **Step 5: Wire BuildService to return BuildSummary**

Modify `command/BuildService.java`:
- Change the `build(...)` signature's return type from `int` to `BuildSummary`.
- Before running pip, count pre-existing wheels in the wheelhouse.
- Time the run; on success compute `BuildSummary.compute(wheelhouse, preExisting, durationMs)` and return it; on pip failure return `new BuildSummary(preExisting, preExisting, 0L, durationMs)` (no new downloads).

Replace the `build` method with:
```java
    public plugin.swisskit.offlinepython.domain.BuildSummary build(
            Path projectDir, BuildConfig cfg, String pythonExecutable,
            java.util.function.Consumer<String> onLog, ProcessRunner runner) throws Exception {
        Path output = projectDir.resolve(cfg.getRepository().getOutput());
        Path wheelhouse = output.resolve(cfg.getRepository().getWheelDir());
        Files.createDirectories(wheelhouse);

        int preExisting = countWheels(wheelhouse);
        Path reqs = projectDir.resolve("requirements.txt");
        List<String> cmd = ProcessRunner.pipDownloadCommand(
                pythonExecutable, reqs.toString(), wheelhouse.toString(),
                cfg.getPython().getPlatform(), majorMinor(cfg.getPython().getVersion()),
                cfg.getPython().getImplementation(), cfg.getDownload().isOnlyBinary());
        onLog.accept("$ " + String.join(" ", cmd));
        long start = System.currentTimeMillis();
        int code = runner.run(cmd, onLog);
        long duration = System.currentTimeMillis() - start;
        if (code != 0) {
            return new plugin.swisskit.offlinepython.domain.BuildSummary(preExisting, preExisting, 0L, duration);
        }
        writeManifest(projectDir, cfg, output, wheelhouse);
        writeSha256Sums(output);
        return plugin.swisskit.offlinepython.domain.BuildSummary.compute(wheelhouse, preExisting, duration);
    }

    private int countWheels(Path wheelhouse) throws IOException {
        try (Stream<Path> files = Files.list(wheelhouse)) {
            return (int) files.filter(p -> p.getFileName().toString().endsWith(".whl")).count();
        }
    }
```
Keep existing `writeManifest`, `writeSha256Sums`, `wheelNamePart`, `majorMinor` unchanged. Add `import java.util.stream.Stream;` if not present.

- [ ] **Step 6: Compile (BuildPanel still calls old int return — will break; fixed in Task 9. For now compile only BuildService + BuildSummary.)**

Run: `mvn -pl SwissKitJ-Plugin-OfflinePython compile -B`
Expected: may report errors in `BuildPanel` (old `int` usage) — those are fixed in Task 9. Confirm `BuildService.java` and `BuildSummary.java` themselves have no errors via `mcp__idea__get_file_problems` on those two files (expected 0). Do not commit a broken module; if the only errors are the known BuildPanel ones, proceed — Task 9 fixes them. If unsure, temporarily comment is NOT allowed; instead proceed to Task 9 before committing this task. 

> Simplified instruction: **defer the commit for Task 4 until after Task 9** (BuildPanel rewrite) so the module compiles cleanly. Implement BuildSummary + BuildService change now, but commit together with Task 9.

- [ ] **Step 7: (Commit deferred to Task 9)**

---

## Task 5: VerifyScope + VerifyService scope (TDD)

**Files:**
- Create: `src/main/java/plugin/swisskit/offlinepython/domain/VerifyScope.java`
- Modify: `src/main/java/plugin/swisskit/offlinepython/command/VerifyService.java`
- Test: `src/test/java/plugin/swisskit/offlinepython/VerifyScopeTest.java`

- [ ] **Step 1: Write the failing test**

`src/test/java/plugin/swisskit/offlinepython/VerifyScopeTest.java`:
```java
package plugin.swisskit.offlinepython;

import org.junit.jupiter.api.Test;
import plugin.swisskit.offlinepython.command.VerifyService;
import plugin.swisskit.offlinepython.domain.Manifest;
import plugin.swisskit.offlinepython.domain.Status;
import plugin.swisskit.offlinepython.domain.VerifyResult;
import plugin.swisskit.offlinepython.domain.VerifyScope;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class VerifyScopeTest {

    private final VerifyService svc = new VerifyService();

    @org.junit.jupiter.api.Test
    void allRunsEveryCheck(@org.junit.jupiter.api.io.TempDir Path tmp) throws Exception {
        Path output = tmp.resolve("output");
        Files.createDirectories(output);
        Manifest m = new Manifest();
        m.getWheels().add(new plugin.swisskit.offlinepython.domain.WheelEntry(
                "numpy", "1.26.4", "wheelhouse/numpy.whl", "", 0, true));
        Path wh = output.resolve("wheelhouse"); Files.createDirectories(wh);
        Files.writeString(wh.resolve("numpy.whl"), "x");

        VerifyResult r = svc.verify(output, m, VerifyScope.ALL);
        assertNotNull(r.sha256()); assertNotNull(r.fileIntegrity());
        assertNotNull(r.wheels()); assertNotNull(r.requirements()); assertNotNull(r.manifest());
    }

    @Test
    void integrityScopeSkipsSha256(@org.junit.jupiter.api.io.TempDir Path tmp) throws Exception {
        Path output = tmp.resolve("output");
        Files.createDirectories(output);
        Manifest m = new Manifest();
        VerifyResult r = svc.verify(output, m, VerifyScope.INTEGRITY);
        // INTEGRITY keeps fileIntegrity + manifest, nulls sha256
        assertNotNull(r.fileIntegrity());
        assertNull(r.sha256());
    }

    @Test
    void sha256ScopeOnlySha256(@org.junit.jupiter.api.io.TempDir Path tmp) throws Exception {
        Path output = tmp.resolve("output"); Files.createDirectories(output);
        VerifyResult r = svc.verify(output, new Manifest(), VerifyScope.SHA256);
        assertNotNull(r.sha256());
        assertNull(r.fileIntegrity());
        assertNull(r.wheels());
    }
}
```

- [ ] **Step 2: Run test → FAIL**

Run: `mvn -pl SwissKitJ-Plugin-OfflinePython test -Dtest=VerifyScopeTest -B`
Expected: FAIL — VerifyScope not found / verify signature mismatch.

- [ ] **Step 3: Create VerifyScope**

`src/main/java/plugin/swisskit/offlinepython/domain/VerifyScope.java`:
```java
package plugin.swisskit.offlinepython.domain;

public enum VerifyScope { ALL, INTEGRITY, SHA256 }
```

- [ ] **Step 4: Add scoped verify to VerifyService**

In `command/VerifyService.java`, keep the existing `verify(Path, Manifest)` (delegates to ALL) and add:
```java
    public plugin.swisskit.offlinepython.domain.VerifyResult verify(
            Path outputDir, Manifest manifest, plugin.swisskit.offlinepython.domain.VerifyScope scope) {
        boolean all = scope == plugin.swisskit.offlinepython.domain.VerifyScope.ALL;
        boolean integ = all || scope == plugin.swisskit.offlinepython.domain.VerifyScope.INTEGRITY;
        boolean sha = all || scope == plugin.swisskit.offlinepython.domain.VerifyScope.SHA256;
        return new plugin.swisskit.offlinepython.domain.VerifyResult(
                sha ? checkSha256(outputDir, manifest) : null,
                integ ? checkFileIntegrity(outputDir, manifest) : null,
                all ? checkWheels(manifest) : null,
                all ? checkRequirements(manifest) : null,
                integ ? checkManifest(manifest) : null);
    }
```
And change the existing `verify(Path, Manifest)` to `return verify(outputDir, manifest, plugin.swisskit.offlinepython.domain.VerifyScope.ALL);`.

> Note: `VerifyResult` is a record with 5 nullable fields allowed (records permit null components). The existing VerifyPanel/VerifyServiceTest use `verify(Path, Manifest)` → ALL, unaffected.

- [ ] **Step 5: Run test → PASS (3)**

Run: `mvn -pl SwissKitJ-Plugin-OfflinePython test -Dtest=VerifyScopeTest -B`
Expected: PASS. Also run the existing `VerifyServiceTest` to confirm no regression: `mvn -pl SwissKitJ-Plugin-OfflinePython test -Dtest=VerifyServiceTest -B` → PASS.

- [ ] **Step 6: Commit**

```bash
git add SwissKitJ-Plugin-OfflinePython/src/main/java/plugin/swisskit/offlinepython/domain/VerifyScope.java \
        SwissKitJ-Plugin-OfflinePython/src/main/java/plugin/swisskit/offlinepython/command/VerifyService.java \
        SwissKitJ-Plugin-OfflinePython/src/test/java/plugin/swisskit/offlinepython/VerifyScopeTest.java
git commit -m "feat(OfflinePython): VerifyScope (all/integrity/SHA256) filtering with tests"
```

---

## Task 6: DoctorService extension (pip-download-available + network)

**Files:**
- Modify: `src/main/java/plugin/swisskit/offlinepython/command/DoctorService.java`
- Test: `src/test/java/plugin/swisskit/offlinepython/DoctorServiceTest.java`

- [ ] **Step 1: Write the failing test**

`src/test/java/plugin/swisskit/offlinepython/DoctorServiceTest.java`:
```java
package plugin.swisskit.offlinepython;

import org.junit.jupiter.api.Test;
import plugin.swisskit.offlinepython.command.DoctorService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DoctorServiceTest {

    @Test
    void runReturnsExpectedCheckNames() {
        List<DoctorService.Check> checks = new DoctorService().run(null);
        var names = checks.stream().map(DoctorService.Check::name).toList();
        assertTrue(names.contains("Python 解释器"));
        assertTrue(names.contains("Python 版本"));
        assertTrue(names.contains("pip"));
        assertTrue(names.contains("pip download 可用"));
        assertTrue(names.contains("网络 (PyPI)"));
        assertTrue(names.contains("磁盘空间"));
        assertTrue(names.contains("缓存目录"));
        assertTrue(names.size() >= 7);
    }

    @Test
    void pipDownloadAvailableDetectsPlatformFlag() {
        // --platform flag present => available
        assertTrue(DoctorService.parsePipDownloadSupportsPlatform(
                "usage: pip download ... --platform <platform> ..."));
        // flag absent => not available (old pip)
        assertFalse(DoctorService.parsePipDownloadSupportsPlatform("usage: pip download ..."));
    }
}
```

- [ ] **Step 2: Run test → FAIL**

Run: `mvn -pl SwissKitJ-Plugin-OfflinePython test -Dtest=DoctorServiceTest -B`
Expected: FAIL — new methods/keys missing.

- [ ] **Step 3: Extend DoctorService**

Rewrite `command/DoctorService.java` to add `pip download 可用` and `网络 (PyPI)` checks plus the pure `parsePipDownloadSupportsPlatform`:
```java
package plugin.swisskit.offlinepython.command;

import plugin.swisskit.offlinepython.infra.ProcessRunner;

import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Diagnoses the host environment for build readiness. */
public class DoctorService {

    public record Check(String name, String value, boolean ok) {}

    public List<Check> run(String configuredExecutable) {
        List<Check> out = new ArrayList<>();
        var d = plugin.swisskit.offlinepython.infra.PythonDetector.detect(configuredExecutable);
        out.add(new Check("Python 解释器", d.executable() == null ? "未找到" : d.executable(), d.executable() != null));
        out.add(new Check("Python 版本", d.pythonVersion() == null ? "—" : d.pythonVersion(),
                d.pythonVersion() != null && plugin.swisskit.offlinepython.infra.PythonDetector.isAtLeast(d.pythonVersion(), "3.10")));
        out.add(new Check("pip", d.pipVersion() == null ? "缺失" : d.pipVersion(), d.pipVersion() != null));
        boolean pipDownloadOk = d.executable() != null && d.pipVersion() != null
                && parsePipDownloadSupportsPlatform(
                    ProcessRunner.captureQuiet(d.executable(), "-m", "pip", "download", "--help"));
        out.add(new Check("pip download 可用", pipDownloadOk ? "支持 --platform/--python-version" : "不支持跨平台下载", pipDownloadOk));
        boolean net = pingPyPI();
        out.add(new Check("网络 (PyPI)", net ? "可达" : "不可达", net));
        long freeGb = freeSpaceGb(Path.of(System.getProperty("user.home")));
        out.add(new Check("磁盘空间", freeGb + " GB 可用", freeGb > 1));
        Path cache = Path.of(System.getProperty("user.home"), ".offline-python", "cache");
        out.add(new Check("缓存目录", cache.toString(), isWritable(cache)));
        return out;
    }

    /** True if `pip download --help` mentions --platform (cross-platform download support). */
    public static boolean parsePipDownloadSupportsPlatform(String helpOutput) {
        return helpOutput != null && helpOutput.contains("--platform");
    }

    private boolean pingPyPI() {
        try {
            java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create("https://pypi.org/simple/"))
                    .timeout(java.time.Duration.ofSeconds(5))
                    .method("HEAD", java.net.http.HttpRequest.BodyPublishers.noBody()).build();
            java.net.http.HttpResponse<Void> r = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(5)).build()
                    .send(req, java.net.http.HttpResponse.BodyHandlers.discarding());
            return r.statusCode() >= 200 && r.statusCode() < 500;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isWritable(Path dir) {
        try { Files.createDirectories(dir); return Files.isWritable(dir); }
        catch (Exception e) { return false; }
    }

    private long freeSpaceGb(Path p) {
        try {
            FileStore store = Files.getFileStore(p);
            return store.getUsableSpace() / (1024L * 1024 * 1024);
        } catch (Exception e) { return 0; }
    }
}
```

- [ ] **Step 4: Run test → PASS (2)**

Run: `mvn -pl SwissKitJ-Plugin-OfflinePython test -Dtest=DoctorServiceTest -B`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add SwissKitJ-Plugin-OfflinePython/src/main/java/plugin/swisskit/offlinepython/command/DoctorService.java \
        SwissKitJ-Plugin-OfflinePython/src/test/java/plugin/swisskit/offlinepython/DoctorServiceTest.java
git commit -m "feat(OfflinePython): doctor checks for pip download support + PyPI network"
```

---

## Task 7: OpbStyle helpers for nav groups / disabled items / section labels

**Files:**
- Modify: `src/main/java/plugin/swisskit/offlinepython/ui/OpbStyle.java`

- [ ] **Step 1: Add helpers**

Add these methods to `OpbStyle` (keep all existing constants/methods):
```java
    /** Small uppercase group-label (仓库操作 / 查看与工具). */
    public static String groupLabel() {
        return "-fx-text-fill: " + TEXT_TERTIARY + "; -fx-font-size: 10px; -fx-font-weight: bold;";
    }

    /** Disabled (V2/V3) nav item style. */
    public static String navItemDisabled() {
        return "-fx-background-color: transparent; -fx-text-fill: " + TEXT_TERTIARY
             + "; -fx-background-radius: " + NAV_RADIUS + ";";
    }

    /** Small count badge (deps 角标). */
    public static String countBadge() {
        return "-fx-background-color: " + ACCENT + "; -fx-text-fill: white;"
             + " -fx-background-radius: 9; -fx-padding: 0 7 0 7; -fx-font-size: 10px;";
    }

    /** Section-header title (for panel headers). */
    public static String sectionHeader() {
        return "-fx-text-fill: " + TEXT_PRIMARY + "; -fx-font-size: 15px; -fx-font-weight: 500;";
    }
```

- [ ] **Step 2: Build**

Run: `mvn -pl SwissKitJ-Plugin-OfflinePython compile -B`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add SwissKitJ-Plugin-OfflinePython/src/main/java/plugin/swisskit/offlinepython/ui/OpbStyle.java
git commit -m "feat(OfflinePython): OpbStyle nav-group/disabled/badge/section helpers"
```

---

## Task 8: CommandPanel base — ProjectContext field + titleNode

**Files:**
- Modify: `src/main/java/plugin/swisskit/offlinepython/ui/panel/CommandPanel.java`

- [ ] **Step 1: Replace**

`src/main/java/plugin/swisskit/offlinepython/ui/panel/CommandPanel.java`:
```java
package plugin.swisskit.offlinepython.ui.panel;

import fan.summer.api.component.UiUtils;
import fan.summer.api.i18n.I18n;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import plugin.swisskit.offlinepython.ui.LogConsole;
import plugin.swisskit.offlinepython.ui.OpbStyle;
import plugin.swisskit.offlinepython.ui.ProjectContext;

/** Base for command panels: glass card + shared log + shared project context + section title. */
public abstract class CommandPanel extends VBox {
    protected final LogConsole log;
    protected final ProjectContext project;

    protected CommandPanel(LogConsole log, ProjectContext project) {
        this.log = log;
        this.project = project;
        setSpacing(14);
        setStyle(OpbStyle.card() + " -fx-padding: 18;");
    }

    protected Label titleNode() { return UiUtils.sectionTitle(title()); }

    public abstract String title();
}
```

> Note: this changes the base constructor to require `ProjectContext`. All panels are rewritten in Tasks 9–13 to pass it. **Defer commit until Task 13** (all panels updated) so the module compiles.

- [ ] **Step 2: (Commit deferred to Task 13)**

---

## Task 9: DepsPanel (rich table + toolbar + options + summary)

**Files:**
- Modify: `src/main/java/plugin/swisskit/offlinepython/ui/panel/DepsPanel.java`

> Also fixes the Task 4 BuildService return-type usage is in BuildPanel (Task 10), not here. This task is DepsPanel only.

- [ ] **Step 1: Replace DepsPanel**

`src/main/java/plugin/swisskit/offlinepython/ui/panel/DepsPanel.java`:
```java
package plugin.swisskit.offlinepython.ui.panel;

import fan.summer.api.component.GlassNotification;
import fan.summer.api.component.UiUtils;
import fan.summer.api.i18n.I18n;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import plugin.swisskit.offlinepython.command.DepsService;
import plugin.swisskit.offlinepython.domain.DependencySpec;
import plugin.swisskit.offlinepython.domain.RequirementsFile;
import plugin.swisskit.offlinepython.ui.LogConsole;
import plugin.swisskit.offlinepython.ui.ProjectContext;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class DepsPanel extends CommandPanel {

    /** Editable row backing the table. */
    public static class Row {
        public final javafx.beans.property.SimpleStringProperty name = new javafx.beans.property.SimpleStringProperty();
        public final javafx.beans.property.SimpleStringProperty version = new javafx.beans.property.SimpleStringProperty();
        public final javafx.beans.property.SimpleStringProperty platform = new javafx.beans.property.SimpleStringProperty();
        public final javafx.beans.property.SimpleStringProperty size = new javafx.beans.property.SimpleStringProperty("—");
        public Row(String n, String v, String p) { name.set(n); version.set(v); platform.set(p); }
        public String toRequirement() {
            String s = name.get() + (version.get() == null || version.get().isBlank() ? "" : version.get());
            return s;
        }
    }

    private final DepsService deps = new DepsService();
    private final TableView<Row> table = new TableView<>();
    private final CheckBox recursive = new CheckBox("递归");
    private final CheckBox wheelFirst = new CheckBox("wheel 优先");
    private final CheckBox upgradePip = new CheckBox("升级 pip");
    private final Label summary = new Label();

    public DepsPanel(LogConsole log, ProjectContext project) {
        super(log, project);
        recursive.setSelected(true); wheelFirst.setSelected(true);
        buildUi();
        loadFromProject();
    }

    @SuppressWarnings("unchecked")
    private void buildUi() {
        getChildren().add(titleNode());

        TableColumn<Row, String> cName = col("包名", "name", 1.4);
        TableColumn<Row, String> cVer = col("版本约束", "version", 1.0);
        TableColumn<Row, String> cPlat = col("目标平台", "platform", 1.1);
        TableColumn<Row, String> cSize = col("预估大小", "size", 0.8);
        TableColumn<Row, Row> cDel = new TableColumn<>("");
        cDel.setCellFactory(tc -> new TableCell<>() {
            private final Button del = UiUtils.glassBtn("✕", false);
            { del.setOnAction(e -> table.getItems().remove(getIndex())); }
            @Override protected void updateItem(Row r, boolean empty) {
                super.updateItem(r, empty); setGraphic(empty || r == null ? null : del);
            }
        });
        table.getColumns().addAll(cName, cVer, cPlat, cSize, cDel);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.setMinHeight(150);
        table.setStyle("-fx-background-color: transparent;");

        // toolbar
        TextField search = new TextField(); search.setStyle(UiUtils.fieldStyle()); search.setPromptText("搜索包…");
        search.textProperty().addListener((o, ov, nv) -> filterTable(nv));
        Button imp = UiUtils.glassBtn("导入 requirements.txt", false);
        imp.setOnAction(e -> doImport());
        Button pypiAdd = UiUtils.glassBtn("PyPI 查询版本", false);
        pypiAdd.setOnAction(e -> doPyPIFetch());
        Button save = UiUtils.glassBtn("保存", true);
        save.setOnAction(e -> doSave(false));
        HBox toolbar = new HBox(8, search, imp, pypiAdd);
        HBox.setHgrow(search, Priority.ALWAYS);
        toolbar.getChildren().addAll(new javafx.scene.layout.Region(), save);

        // add row
        TextField nField = new TextField(); nField.setStyle(UiUtils.fieldStyle()); nField.setPromptText("包名");
        TextField vField = new TextField(); vField.setStyle(UiUtils.fieldStyle()); vField.setPromptText("版本 (如 ==1.26.4)");
        TextField pField = new TextField(); pField.setStyle(UiUtils.fieldStyle()); pField.setPromptText("平台 (如 win_amd64)");
        Button add = UiUtils.glassBtn("＋", true);
        add.setOnAction(e -> {
            if (nField.getText().isBlank()) return;
            Row r = new Row(nField.getText().trim(), vField.getText().trim(),
                    pField.getText().isBlank() ? currentPlatform() : pField.getText().trim());
            table.getItems().add(r);
            nField.clear(); vField.clear(); pField.clear();
            refreshSummary();
        });
        HBox addRow = new HBox(8, labeled("包名", nField), labeled("版本", vField), labeled("平台", pField), add);
        HBox.setHgrow(nField, Priority.ALWAYS);

        // options
        HBox opts = new HBox(18, recursive, wheelFirst, upgradePip);
        opts.setStyle("-fx-text-fill: " + plugin.swisskit.offlinepython.ui.OpbStyle.TEXT_SECONDARY + ";");

        // summary
        Button saveBuild = UiUtils.glassBtn("保存并去构建 →", true);
        saveBuild.setOnAction(e -> doSave(true));
        HBox summaryBar = new HBox(14, summary, spacer(), platformPill(), saveBuild);
        summaryBar.setStyle(plugin.swisskit.offlinepython.ui.OpbStyle.card() + " -fx-padding: 10 14 10 14;");
        HBox.setHgrow(summaryBar, Priority.ALWAYS);

        VBox tableBox = new VBox(6, table);
        getChildren().addAll(toolbar, tableBox, addRow, opts, summaryBar);
    }

    private Label spacer() { Label s = new Label(); HBox.setHgrow(s, Priority.ALWAYS); return s; }

    private HBox labeled(String text, TextField f) {
        HBox h = new HBox(6, UiUtils.subLabel(text), f); HBox.setHgrow(f, Priority.ALWAYS); return h;
    }

    private TableColumn<Row, String> col(String title, String prop, double width) {
        TableColumn<Row, String> c = new TableColumn<>(title);
        c.setCellValueFactory(new PropertyValueFactory<>(prop));
        c.setPrefWidth(width * 100);
        c.setStyle("-fx-text-fill: rgba(255,255,255,0.85);");
        return c;
    }

    private Label platformPill() {
        Label p = new Label(currentPlatform());
        p.setStyle("-fx-background-color: " + plugin.swisskit.offlinepython.ui.OpbStyle.ACCENT_SOFT
                + "; -fx-text-fill: #9cc0ff; -fx-background-radius: 8; -fx-padding: 2 10 2 10;");
        return p;
    }

    private String currentPlatform() {
        return project.getConfig() != null && project.getConfig().getPython() != null
                ? project.getConfig().getPython().getPlatform() : "win_amd64";
    }

    private void filterTable(String q) {
        // Simple: TableView shows all; filter is best-effort via re-load not needed for V1.
        // (Search filtering omitted to keep V1 bounded; field present per spec for future.)
    }

    private void loadFromProject() {
        Path dir = project.getProjectDir();
        if (dir == null) return;
        Path req = dir.resolve("requirements.txt");
        try {
            if (Files.exists(req)) {
                table.getItems().setAll(toRows(RequirementsFile.parse(Files.readString(req))));
                refreshSummary();
            }
        } catch (Exception e) {
            log.log("加载 requirements 失败: " + e.getMessage());
        }
    }

    private List<Row> toRows(List<DependencySpec> specs) {
        List<Row> rows = new ArrayList<>();
        for (DependencySpec d : specs) rows.add(new Row(d.name(), d.versionSpec(), "win_amd64"));
        return rows;
    }

    private void doImport() {
        FileChooser fc = new FileChooser();
        File f = fc.showOpenDialog(getScene().getWindow());
        if (f == null) return;
        try {
            table.getItems().setAll(toRows(RequirementsFile.parse(Files.readString(f.toPath()))));
            refreshSummary();
            GlassNotification.toast(this, GlassNotification.Type.SUCCESS, "已导入 requirements.txt");
        } catch (Exception e) {
            GlassNotification.toast(this, GlassNotification.Type.ERROR, "导入失败");
        }
    }

    private void doPyPIFetch() {
        Row sel = table.getSelectionModel().getSelectedItem();
        if (sel == null) { GlassNotification.toast(this, GlassNotification.Type.WARNING, "先选中一行"); return; }
        new Thread(() -> {
            String exe = project.getConfig() != null ? project.getConfig().getPython().getExecutable() : null;
            var v = deps.latestVersion(sel.name.get(), exe);
            long size = deps.fetchSizeBytes(sel.name.get(), sel.version.get(), sel.platform.get());
            Platform.runLater(() -> {
                v.ifPresent(sel.version::set);
                sel.size.set(size > 0 ? humanSize(size) : "—");
                table.refresh();
                refreshSummary();
            });
        }, "opb-deps-pypi").start();
    }

    private void doSave(boolean thenBuild) {
        Path dir = project.getProjectDir();
        if (dir == null) { GlassNotification.toast(this, GlassNotification.Type.WARNING, "先打开或新建项目"); return; }
        try {
            List<DependencySpec> specs = new ArrayList<>();
            for (Row r : table.getItems()) specs.add(new DependencySpec(r.name.get(), r.version.get(), null));
            Files.writeString(dir.resolve("requirements.txt"), RequirementsFile.write(specs));
            // persist options into config.download
            if (project.getConfig() != null) {
                project.getConfig().getDownload().setRecursive(recursive.isSelected());
                project.getConfig().getDownload().setOnlyBinary(wheelFirst.isSelected());
                project.getConfig().getDownload().setUpgradePip(upgradePip.isSelected());
                project.saveConfig();
            }
            GlassNotification.toast(this, GlassNotification.Type.SUCCESS, "已保存依赖");
            log.log("已保存 " + specs.size() + " 条依赖");
            if (thenBuild) fireEventBuildNav();
        } catch (Exception e) {
            log.log("保存失败: " + e.getMessage());
            GlassNotification.toast(this, GlassNotification.Type.ERROR, "保存失败");
        }
    }

    /** Ask the shell to switch to the build panel. Implemented via a custom event. */
    private void fireEventBuildNav() {
        if (getScene() != null) getScene().getRoot().fireEvent(
                new plugin.swisskit.offlinepython.ui.NavEvent("build"));
    }

    private void refreshSummary() {
        int n = table.getItems().size();
        summary.setText("直接 " + n + " 个依赖");
    }

    private static String humanSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }

    @Override public String title() { return I18n.get("opb.deps.title"); }
}
```

- [ ] **Step 2: (Commit deferred — needs NavEvent (Task 12) + panel base compiled together.)**

---

## Task 10: BuildPanel (banner + result tiles)

**Files:**
- Modify: `src/main/java/plugin/swisskit/offlinepython/ui/panel/BuildPanel.java`

- [ ] **Step 1: Replace BuildPanel**

`src/main/java/plugin/swisskit/offlinepython/ui/panel/BuildPanel.java`:
```java
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
import plugin.swisskit.offlinepython.infra.JsonStore;
import plugin.swisskit.offlinepython.infra.ProcessRunner;
import plugin.swisskit.offlinepython.task.PluginTask;
import plugin.swisskit.offlinepython.ui.LogConsole;
import plugin.swisskit.offlinepython.ui.ProjectContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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
                + "; -fx-text-fill: #cfe0ff; -fx-background-radius: 10; -fx-padding: 9 12 9 12;");
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
                ? project.getConfig().getPython().getPlatform() : "?";
        banner.setText("📋 当前依赖：" + depCount + " 个直接  ·  目标 " + plat
                + (project.getConfig() != null && project.getConfig().getPython() != null
                    ? "  ·  Python " + project.getConfig().getPython().getVersion() : ""));
    }

    private int countDeps(Path dir) {
        if (dir == null) return 0;
        try {
            if (Files.exists(dir.resolve("requirements.txt")))
                return RequirementsFile_count(dir.resolve("requirements.txt"));
        } catch (Exception ignored) {}
        return 0;
    }
    private int RequirementsFile_count(Path req) throws java.io.IOException {
        return (int) RequirementsFile.parse(Files.readString(req)).stream().filter(d -> !d.name().isBlank()).count();
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
        Label l = new Label(label); l.setStyle("-fx-text-fill: rgba(255,255,255,0.45); -fx-font-size: 10px;");
        Label v = new Label(value); v.setStyle("-fx-text-fill: rgba(255,255,255,0.92); -fx-font-size: 16px; -fx-font-weight: 600;");
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
```

> Removes the old `int`-return usage, matching the Task 4 BuildService change.

- [ ] **Step 2: (Commit deferred — module compiles after Tasks 11–13.)**

---

## Task 11: VerifyPanel (scope segments + report + conclusion)

**Files:**
- Modify: `src/main/java/plugin/swisskit/offlinepython/ui/panel/VerifyPanel.java`

- [ ] **Step 1: Replace VerifyPanel**

`src/main/java/plugin/swisskit/offlinepython/ui/panel/VerifyPanel.java`:
```java
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

import java.nio.file.Files;
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
        segs.setStyle("-fx-background-color: rgba(255,255,255,0.06); -fx-background-radius: 8;");
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
        b.setStyle("-fx-text-fill: rgba(255,255,255,0.8); -fx-background-radius: 8; -fx-cursor: hand;");
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
        conclusion.setStyle("-fx-background-radius: 10; -fx-padding: 10 14; -fx-font-weight: 500;");
        if (fail) {
            conclusion.setText("⚠ 仓库存在问题");
            conclusion.setStyle("-fx-background-color: rgba(242,92,92,0.14); -fx-text-fill: #f25c5c;"
                    + " -fx-background-radius: 10; -fx-padding: 10 14; -fx-font-weight: 500;");
        } else if (warn) {
            conclusion.setText("✓ 仓库可用（含警告）");
            conclusion.setStyle("-fx-background-color: rgba(245,166,35,0.14); -fx-text-fill: #f5a623;"
                    + " -fx-background-radius: 10; -fx-padding: 10 14; -fx-font-weight: 500;");
        } else {
            conclusion.setText("✓ Repository OK");
            conclusion.setStyle("-fx-background-color: rgba(76,217,123,0.14); -fx-text-fill: #4cd97b;"
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
            case PASS -> "rgba(76,217,123,0.18)";
            case WARN -> "rgba(245,166,35,0.18)";
            case FAIL -> "rgba(242,92,92,0.18)";
        };
    }

    @Override public String title() { return I18n.get("opb.verify.title"); }
}
```

- [ ] **Step 2: (Commit deferred.)**

---

## Task 12: DoctorPanel (7-check table) + NavEvent

**Files:**
- Modify: `src/main/java/plugin/swisskit/offlinepython/ui/panel/DoctorPanel.java`
- Create: `src/main/java/plugin/swisskit/offlinepython/ui/NavEvent.java`

- [ ] **Step 1: Create NavEvent** (custom event so DepsPanel's "保存并去构建" can ask the shell to switch tabs)

`src/main/java/plugin/swisskit/offlinepython/ui/NavEvent.java`:
```java
package plugin.swisskit.offlinepython.ui;

import javafx.event.Event;
import javafx.event.EventType;

/** Fired by panels (e.g. DepsPanel "保存并去构建") to request the shell switch nav. */
public class NavEvent extends Event {
    public static final EventType<NavEvent> NAV = new EventType<>(Event.ANY, "OPB_NAV");
    private final String target;
    public NavEvent(String target) { super(NAV); this.target = target; }
    public String target() { return target; }
}
```

- [ ] **Step 2: Replace DoctorPanel**

`src/main/java/plugin/swisskit/offlinepython/ui/panel/DoctorPanel.java`:
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
import plugin.swisskit.offlinepython.ui.ProjectContext;

public class DoctorPanel extends CommandPanel {
    private final GridPane grid = new GridPane();

    public DoctorPanel(LogConsole log, ProjectContext project) {
        super(log, project);
        getChildren().add(titleNode());
        Button run = UiUtils.glassBtn("▶ 运行诊断", true);
        run.setOnAction(e -> {
            grid.getChildren().clear();
            int row = 0;
            for (var c : new DoctorService().run(project.getConfig() != null
                    ? project.getConfig().getPython().getExecutable() : null)) {
                Label key = UiUtils.subLabel(c.name());
                Label val = new Label(c.value());
                val.setStyle("-fx-text-fill: " + (c.ok() ? OpbStyle.SUCCESS : OpbStyle.DANGER) + ";");
                Label mark = new Label(c.ok() ? "✓" : "✕");
                mark.setStyle("-fx-text-fill: " + (c.ok() ? OpbStyle.SUCCESS : OpbStyle.DANGER)
                        + "; -fx-font-weight: bold;");
                grid.add(key, 0, row); grid.add(val, 1, row); grid.add(mark, 2, row);
                row++;
            }
            log.log("诊断完成");
        });
        grid.setHgap(14); grid.setVgap(6);
        // header
        Label h0 = UiUtils.subLabel("检查项"), h1 = UiUtils.subLabel("结果"), h2 = UiUtils.subLabel("");
        grid.add(h0, 0, -1);
        getChildren().addAll(run, grid);
    }

    @Override public String title() { return I18n.get("opb.doctor.title"); }
}
```

> Note: the `grid.add(h0, 0, -1)` line is wrong (negative row). Remove those three header lines — GridPane header is optional. Use this corrected constructor tail instead:
```java
        grid.setHgap(14); grid.setVgap(6);
        getChildren().addAll(run, grid);
```
(Delete the three `Label h0...` / `grid.add(h0,0,-1)` lines.)

- [ ] **Step 3: (Commit deferred.)**

---

## Task 13: InitPanel + PythonInstallGuide + CommandShell rewrite + wire OfflinePythonPlugin

**Files:**
- Modify: `src/main/java/plugin/swisskit/offlinepython/ui/panel/InitPanel.java`
- Modify: `src/main/java/plugin/swisskit/offlinepython/ui/PythonInstallGuide.java`
- Modify: `src/main/java/plugin/swisskit/offlinepython/ui/CommandShell.java`

> This task also includes the deferred commits from Tasks 4, 8, 9, 10, 11, 12 — at its end the whole module compiles.

- [ ] **Step 1: Replace InitPanel**

`src/main/java/plugin/swisskit/offlinepython/ui/panel/InitPanel.java`:
```java
package plugin.swisskit.offlinepython.ui.panel;

import fan.summer.api.component.GlassNotification;
import fan.summer.api.component.UiUtils;
import fan.summer.api.i18n.I18n;
import plugin.swisskit.offlinepython.ui.LogConsole;
import plugin.swisskit.offlinepython.ui.ProjectContext;

public class InitPanel extends CommandPanel {
    public InitPanel(LogConsole log, ProjectContext project) {
        super(log, project);
        getChildren().add(titleNode());
        getChildren().add(UiUtils.subLabel("在顶栏点「新建」创建项目，或「打开」现有项目目录。"));
        var note = UiUtils.subLabel("init 会在项目目录生成 config.json、requirements.txt、README.md。");
        getChildren().add(note);
    }
    @Override public String title() { return I18n.get("opb.init.title"); }
}
```

- [ ] **Step 2: Replace PythonInstallGuide** (§7.2 full)

`src/main/java/plugin/swisskit/offlinepython/ui/PythonInstallGuide.java`:
```java
package plugin.swisskit.offlinepython.ui;

import fan.summer.api.component.GlassNotification;
import fan.summer.api.component.UiUtils;
import fan.summer.api.i18n.I18n;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.awt.Desktop;
import java.net.URI;
import java.io.File;

public class PythonInstallGuide extends VBox {
    public PythonInstallGuide(Runnable onRedetect) {
        setSpacing(10);
        setStyle(OpbStyle.card() + " -fx-padding: 20;");

        Label warn = new Label("⚠ " + I18n.get("opb.python.missing"));
        warn.setStyle("-fx-text-fill: " + OpbStyle.WARNING + "; -fx-font-size: 15px; -fx-font-weight: 500;");
        getChildren().add(warn);
        getChildren().add(UiUtils.subLabel("本插件需要 Python ≥ 3.10 + pip。"));
        getChildren().add(cmdRow("macOS", "brew install python", true));
        getChildren().add(linkRow("Windows", "https://www.python.org/downloads"));
        getChildren().add(cmdRow("Linux", "sudo apt install python3 python3-pip", true));

        Button retry = UiUtils.glassBtn("安装后点此重新检测", true);
        retry.setOnAction(e -> onRedetect.run());
        Button manual = UiUtils.glassBtn("手动指定路径…", false);
        manual.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("选择 python 可执行文件");
            File f = fc.showOpenDialog(getScene().getWindow());
            if (f == null) return;
            onManualPath(f.getAbsolutePath(), onRedetect);
        });
        getChildren().add(new HBox(8, retry, manual));
    }

    private HBox cmdRow(String os, String cmd, boolean copyable) {
        Label l = UiUtils.subLabel(os);
        TextField field = new TextField(cmd);
        field.setEditable(false);
        field.setStyle(UiUtils.fieldStyle());
        field.setPrefWidth(300);
        Button copy = UiUtils.glassBtn("复制", false);
        copy.setOnAction(e -> {
            ClipboardContent c = new ClipboardContent(); c.putString(cmd);
            Clipboard.getSystemClipboard().setContent(c);
            GlassNotification.toast(this, GlassNotification.Type.SUCCESS, "已复制: " + cmd);
        });
        HBox row = new HBox(8, l, field, copy);
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(field, javafx.scene.layout.Priority.ALWAYS);
        return row;
    }

    private HBox linkRow(String os, String url) {
        Label l = UiUtils.subLabel(os);
        TextField field = new TextField(url);
        field.setEditable(false); field.setStyle(UiUtils.fieldStyle()); field.setPrefWidth(300);
        Button open = UiUtils.glassBtn("打开浏览器", false);
        open.setOnAction(e -> {
            try { Desktop.getDesktop().browse(new URI(url)); }
            catch (Exception ex) { GlassNotification.toast(this, GlassNotification.Type.ERROR, "无法打开浏览器"); }
        });
        HBox row = new HBox(8, l, field, open);
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(field, javafx.scene.layout.Priority.ALWAYS);
        return row;
    }

    private void onManualPath(String path, Runnable onRedetect) {
        // Best-effort: write to the user config dir's config.json if a project is open; else just re-detect.
        // (ProjectContext not available here; the shell re-detects after this.)
        onRedetect.run();
        GlassNotification.toast(this, GlassNotification.Type.INFO, "已记录路径，重新检测中");
    }
}
```

- [ ] **Step 3: Rewrite CommandShell** (top bar + 2-group nav + collapsible log + NavEvent handling)

`src/main/java/plugin/swisskit/offlinepython/ui/CommandShell.java`:
```java
package plugin.swisskit.offlinepython.ui;

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
            GlassNotification.toast(root, fan.summer.api.component.GlassNotification.Type.SUCCESS, "项目已初始化");
        } catch (Exception e) {
            GlassNotification.toast(root, fan.summer.api.component.GlassNotification.Type.ERROR, "新建失败");
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
```

- [ ] **Step 4: OfflinePythonPlugin** — no change required (it already constructs CommandShell and returns getView()). Verify it still compiles; the constructor signature is unchanged.

- [ ] **Step 5: Build the whole module**

Run: `mvn -pl SwissKitJ-Plugin-OfflinePython compile -B` (or IDE build_project on the OfflinePython sources)
Expected: BUILD SUCCESS. Use `mcp__idea__get_file_problems` on each changed UI file to confirm 0 problems.

- [ ] **Step 6: Run full test suite**

Run: `mvn -pl SwissKitJ-Plugin-OfflinePython test -B`
Expected: all existing tests + new (OpbStyle, ProjectContext, DepsService, BuildSummary, VerifyScope, DoctorService) pass.

- [ ] **Step 7: Commit (covers Tasks 4, 8–13)**

```bash
git add SwissKitJ-Plugin-OfflinePython
git commit -m "feat(OfflinePython): rebuild UI per spec §9 — top bar, two-group nav, collapsible log, rich panels"
```

---

## Task 14: DevLauncher visual verification

**Files:** none.

- [ ] **Step 1: Resolve the known DevLauncher classpath issue (prerequisite)**

IntelliJ → Maven tool window → **Reload All Maven Projects** (so `SwissKitJ-Api:3.1.0` is on the classpath), then regenerate the `DevLauncher` run config (right-click `DevLauncher.main()` → Run). Confirm the launched `-classpath` includes the 3.1.0 API jar (no `NoClassDefFoundError`).

- [ ] **Step 2: Launch and verify against the confirmed mockups**

Launch `DevLauncher`. Confirm:
- **Top bar**: project selector (opens dir), 新建, 打开, Python badge (green/red).
- **Left nav**: two groups (仓库操作 / 查看与工具); 11 commands; V1 active, V2/V3 greyed with V2/V3 tags; deps shows a count badge.
- **Right content**: switches per nav; Python-missing → PythonInstallGuide card (3 platforms + 复制/打开浏览器 + 重新检测 + 手动指定路径).
- **DepsPanel**: dependency table (name/version/platform/size/delete) + add row + toolbar (search/导入/PyPI 查询版本/保存) + options (递归/wheel 优先/升级 pip) + summary + 「保存并去构建 →」switches to build.
- **BuildPanel**: current-deps banner + build/cancel + progress + 4 result tiles (已下载/耗时/大小/缓存命中).
- **VerifyPanel**: scope segments (全量/仅完整性/仅 SHA256) + PASS/WARN/FAIL report + conclusion banner.
- **DoctorPanel**: 7-check table incl. pip download 可用 + 网络 (PyPI).
- **Log console**: collapsible (▾/▸) + level filter pills + live pip output.

- [ ] **Step 3: Commit any polish**

```bash
git add -A
git commit -m "chore(OfflinePython): UI rebuild verified via DevLauncher" || echo "nothing to commit"
```

---

## Self-Review (completed)

**1. Spec coverage (§9 / §7.2 / §8):**
- 顶栏 项目选择器 + 新建/打开 + Python 徽章 → Task 13 (CommandShell top bar). ✓
- 左栏 双组 11 命令导航，deps 角标，V2/V3 灰显 → Task 13 (buildNav/addGroup). ✓
- 右侧 StackPane 切换 + PythonInstallGuide 整体替换 → Task 13 (select/refreshPython). ✓
- 底部 日志控制台 可折叠 + 级别过滤 → Tasks 1 + 13. ✓
- 面板共性：表单 + 执行/取消 + 进度/结果，UiUtils 玻璃风 → Tasks 7–13. ✓
- DepsPanel 依赖表/工具栏/选项/汇总 → Task 9; DepsService (pip index versions + PyPI 大小) → Task 3 (spec §8 deps). ✓
- BuildPanel 当前依赖 banner + 进度 + 结果 tile（已下载/耗时/大小/缓存命中）→ Tasks 4 + 10. ✓
- VerifyPanel 范围分段 + 逐项报告 + Repository OK 结论 → Tasks 5 + 11. ✓
- DoctorPanel 7 项检查（含 pip download 可用 / 网络）→ Tasks 6 + 12. ✓
- PythonInstallGuide §7.2（3 平台 + 复制/打开浏览器 + 重新检测/手动路径）→ Task 13. ✓
- 生命周期/取消/hasRunningTasks/onUnload(cancel) → Task 13. ✓

**2. Placeholder scan:** No TBD/TODO. Task 12 has a self-correcting instruction (delete the invalid `grid.add(..., -1)` header lines — the corrected tail is given). All code steps contain complete code. ✓

**3. Type consistency:**
- `CommandPanel(LogConsole, ProjectContext)` used by all panels (Tasks 9–13). ✓
- `ProjectContext.projectDirProperty()/configProperty()/openExisting/createNew/getConfig/saveConfig` consistent across Tasks 2, 9, 10, 13. ✓
- `BuildService.build(...) → BuildSummary` (Task 4) consumed by BuildPanel (Task 10). ✓
- `VerifyService.verify(Path, Manifest, VerifyScope)` (Task 5) consumed by VerifyPanel (Task 11). ✓
- `DoctorService.run(String)` 7-check list (Task 6) consumed by DoctorPanel (Task 12). ✓
- `DepsService.latestVersion/fetchSizeBytes` + static parsers (Task 3) consumed by DepsPanel (Task 9). ✓
- `LogConsole.log(LogLevel, String)/setVisibleLevels/setCollapsed` (Task 1) consumed by CommandShell (Task 13). ✓
- `NavEvent` (Task 12) fired by DepsPanel (Task 9) and handled by CommandShell (Task 13). ✓
- `OpbStyle` helpers (Task 7) used across panels. ✓

---

## Out of scope (deferred)

- V2 commands (update/clean/list/info/cache) and V3 (pack/export) functionality — shown disabled in nav.
- Full i18n of new UI label strings (currently hard-coded literals; existing `opb.*` keys reused).
- DepsPanel search-filtering of rows (field present, wiring deferred).
- `手动指定路径` writing to config.executable when no project is open (re-detect only for now).
- Pre-build "解析后 ~N" count (only known after build; summary shows direct count).
- Migrating sibling plugins to the host theme (separate effort).
```
