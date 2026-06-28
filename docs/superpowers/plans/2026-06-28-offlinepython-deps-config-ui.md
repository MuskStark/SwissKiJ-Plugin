# OfflinePython 依赖配置面板 UI 重构 — 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 重构 `DepsPanel`(依赖配置面板):表格可读性(玻璃表样式)、目标平台改为全局多选下拉(并打通构建层多 `--platform`)、蓝色按钮收敛为唯一主 CTA + Tooltip。

**Architecture:** 纯逻辑下沉到可单测的 `PlatformCatalog`(平台清单 + 汇总/至少一个的纯函数);新增 `PlatformMultiSelect`(JavaFX `MenuButton`+`CheckMenuItem` 薄视图);`BuildConfig.Python.platform` 单值升级为 `platforms: List<String>`,`ProcessRunner` 循环发 `--platform`,`Manifest` 同步;`OpbStyle` 加表格 CSS 助手;`DepsPanel` 近乎重写(玻璃表 + 镜像平台列 + 按钮收敛)。每个 task 结束模块可编译、测试全绿。

**Tech Stack:** Java 21、JavaFX 21.0.2(provided)、Lombok 1.18.42(`@Data`)、Gson 2.11.0、JUnit Jupiter 5.10.2、Maven。

**关键约束(已核实):**
- `getPlatform()`/`setPlatform()` 调用点仅 4 处:`DepsPanel.java:153`、`BuildPanel.java:57`、`BuildService.java:36`、`BuildService.java:83`。`Manifest` 的 `platform` 仅在 `BuildService:83` 写入,无读取点。
- 测试运行命令:`mvn -f SwissKitJ-Plugin-OfflinePython/pom.xml -q -Dtest=<TestClass> test`(单类)/`... test`(全量)。JavaFX 为 `provided` 作用域,编译+测试可用;无 JavaFX UI 测试框架,故 UI 节点靠 DevLauncher 手动验证,纯逻辑走单测。
- 手动运行 app:`mvn -f SwissKitJ-Plugin-OfflinePython/pom.xml -Pdev javafx:run`。
- 提交信息沿用仓库前缀 `feat(OfflinePython):` / `refactor(OfflinePython):`。

---

## 文件结构

| 文件 | 责任 | 动作 |
|---|---|---|
| `domain/PlatformCatalog.java` | 平台清单 + 纯选择助手(汇总/toggle 至少一个/label) | 新增 |
| `ui/OpbStyle.java` | 玻璃设计 CSS 助手 | 加 3 个表格方法 |
| `domain/BuildConfig.java` | 配置 POJO | `Python.platform` → `platforms` + `getPrimaryPlatform()` |
| `domain/Manifest.java` | 构建产物清单 POJO | `Python.platform` → `platforms` |
| `infra/ProcessRunner.java` | 子进程/命令构建 | `pipDownloadCommand` 改 `List<String> platforms`,循环 `--platform` |
| `command/BuildService.java` | 跑 pip download + 写 manifest | 读 `getPlatforms()`、manifest 写全部 |
| `ui/panel/BuildPanel.java` | 构建面板 | banner 用 `getPrimaryPlatform()` |
| `ui/panel/DepsPanel.java` | 依赖配置面板 | 近乎重写 |
| `ui/control/PlatformMultiSelect.java` | 多选平台下拉(`MenuButton`) | 新增 |
| 测试:`PlatformCatalogTest`(新)、`OpbStyleTest`、`ProcessRunnerTest`、`BuildConfigTest`、`ManifestTest` | | |

---

## Task 1: PlatformCatalog(纯逻辑 + 单测)

**Files:**
- Create: `SwissKitJ-Plugin-OfflinePython/src/main/java/plugin/swisskit/offlinepython/domain/PlatformCatalog.java`
- Test: `SwissKitJ-Plugin-OfflinePython/src/test/java/plugin/swisskit/offlinepython/PlatformCatalogTest.java`

- [ ] **Step 1: 写失败测试**

Create `PlatformCatalogTest.java`:

```java
package plugin.swisskit.offlinepython;

import org.junit.jupiter.api.Test;
import plugin.swisskit.offlinepython.domain.PlatformCatalog;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PlatformCatalogTest {

    @Test
    void allTagsArePipValidPlatformTags() {
        // pip --platform accepts win_amd64, win32 (note: no underscore), manylinux*, macosx_*, any
        for (PlatformCatalog.Entry e : PlatformCatalog.ALL) {
            String t = e.tag();
            assertTrue(t.matches("^(win_amd64|win32|manylinux[0-9_]*_(x86_64|aarch64)|macosx_[0-9_]+_(x86_64|arm64)|any)$"),
                    "invalid tag: " + t);
            assertFalse(e.label().isBlank());
        }
    }

    @Test
    void labelOfKnownAndUnknown() {
        assertEquals("Windows x64", PlatformCatalog.labelOf("win_amd64"));
        assertEquals("zzz", PlatformCatalog.labelOf("zzz"));
    }

    @Test
    void summarySingleShowsTag() {
        assertEquals("win_amd64", PlatformCatalog.summary(List.of("win_amd64")));
    }

    @Test
    void summaryTwoJoinsWithComma() {
        assertEquals("win_amd64、manylinux2014_x86_64",
                PlatformCatalog.summary(List.of("win_amd64", "manylinux2014_x86_64")));
    }

    @Test
    void summaryThreeAppendsCount() {
        assertEquals("win_amd64、manylinux2014_x86_64 +1",
                PlatformCatalog.summary(List.of("win_amd64", "manylinux2014_x86_64", "any")));
    }

    @Test
    void summaryEmptyDefaultsToWinAmd64() {
        assertEquals("win_amd64", PlatformCatalog.summary(List.of()));
    }

    @Test
    void toggleAddsMissingTag() {
        assertEquals(List.of("win_amd64", "any"),
                PlatformCatalog.toggle(List.of("win_amd64"), "any"));
    }

    @Test
    void toggleRemovesPresentTagWhenOthersRemain() {
        assertEquals(List.of("win_amd64"),
                PlatformCatalog.toggle(List.of("win_amd64", "any"), "any"));
    }

    @Test
    void toggleRefusesToRemoveLastPlatform() {
        assertEquals(List.of("win_amd64"),
                PlatformCatalog.toggle(List.of("win_amd64"), "win_amd64"));
    }
}
```

- [ ] **Step 2: 运行测试,确认失败**

Run: `mvn -f SwissKitJ-Plugin-OfflinePython/pom.xml -q -Dtest=PlatformCatalogTest test`
Expected: 编译失败 / `PlatformCatalog` 无法解析。

- [ ] **Step 3: 写最小实现**

Create `PlatformCatalog.java`:

```java
package plugin.swisskit.offlinepython.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Catalog of pip-valid target platforms plus pure selection helpers (summary text and an
 * at-least-one toggle guard). Pure logic lives here so it is unit-testable headless; the
 * JavaFX {@code PlatformMultiSelect} view is a thin wrapper over these methods.
 */
public final class PlatformCatalog {

    /** A supported platform: a pip-valid --platform tag plus a Chinese display label. */
    public record Entry(String tag, String label) {}

    /** Ordered catalog of supported target platforms. */
    public static final List<Entry> ALL = List.of(
            new Entry("win_amd64", "Windows x64"),
            new Entry("win32", "Windows x86"),
            new Entry("manylinux2014_x86_64", "Linux x64"),
            new Entry("manylinux2014_aarch64", "Linux ARM64"),
            new Entry("macosx_10_15_x86_64", "macOS Intel"),
            new Entry("macosx_11_0_arm64", "macOS Apple Silicon"),
            new Entry("any", "通用（纯 Python）"));

    /** Display label for a tag, or the tag itself if not in the catalog. */
    public static String labelOf(String tag) {
        for (Entry e : ALL) if (e.tag().equals(tag)) return e.label();
        return tag;
    }

    /**
     * Compact summary for a selection: one item → its tag; two → "a、b"; three+ → "a、b +N".
     * Used by both the dropdown button text and the table's target-platform column.
     */
    public static String summary(List<String> selected) {
        if (selected == null || selected.isEmpty()) return "win_amd64";
        if (selected.size() == 1) return selected.get(0);
        if (selected.size() == 2) return selected.get(0) + "、" + selected.get(1);
        return selected.get(0) + "、" + selected.get(1) + " +" + (selected.size() - 2);
    }

    /**
     * Toggle a tag in the selection. Removing the last remaining platform is refused
     * (returns the list unchanged) so at least one target is always selected.
     */
    public static List<String> toggle(List<String> selected, String tag) {
        List<String> next = new ArrayList<>(selected == null ? List.of() : selected);
        if (next.contains(tag)) {
            if (next.size() <= 1) return next; // keep at least one
            next.remove(tag);
        } else {
            next.add(tag);
        }
        return next;
    }

    private PlatformCatalog() {}
}
```

- [ ] **Step 4: 运行测试,确认通过**

Run: `mvn -f SwissKitJ-Plugin-OfflinePython/pom.xml -q -Dtest=PlatformCatalogTest test`
Expected: PASS(9 tests)。

- [ ] **Step 5: 提交**

```bash
git add SwissKitJ-Plugin-OfflinePython/src/main/java/plugin/swisskit/offlinepython/domain/PlatformCatalog.java \
        SwissKitJ-Plugin-OfflinePython/src/test/java/plugin/swisskit/offlinepython/PlatformCatalogTest.java
git commit -m "feat(OfflinePython): PlatformCatalog — platform list + pure summary/toggle helpers with tests"
```

---

## Task 2: OpbStyle 表格 CSS 助手

**Files:**
- Modify: `SwissKitJ-Plugin-OfflinePython/src/main/java/plugin/swisskit/offlinepython/ui/OpbStyle.java`(在 `sectionHeader()` 之后追加 3 个方法)
- Test: `SwissKitJ-Plugin-OfflinePython/src/test/java/plugin/swisskit/offlinepython/OpbStyleTest.java`(追加用例)

- [ ] **Step 1: 写失败测试**

在 `OpbStyleTest.java` 末尾(`}` 前)追加:

```java
    @Test
    void tableCellStyleAppliesFontSizeAndOptions() {
        String s = OpbStyle.tableCellStyle(OpbStyle.TEXT_PRIMARY, true, true);
        assertTrue(s.contains("-fx-font-size: 13px"));
        assertTrue(s.contains(OpbStyle.TEXT_PRIMARY));
        assertTrue(s.contains("-fx-font-weight: bold"));
        assertTrue(s.contains("monospace"));
        assertTrue(s.contains("-fx-padding"));
    }

    @Test
    void tableRowStyleZebraAndSelection() {
        assertTrue(OpbStyle.tableRowStyle(true, false).contains(OpbStyle.GLASS_BG_HOVER));
        assertTrue(OpbStyle.tableRowStyle(false, false).contains(OpbStyle.GLASS_BG));
        assertTrue(OpbStyle.tableRowStyle(false, true).contains(OpbStyle.ACCENT_SOFT));
    }

    @Test
    void tableHeaderStyleIsSecondaryBoldSmall() {
        String s = OpbStyle.tableHeaderStyle();
        assertTrue(s.contains(OpbStyle.TEXT_SECONDARY));
        assertTrue(s.contains("11px"));
        assertTrue(s.contains("bold"));
    }
```

- [ ] **Step 2: 运行测试,确认失败**

Run: `mvn -f SwissKitJ-Plugin-OfflinePython/pom.xml -q -Dtest=OpbStyleTest test`
Expected: 编译失败(`tableCellStyle`/`tableRowStyle`/`tableHeaderStyle` 无法解析)。

- [ ] **Step 3: 写实现**

在 `OpbStyle.java` 的 `sectionHeader()` 方法(`return "..."; }`)之后、类结束 `}` 之前,追加:

```java
    /** Data TableCell text style: 13px, given text color, optional bold / monospace, cell padding. */
    public static String tableCellStyle(String textColor, boolean bold, boolean mono) {
        String s = "-fx-text-fill: " + textColor + "; -fx-font-size: 13px; -fx-padding: 4 8 4 8;";
        if (bold) s += " -fx-font-weight: bold;";
        if (mono) s += " -fx-font-family: monospace;";
        return s;
    }

    /** TableRow background: zebra by index parity, accent-soft when selected. */
    public static String tableRowStyle(boolean odd, boolean selected) {
        String bg = selected ? ACCENT_SOFT : (odd ? GLASS_BG_HOVER : GLASS_BG);
        return "-fx-background-color: " + bg + ";";
    }

    /** Table header label style: secondary, 11px, bold. */
    public static String tableHeaderStyle() {
        return "-fx-text-fill: " + TEXT_SECONDARY + "; -fx-font-size: 11px; -fx-font-weight: bold;";
    }
```

- [ ] **Step 4: 运行测试,确认通过**

Run: `mvn -f SwissKitJ-Plugin-OfflinePython/pom.xml -q -Dtest=OpbStyleTest test`
Expected: PASS(含原有 + 新增 3)。

- [ ] **Step 5: 提交**

```bash
git add SwissKitJ-Plugin-OfflinePython/src/main/java/plugin/swisskit/offlinepython/ui/OpbStyle.java \
        SwissKitJ-Plugin-OfflinePython/src/test/java/plugin/swisskit/offlinepython/OpbStyleTest.java
git commit -m "feat(OfflinePython): OpbStyle table CSS helpers — header/cell/row(zebra+selection)"
```

---

## Task 3: platform → platforms 打通配置与构建管线

> 本 task 把单值 platform 升级为 `List<String> platforms`,并更新**所有**读取点,使模块在本 task 结束时可编译、测试全绿、构建管线真正支持多平台。`DepsPanel.currentPlatform()` 仅做 1 行最小改动以保持编译(完整 UI 改写在 Task 5)。

**Files:**
- Modify: `domain/BuildConfig.java:15, 31-37`
- Modify: `domain/Manifest.java:18-24`
- Modify: `infra/ProcessRunner.java:21-33`
- Modify: `command/BuildService.java:36, 83`
- Modify: `ui/panel/BuildPanel.java:56-57`
- Modify: `ui/panel/DepsPanel.java:151-154`(最小改动)
- Test: `ProcessRunnerTest.java`(重写)、`BuildConfigTest.java`(改 + 加)、`ManifestTest.java`(加用例)

- [ ] **Step 1: 写失败测试 — ProcessRunner 多平台**

整文件替换 `ProcessRunnerTest.java` 为:

```java
package plugin.swisskit.offlinepython;

import org.junit.jupiter.api.Test;
import plugin.swisskit.offlinepython.infra.ProcessRunner;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProcessRunnerTest {

    @Test
    void buildsPipDownloadCommandForWindowsTarget() {
        List<String> cmd = ProcessRunner.pipDownloadCommand(
            "/usr/local/bin/python3.12", "requirements.txt", "output/wheelhouse",
            List.of("win_amd64"), "3.12", "cp", true);
        assertEquals(List.of(
            "/usr/local/bin/python3.12", "-m", "pip", "download",
            "-r", "requirements.txt",
            "-d", "output/wheelhouse",
            "--platform", "win_amd64",
            "--python-version", "3.12",
            "--implementation", "cp",
            "--only-binary=:all:"
        ), cmd);
    }

    @Test
    void omitsOnlyBinaryFlagWhenFalse() {
        List<String> cmd = ProcessRunner.pipDownloadCommand(
            "python3", "r.txt", "wh", List.of("linux_x86_64"), "3.12", "cp", false);
        assertFalse(cmd.contains("--only-binary=:all:"));
        assertTrue(cmd.contains("--platform"));
        assertTrue(cmd.contains("linux_x86_64"));
    }

    @Test
    void emitsOnePlatformFlagPerSelectedPlatformInOrder() {
        List<String> cmd = ProcessRunner.pipDownloadCommand(
            "python3", "r.txt", "wh",
            List.of("win_amd64", "manylinux2014_x86_64"), "3.12", "cp", true);
        int i = cmd.indexOf("--platform");
        assertEquals("win_amd64", cmd.get(i + 1));
        int j = i + 1 + cmd.subList(i + 1, cmd.size()).indexOf("--platform");
        assertEquals("manylinux2014_x86_64", cmd.get(j + 1));
        assertEquals(2, cmd.stream().filter("--platform"::equals).count());
    }

    @Test
    void fallsBackToAnyPlatformWhenSelectionEmpty() {
        List<String> cmd = ProcessRunner.pipDownloadCommand(
            "python3", "r.txt", "wh", List.of(), "3.12", "cp", true);
        assertTrue(cmd.contains("--platform"));
        assertTrue(cmd.contains("any"));
    }
}
```

- [ ] **Step 2: 运行测试,确认失败**

Run: `mvn -f SwissKitJ-Plugin-OfflinePython/pom.xml -q -Dtest=ProcessRunnerTest test`
Expected: 编译失败(`pipDownloadCommand` 形参仍是 `String platform`)。

- [ ] **Step 3: 改 ProcessRunner.pipDownloadCommand**

替换 `infra/ProcessRunner.java` 中 `pipDownloadCommand` 整个方法(原 22-33 行)为:

```java
    /** Build the platform-targeted pip download command list. Emits one --platform per
     *  selected platform (pip accepts repeated --platform). Empty selection falls back to "any". */
    public static List<String> pipDownloadCommand(String python, String requirements,
                                                  String destDir, List<String> platforms,
                                                  String pythonVersion, String implementation,
                                                  boolean onlyBinary) {
        List<String> cmd = new ArrayList<>();
        cmd.add(python);
        cmd.addAll(List.of("-m", "pip", "download", "-r", requirements, "-d", destDir));
        List<String> plats = (platforms == null || platforms.isEmpty()) ? List.of("any") : platforms;
        for (String p : plats) cmd.addAll(List.of("--platform", p));
        cmd.addAll(List.of("--python-version", pythonVersion, "--implementation", implementation));
        if (onlyBinary) cmd.add("--only-binary=:all:");
        return cmd;
    }
```

- [ ] **Step 4: 改 BuildConfig.Python(platform → platforms + getPrimaryPlatform)**

在 `domain/BuildConfig.java`:

(a) `defaults()` 第 15 行 `c.python.platform = "win_amd64";` 改为:
```java
        c.python.platforms = new java.util.ArrayList<>(java.util.List.of("win_amd64"));
```

(b) `Python` 内部类(31-37 行)整段替换为:
```java
    @Data public static class Python {
        private String version;
        private java.util.List<String> platforms = new java.util.ArrayList<>(java.util.List.of("win_amd64"));
        private String implementation;
        private boolean installer;
        private String executable; // null = auto-detect

        /** First selected platform (primary for estimates / single-platform display); defaults to win_amd64. */
        public String getPrimaryPlatform() {
            return platforms == null || platforms.isEmpty() ? "win_amd64" : platforms.get(0);
        }
    }
```

> Lombok 为 `platforms` 生成 `getPlatforms()/setPlatforms(...)`;手写 `getPrimaryPlatform()` 不冲突。`platform` 字段被移除,旧 `config.json` 里残留的 `platform` 键 Gson 默认忽略,`platforms` 由字段初始化器兜底为 `["win_amd64"]`(本分支未发布,旧非默认 platform 取值丢失可接受)。

- [ ] **Step 5: 改 Manifest.Python(platform → platforms)**

在 `domain/Manifest.java`,`Python` 内部类(18-24 行)整段替换为:
```java
    @Data
    public static class Python {
        private String version;
        private java.util.List<String> platforms = new java.util.ArrayList<>();
        private String installer;       // relative path
        private String installerSha256;
    }
```

- [ ] **Step 6: 改 BuildService 两处读取点**

在 `command/BuildService.java`:
- 第 36 行 `cfg.getPython().getPlatform(),` 改为 `cfg.getPython().getPlatforms(),`
- 第 83 行 `m.getPython().setPlatform(cfg.getPython().getPlatform());` 改为:
```java
        m.getPython().setPlatforms(new java.util.ArrayList<>(cfg.getPython().getPlatforms()));
```

- [ ] **Step 7: 改 BuildPanel banner(57 行)**

在 `ui/panel/BuildPanel.java` 第 56-57 行:
```java
        String plat = project.getConfig() != null && project.getConfig().getPython() != null
                ? project.getConfig().getPython().getPlatform() : "?";
```
改为:
```java
        String plat = project.getConfig() != null && project.getConfig().getPython() != null
                ? project.getConfig().getPython().getPrimaryPlatform() : "?";
```

- [ ] **Step 8: 改 DepsPanel.currentPlatform 最小改动(保持编译)**

在 `ui/panel/DepsPanel.java` 第 151-154 行 `currentPlatform()` 内,把 `getPython().getPlatform()` 改为 `getPython().getPrimaryPlatform()`:
```java
    private String currentPlatform() {
        return project.getConfig() != null && project.getConfig().getPython() != null
                ? project.getConfig().getPython().getPrimaryPlatform() : "win_amd64";
    }
```
> 该方法在 Task 5 整体重写时删除;此处仅为保持编译绿。

- [ ] **Step 9: 改 BuildConfigTest(改现有 + 加新)**

`BuildConfigTest.java` 顶部加 `import java.util.List;`,并把 `roundTripsThroughJson` 用例改为:

```java
    @Test
    void roundTripsThroughJson(@TempDir Path tmp) throws Exception {
        BuildConfig cfg = BuildConfig.defaults();
        cfg.getPython().setVersion("3.12.10");
        cfg.getPython().setPlatforms(new java.util.ArrayList<>(List.of("win_amd64", "manylinux2014_x86_64")));

        Path file = tmp.resolve("config.json");
        JsonStore.save(cfg, file);
        BuildConfig loaded = JsonStore.load(file, BuildConfig.class);

        assertEquals("3.12.10", loaded.getPython().getVersion());
        assertEquals(List.of("win_amd64", "manylinux2014_x86_64"), loaded.getPython().getPlatforms());
        assertEquals("win_amd64", loaded.getPython().getPrimaryPlatform());
        assertTrue(loaded.getDownload().isRecursive());
    }
```

并在类内追加:
```java
    @Test
    void primaryPlatformDefaultsToWinAmd64WhenEmpty() {
        BuildConfig cfg = new BuildConfig();
        cfg.getPython().setPlatforms(new java.util.ArrayList<>());
        assertEquals("win_amd64", cfg.getPython().getPrimaryPlatform());
    }
```
> 现有 `defaultsAreSensible` 用例不变。

- [ ] **Step 10: 加 Manifest platforms 用例**

`ManifestTest.java` 顶部加 `import java.util.List;`,类内追加:
```java
    @Test
    void manifestTracksPlatforms() {
        Manifest m = new Manifest();
        m.getPython().setPlatforms(new java.util.ArrayList<>(List.of("win_amd64", "manylinux2014_aarch64")));
        String json = new com.google.gson.GsonBuilder().create().toJson(m);
        Manifest back = JsonStore.fromJson(json, Manifest.class);
        assertEquals(List.of("win_amd64", "manylinux2014_aarch64"), back.getPython().getPlatforms());
    }
```

- [ ] **Step 11: 全量测试,确认通过**

Run: `mvn -f SwissKitJ-Plugin-OfflinePython/pom.xml -q test`
Expected: 全部 PASS(含 ProcessRunner/BuildConfig/Manifest 新增用例)。确认无 `getPlatform`/`setPlatform` 编译错误。

- [ ] **Step 12: 提交**

```bash
git add SwissKitJ-Plugin-OfflinePython/src/main/java/plugin/swisskit/offlinepython/domain/BuildConfig.java \
        SwissKitJ-Plugin-OfflinePython/src/main/java/plugin/swisskit/offlinepython/domain/Manifest.java \
        SwissKitJ-Plugin-OfflinePython/src/main/java/plugin/swisskit/offlinepython/infra/ProcessRunner.java \
        SwissKitJ-Plugin-OfflinePython/src/main/java/plugin/swisskit/offlinepython/command/BuildService.java \
        SwissKitJ-Plugin-OfflinePython/src/main/java/plugin/swisskit/offlinepython/ui/panel/BuildPanel.java \
        SwissKitJ-Plugin-OfflinePython/src/main/java/plugin/swisskit/offlinepython/ui/panel/DepsPanel.java \
        SwissKitJ-Plugin-OfflinePython/src/test/java/plugin/swisskit/offlinepython/ProcessRunnerTest.java \
        SwissKitJ-Plugin-OfflinePython/src/test/java/plugin/swisskit/offlinepython/BuildConfigTest.java \
        SwissKitJ-Plugin-OfflinePython/src/test/java/plugin/swisskit/offlinepython/ManifestTest.java
git commit -m "refactor(OfflinePython): platform→platforms[] end-to-end — multi --platform pip download + manifest"
```

---

## Task 4: PlatformMultiSelect(多选下拉视图)

> JavaFX `MenuButton`+`CheckMenuItem` 薄视图。可单测的选择逻辑(`summary`/`toggle`/至少一个)已在 Task 1 覆盖;本 task 的节点连线靠 DevLauncher 手动验证(本仓库无 JavaFX 测试框架)。

**Files:**
- Create: `SwissKitJ-Plugin-OfflinePython/src/main/java/plugin/swisskit/offlinepython/ui/control/PlatformMultiSelect.java`

- [ ] **Step 1: 写实现**

Create `PlatformMultiSelect.java`:

```java
package plugin.swisskit.offlinepython.ui.control;

import javafx.scene.control.MenuButton;
import javafx.scene.control.CheckMenuItem;
import plugin.swisskit.offlinepython.domain.PlatformCatalog;
import plugin.swisskit.offlinepython.ui.OpbStyle;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Multi-select target-platform dropdown: a MenuButton of CheckMenuItems backed by a plain
 * selection list. Selection bookkeeping (summary, at-least-one guard) is delegated to
 * {@link PlatformCatalog}; this class is the JavaFX wiring only.
 */
public class PlatformMultiSelect extends MenuButton {

    private final List<String> selected = new ArrayList<>(List.of("win_amd64"));
    private boolean updating = false;
    private Consumer<List<String>> onChange;

    public PlatformMultiSelect() {
        super(PlatformCatalog.summary(List.of("win_amd64")));
        setStyle("-fx-background-color: " + OpbStyle.GLASS_BG + "; -fx-text-fill: " + OpbStyle.TEXT_PRIMARY
                + "; -fx-border-color: " + OpbStyle.GLASS_BORDER + "; -fx-background-radius: 8; -fx-cursor: hand;");
        rebuildMenu();
    }

    private void rebuildMenu() {
        getItems().clear();
        for (PlatformCatalog.Entry e : PlatformCatalog.ALL) {
            CheckMenuItem mi = new CheckMenuItem(PlatformCatalog.labelOf(e.tag()) + "  (" + e.tag() + ")");
            mi.setSelected(selected.contains(e.tag()));
            mi.selectedProperty().addListener((o, ov, nv) -> {
                if (updating) return;
                updating = true;
                try {
                    List<String> next = PlatformCatalog.toggle(selected, e.tag());
                    if (next.equals(selected)) { mi.setSelected(true); return; } // refused: keep at least one
                    selected.clear();
                    selected.addAll(next);
                    setText(PlatformCatalog.summary(selected));
                    if (onChange != null) onChange.accept(getSelected());
                } finally {
                    updating = false;
                }
            });
            getItems().add(mi);
        }
    }

    /** Current selection (defensive copy). */
    public List<String> getSelected() { return List.copyOf(selected); }

    /** Primary platform (first selected) used for size estimates; never empty. */
    public String primaryPlatform() { return selected.isEmpty() ? "win_amd64" : selected.get(0); }

    /** Compact summary text (delegates to PlatformCatalog). */
    public String summary() { return PlatformCatalog.summary(selected); }

    /** Replace the selection (at least one platform enforced). */
    public void setSelected(List<String> sel) {
        selected.clear();
        if (sel == null || sel.isEmpty()) selected.add("win_amd64");
        else selected.addAll(sel);
        setText(PlatformCatalog.summary(selected));
        rebuildMenu();
        if (onChange != null) onChange.accept(getSelected());
    }

    /** Notified after each successful selection change. */
    public void setOnChange(Consumer<List<String>> cb) { this.onChange = cb; }
}
```

- [ ] **Step 2: 编译确认**

Run: `mvn -f SwissKitJ-Plugin-OfflinePython/pom.xml -q test-compile`
Expected: BUILD SUCCESS(无编译错误)。

- [ ] **Step 3: 手动验证(DevLauncher)**

Run: `mvn -f SwissKitJ-Plugin-OfflinePython/pom.xml -Pdev javafx:run`
在依赖面板工具栏找到「目标平台」下拉(注:此时 DepsPanel 还未接入,Task 5 才接入——若本步不便独立验证,可与 Task 5 合并手动验证)。预期:本步仅需确认模块带新类可正常编译启动。

- [ ] **Step 4: 提交**

```bash
git add SwissKitJ-Plugin-OfflinePython/src/main/java/plugin/swisskit/offlinepython/ui/control/PlatformMultiSelect.java
git commit -m "feat(OfflinePython): PlatformMultiSelect — MenuButton+CheckMenuItem multi-select dropdown"
```

---

## Task 5: DepsPanel UI 重写(玻璃表 + 镜像平台列 + 按钮收敛)

> 近乎整文件重写。UI 视图无单测(仓库惯例);依赖的逻辑(PlatformCatalog / 配置 / ProcessRunner)已在 Task 1、3 覆盖。本 task 以 DevLauncher 手动验证收尾。

**Files:**
- Modify(整文件替换): `SwissKitJ-Plugin-OfflinePython/src/main/java/plugin/swisskit/offlinepython/ui/panel/DepsPanel.java`

- [ ] **Step 1: 整文件替换 DepsPanel.java**

用以下内容**完整替换** `DepsPanel.java`:

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
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import plugin.swisskit.offlinepython.command.DepsService;
import plugin.swisskit.offlinepython.domain.DependencySpec;
import plugin.swisskit.offlinepython.domain.RequirementsFile;
import plugin.swisskit.offlinepython.ui.LogConsole;
import plugin.swisskit.offlinepython.ui.OpbStyle;
import plugin.swisskit.offlinepython.ui.ProjectContext;
import plugin.swisskit.offlinepython.ui.control.PlatformMultiSelect;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class DepsPanel extends CommandPanel {

    /** Editable row backing the table (name/version/size only — platform is global). */
    public static class Row {
        public final javafx.beans.property.SimpleStringProperty name = new javafx.beans.property.SimpleStringProperty();
        public final javafx.beans.property.SimpleStringProperty version = new javafx.beans.property.SimpleStringProperty();
        public final javafx.beans.property.SimpleStringProperty size = new javafx.beans.property.SimpleStringProperty("—");
        public Row(String n, String v) { name.set(n); version.set(v); }
        // PropertyValueFactory-style accessors (kept for any external binding).
        public javafx.beans.property.SimpleStringProperty nameProperty() { return name; }
        public javafx.beans.property.SimpleStringProperty versionProperty() { return version; }
        public javafx.beans.property.SimpleStringProperty sizeProperty() { return size; }
        public String toRequirement() {
            return name.get() + (version.get() == null || version.get().isBlank() ? "" : version.get());
        }
    }

    private static final String WHITE = "rgba(255,255,255,1.0)";

    private final DepsService deps = new DepsService();
    private final TableView<Row> table = new TableView<>();
    private final CheckBox recursive = new CheckBox("递归");
    private final CheckBox wheelFirst = new CheckBox("wheel 优先");
    private final CheckBox upgradePip = new CheckBox("升级 pip");
    private final Label summary = new Label();
    private final PlatformMultiSelect platformSelect = new PlatformMultiSelect();

    public DepsPanel(LogConsole log, ProjectContext project) {
        super(log, project);
        recursive.setSelected(true); wheelFirst.setSelected(true);
        buildUi();
        loadFromProject();
    }

    @SuppressWarnings("unchecked")
    private void buildUi() {
        getChildren().add(titleNode());

        // --- columns ---
        TableColumn<Row, String> cName = textCol("包名", 1.4, r -> r.name.get(),
                OpbStyle.tableCellStyle(OpbStyle.TEXT_PRIMARY, true, false));
        TableColumn<Row, String> cVer = textCol("版本约束", 1.0, r -> r.version.get(),
                OpbStyle.tableCellStyle(OpbStyle.TEXT_SECONDARY, false, true));
        TableColumn<Row, String> cPlat = mirrorPlatformCol();
        TableColumn<Row, String> cSize = textCol("预估大小", 0.9, r -> r.size.get(),
                OpbStyle.tableCellStyle(OpbStyle.TEXT_PRIMARY, false, true));
        TableColumn<Row, Row> cDel = new TableColumn<>("");
        cDel.setCellFactory(tc -> new TableCell<>() {
            private final Button del = UiUtils.glassBtn("✕", false);
            { del.setTooltip(new Tooltip("删除该行"));
              del.setOnAction(e -> { table.getItems().remove(getIndex()); refreshSummary(); }); }
            @Override protected void updateItem(Row r, boolean empty) {
                super.updateItem(r, empty); setGraphic(empty || r == null ? null : del);
            }
        });
        table.getColumns().addAll(cName, cVer, cPlat, cSize, cDel);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.setFixedCellSize(30);
        table.setMinHeight(150);
        table.setStyle("-fx-background-color: transparent; -fx-font-size: 13px; -fx-table-cell-border-color: transparent;");
        // zebra rows + selection highlight
        table.setRowFactory(tv -> new javafx.scene.control.TableRow<>() {
            @Override protected void updateItem(Row r, boolean empty) {
                super.updateItem(r, empty);
                setStyle(empty || r == null ? "" : OpbStyle.tableRowStyle(getIndex() % 2 == 1, isSelected()));
            }
        });
        // refresh the mirror platform column whenever the global selection changes
        platformSelect.setOnChange(sel -> table.refresh());

        // --- toolbar (all secondary; platform dropdown + add on the right) ---
        TextField search = new TextField(); search.setStyle(UiUtils.fieldStyle()); search.setPromptText("搜索包…");
        search.textProperty().addListener((o, ov, nv) -> filterTable(nv));
        Button imp = UiUtils.glassBtn("导入 requirements.txt", false);
        imp.setTooltip(new Tooltip("选择本地 requirements.txt 并解析为依赖表"));
        imp.setOnAction(e -> doImport());
        Button pypiAdd = UiUtils.glassBtn("PyPI 查询版本", false);
        pypiAdd.setTooltip(new Tooltip("为选中行从 PyPI 查询最新版本与 wheel 大小"));
        pypiAdd.setOnAction(e -> doPyPIFetch());
        Button add = UiUtils.glassBtn("+ 添加依赖", false);
        add.setTooltip(new Tooltip("添加一行依赖，平台跟随全局目标"));
        HBox toolbar = new HBox(8, search, imp, pypiAdd);
        HBox.setHgrow(search, Priority.ALWAYS);
        Region spring = new Region(); HBox.setHgrow(spring, Priority.ALWAYS);
        toolbar.getChildren().addAll(spring, platformSelect, add);

        // --- add-row form (platform is global, no per-row field) ---
        TextField nField = new TextField(); nField.setStyle(UiUtils.fieldStyle()); nField.setPromptText("包名");
        TextField vField = new TextField(); vField.setStyle(UiUtils.fieldStyle()); vField.setPromptText("版本 (如 ==1.26.4)");
        add.setOnAction(e -> {
            if (nField.getText().isBlank()) return;
            table.getItems().add(new Row(nField.getText().trim(), vField.getText().trim()));
            nField.clear(); vField.clear();
            table.refresh();
            refreshSummary();
        });
        HBox addRow = new HBox(8, labeled("包名", nField), labeled("版本", vField));
        HBox.setHgrow(nField, Priority.ALWAYS);

        // --- options ---
        HBox opts = new HBox(18, recursive, wheelFirst, upgradePip);
        opts.setStyle("-fx-text-fill: " + OpbStyle.TEXT_SECONDARY + ";");

        // --- summary bar (secondary save + single primary CTA) ---
        Button save = UiUtils.glassBtn("保存 requirements.txt", false);
        save.setTooltip(new Tooltip("仅保存依赖与配置，不构建"));
        save.setOnAction(e -> doSave(false));
        Button saveBuild = UiUtils.glassBtn("保存并去构建 →", true);
        saveBuild.setTooltip(new Tooltip("保存后跳转构建面板"));
        saveBuild.setOnAction(e -> doSave(true));
        HBox summaryBar = new HBox(14, summary, spacer(), save, saveBuild);
        summaryBar.setStyle(OpbStyle.card() + " -fx-padding: 10 14 10 14;");
        HBox.setHgrow(summaryBar, Priority.ALWAYS);

        VBox tableBox = new VBox(6, table);
        getChildren().addAll(toolbar, tableBox, addRow, opts, summaryBar);
    }

    /** Styled text column backed by a per-row value supplier. */
    private TableColumn<Row, String> textCol(String title, double widthFactor,
                                             java.util.function.Function<Row, String> value, String cellStyle) {
        TableColumn<Row, String> c = new TableColumn<>(title);
        c.setCellValueFactory(cb -> new javafx.beans.property.SimpleStringProperty(value.apply(cb.getValue())));
        c.setPrefWidth(widthFactor * 100);
        c.setStyle(OpbStyle.tableHeaderStyle());
        c.setCellFactory(tc -> new TableCell<>() {
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                if (empty) { setText(null); setStyle(""); return; }
                setText(v == null || v.isBlank() ? "—" : v);
                setStyle(cellStyle);
            }
        });
        return c;
    }

    /** Target-platform column: ignores row data, mirrors the global PlatformMultiSelect (white text, high contrast). */
    private TableColumn<Row, String> mirrorPlatformCol() {
        TableColumn<Row, String> c = new TableColumn<>("目标平台");
        c.setPrefWidth(150);
        c.setStyle(OpbStyle.tableHeaderStyle());
        c.setCellFactory(tc -> new TableCell<>() {
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                if (empty) { setText(null); setStyle(""); return; }
                setText(platformSelect.summary());
                setStyle(OpbStyle.tableCellStyle(WHITE, false, false));
            }
        });
        return c;
    }

    private Label spacer() { Label s = new Label(); HBox.setHgrow(s, Priority.ALWAYS); return s; }

    private HBox labeled(String text, TextField f) {
        HBox h = new HBox(6, UiUtils.subLabel(text), f); HBox.setHgrow(f, Priority.ALWAYS); return h;
    }

    private void filterTable(String q) {
        // Search filtering omitted to keep V1 bounded; field present per spec for future.
    }

    private void loadFromProject() {
        Path dir = project.getProjectDir();
        if (dir == null) return;
        try {
            Path req = dir.resolve("requirements.txt");
            if (Files.exists(req)) {
                table.getItems().setAll(toRows(RequirementsFile.parse(Files.readString(req))));
            }
            if (project.getConfig() != null && project.getConfig().getPython() != null
                    && project.getConfig().getPython().getPlatforms() != null) {
                platformSelect.setSelected(project.getConfig().getPython().getPlatforms());
            }
            table.refresh();
            refreshSummary();
        } catch (Exception e) {
            log.log("加载 requirements 失败: " + e.getMessage());
        }
    }

    private List<Row> toRows(List<DependencySpec> specs) {
        List<Row> rows = new ArrayList<>();
        for (DependencySpec d : specs) rows.add(new Row(d.name(), d.versionSpec()));
        return rows;
    }

    private void doImport() {
        FileChooser fc = new FileChooser();
        File f = fc.showOpenDialog(getScene().getWindow());
        if (f == null) return;
        try {
            table.getItems().setAll(toRows(RequirementsFile.parse(Files.readString(f.toPath()))));
            table.refresh();
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
            long size = deps.fetchSizeBytes(sel.name.get(), sel.version.get(), platformSelect.primaryPlatform());
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
            if (project.getConfig() != null) {
                project.getConfig().getDownload().setRecursive(recursive.isSelected());
                project.getConfig().getDownload().setOnlyBinary(wheelFirst.isSelected());
                project.getConfig().getDownload().setUpgradePip(upgradePip.isSelected());
                project.getConfig().getPython().setPlatforms(new ArrayList<>(platformSelect.getSelected()));
                project.saveConfig();
            }
            GlassNotification.toast(this, GlassNotification.Type.SUCCESS, "已保存依赖");
            log.log("已保存 " + specs.size() + " 条依赖 · 目标 " + platformSelect.getSelected().size() + " 个平台");
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
        int p = platformSelect.getSelected().size();
        summary.setText("直接 " + n + " 个依赖 · 目标 " + p + " 个平台（预估大小按主平台）");
    }

    private static String humanSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }

    @Override public String title() { return I18n.get("opb.deps.title"); }
}
```

- [ ] **Step 2: 编译确认**

Run: `mvn -f SwissKitJ-Plugin-OfflinePython/pom.xml -q test-compile`
Expected: BUILD SUCCESS。

- [ ] **Step 3: 全量测试,确认仍绿**

Run: `mvn -f SwissKitJ-Plugin-OfflinePython/pom.xml -q test`
Expected: 全部 PASS(本 task 不改测试,确认未回归)。

- [ ] **Step 4: 手动验证(DevLauncher)**

Run: `mvn -f SwissKitJ-Plugin-OfflinePython/pom.xml -Pdev javafx:run`

逐项核对:
- **表格**:5 列(包名/版本约束/目标平台/预估大小/✕);13px 字体、30px 行高、隔行斑马底;选中行 accent-soft 高亮;包名加粗、版本居中等宽、大小右对齐等宽;目标平台列**全白高对比**文字。
- **目标平台下拉**:工具栏右侧 `MenuButton`,点开是 7 个 `CheckMenuItem`(带中文标签 + tag);默认勾 `win_amd64`;勾选/取消即时刷新表格「目标平台」列与汇总文案;**取消最后一个**被拒绝(回弹)。
- **按钮**:工具栏「导入 requirements.txt / PyPI 查询版本 / + 添加依赖」均为次要(非蓝);底部「保存 requirements.txt」次要、「保存并去构建 →」**唯一蓝色**主按钮;每个按钮悬停有 Tooltip。
- **保存**:点「保存」后 `config.json` 的 `python.platforms` 为当前选中清单(检查文件)。
- **构建联动**:选 ≥2 平台 → 保存 → 跳构建面板执行,日志里 `pip download ... --platform A --platform B ...`(检查 manifest.json 的 `python.platforms` 记录全部)。
- **+ 添加依赖**:只填包名/版本,新行「目标平台」列随全局显示。

- [ ] **Step 5: 提交**

```bash
git add SwissKitJ-Plugin-OfflinePython/src/main/java/plugin/swisskit/offlinepython/ui/panel/DepsPanel.java
git commit -m "feat(OfflinePython): rebuild DepsPanel — glass table, global multi-select platform mirror col, single primary CTA + tooltips"
```

---

## 收尾(可选)

- [ ] 若 Task 4 与 Task 5 的手动验证合并执行,确认两个 commit 后 app 行为正常即可。
- [ ] 全量回归:`mvn -f SwissKitJ-Plugin-OfflinePython/pom.xml -q test`。
