# OfflinePython 依赖配置面板 v2 — 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 DepsPanel 改成三行布局 + per-dependency 目标平台 + PyPI 在线搜索，并打通构建层按平台集合分组下载。

**Architecture:** 平台从全局下沉为每条依赖自带（`config.python.depPlatforms` map）。构建层按依赖的平台集合分组 `pip download`（specs 内联，不再 `-r requirements.txt`）。新增 `PyPISearchDialog` 拉 PyPI JSON 解析 wheel 列表，选中回填表单。纯逻辑（配置序列化、命令拼接、wheel 解析、分组）走 TDD；JavaFX UI（对话框、面板）靠 DevLauncher 手动验证。

**Tech Stack:** Java 17+、JavaFX、Lombok `@Data`、Gson、JDK HttpClient、JUnit 5、Maven (surefire)。

**Spec:** `docs/superpowers/specs/2026-06-29-offlinepython-deps-config-ui-v2-design.md`

**模块根目录**（除非另注，所有相对路径以此为准）：`SwissKitJ-Plugin-OfflinePython/`
**跑单个测试类**：`mvn -pl SwissKitJ-Plugin-OfflinePython -am test -Dtest=<TestClass>`（在仓库根执行）
**跑全部测试**：`mvn -pl SwissKitJ-Plugin-OfflinePython -am test`
**手动验证 UI**：在 IDEA 里运行 `plugin.swisskit.offlinepython.DevLauncher`（main 类），打开 OfflinePython → 依赖配置面板。

---

## 文件结构

| 文件 | 责任 | 动作 |
|---|---|---|
| `src/main/java/.../domain/BuildConfig.java` | 配置模型；`Python` 加 `depPlatforms` map | 修改 |
| `src/main/java/.../command/WheelInfo.java` | PyPI wheel 条目 record | 新增 |
| `src/main/java/.../command/DepsService.java` | +`searchWheels`/`parseWheels`/`extractPlatformTag` | 修改 |
| `src/main/java/.../infra/ProcessRunner.java` | `pipDownloadCommand` 改 specs 列表 + recursive | 修改 |
| `src/main/java/.../command/BuildService.java` | 按平台集合分组下载 + manifest 写并集 + 接 recursive | 修改 |
| `src/main/java/.../ui/dialog/PyPISearchDialog.java` | 在线搜索 wheel 的模态窗 | 新增 |
| `src/main/java/.../ui/panel/DepsPanel.java` | 三行布局 + per-dep 平台 + 主从编辑 + 接搜索 | 修改 |
| `src/test/java/.../BuildConfigTest.java` | +depPlatforms 往返/兜底 | 修改 |
| `src/test/java/.../DepsServiceTest.java` | +parseWheels/extractPlatformTag | 修改 |
| `src/test/java/.../ProcessRunnerTest.java` | 适配新签名 + recursive | 修改 |
| `src/test/java/.../ManifestTest.java` | +平台并集（由 BuildService 写入） | 修改 |
| `src/test/java/.../BuildServiceTest.java` | 分组/兜底/normalize/并集 纯逻辑 | 新增 |

依赖顺序：Task 1（配置模型）→ Task 2（wheel 解析）→ Task 3（构建管线）→ Task 4（搜索对话框）→ Task 5（面板）。Task 1/2 互不依赖可并行；Task 3 依赖 1；Task 5 依赖 1/2/4。

---

## Task 1: BuildConfig.depPlatforms 数据模型

**Files:**
- Modify: `src/main/java/plugin/swisskit/offlinepython/domain/BuildConfig.java`（`Python` 内部类，约 31-42 行）
- Test: `src/test/java/plugin/swisskit/offlinepython/BuildConfigTest.java`

`platforms` 字段保留（语义降为"新增依赖默认平台"），新增 `depPlatforms` map。

- [ ] **Step 1: 写失败测试** — 在 `BuildConfigTest` 末尾追加两个用例：

```java
    @Test
    void roundTripsDepPlatformsMap(@TempDir Path tmp) throws Exception {
        BuildConfig cfg = BuildConfig.defaults();
        cfg.getPython().getDepPlatforms().put("numpy",
                new java.util.ArrayList<>(List.of("win_amd64", "manylinux2014_x86_64")));
        cfg.getPython().getDepPlatforms().put("requests",
                new java.util.ArrayList<>(List.of("win_amd64")));

        Path file = tmp.resolve("config.json");
        JsonStore.save(cfg, file);
        BuildConfig loaded = JsonStore.load(file, BuildConfig.class);

        assertNotNull(loaded.getPython().getDepPlatforms());
        assertEquals(List.of("win_amd64", "manylinux2014_x86_64"),
                loaded.getPython().getDepPlatforms().get("numpy"));
        assertEquals(List.of("win_amd64"),
                loaded.getPython().getDepPlatforms().get("requests"));
    }

    @Test
    void legacyConfigWithoutDepPlatformsLoadsEmptyMap() {
        String legacyJson = "{\"python\":{\"version\":\"3.12.10\",\"platforms\":[\"win_amd64\"]}}";
        BuildConfig loaded = JsonStore.fromJson(legacyJson, BuildConfig.class);
        assertNotNull(loaded.getPython().getDepPlatforms());
        assertTrue(loaded.getPython().getDepPlatforms().isEmpty());
    }
```

- [ ] **Step 2: 跑测试确认失败** — Run: `mvn -pl SwissKitJ-Plugin-OfflinePython -am test -Dtest=BuildConfigTest`
  Expected: 编译失败（`getDepPlatforms()` 不存在）。

- [ ] **Step 3: 实现最小改动** — 在 `BuildConfig.java` 的 `Python` 内部类加 `depPlatforms` 字段。把：

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

替换为：

```java
    @Data public static class Python {
        private String version;
        /** 新增依赖的默认目标平台（不再是构建驱动；构建驱动改为 per-dep 的 depPlatforms）。 */
        private java.util.List<String> platforms = new java.util.ArrayList<>(java.util.List.of("win_amd64"));
        /** per-dependency 目标平台：normalizeName → 平台集合。key 用 DependencySpec.normalizeName。 */
        private java.util.Map<String, java.util.List<String>> depPlatforms = new java.util.LinkedHashMap<>();
        private String implementation;
        private boolean installer;
        private String executable; // null = auto-detect

        /** First selected platform (primary for estimates / single-platform display); defaults to win_amd64. */
        public String getPrimaryPlatform() {
            return platforms == null || platforms.isEmpty() ? "win_amd64" : platforms.get(0);
        }
    }
```

- [ ] **Step 4: 跑测试确认通过** — Run: `mvn -pl SwissKitJ-Plugin-OfflinePython -am test -Dtest=BuildConfigTest`
  Expected: PASS（全部用例，含原有 4 个 + 新增 2 个）。

- [ ] **Step 5: 提交** —

```bash
git add SwissKitJ-Plugin-OfflinePython/src/main/java/plugin/swisskit/offlinepython/domain/BuildConfig.java \
        SwissKitJ-Plugin-OfflinePython/src/test/java/plugin/swisskit/offlinepython/BuildConfigTest.java
git commit -m "feat(OfflinePython): BuildConfig.Python.depPlatforms per-dep platform map"
```

---

## Task 2: WheelInfo + DepsService wheel 解析

**Files:**
- Create: `src/main/java/plugin/swisskit/offlinepython/command/WheelInfo.java`
- Modify: `src/main/java/plugin/swisskit/offlinepython/command/DepsService.java`
- Test: `src/test/java/plugin/swisskit/offlinepython/DepsServiceTest.java`

- [ ] **Step 1: 新建 WheelInfo record** — 文件 `src/main/java/plugin/swisskit/offlinepython/command/WheelInfo.java`：

```java
package plugin.swisskit.offlinepython.command;

/** One installable wheel for a package, parsed from PyPI's JSON API. */
public record WheelInfo(String version, String platformTag, long sizeBytes, String filename) {}
```

- [ ] **Step 2: 写失败测试** — 在 `DepsServiceTest.java` 顶部 import 区加：

```java
import plugin.swisskit.offlinepython.command.WheelInfo;
import java.util.List;
```

在类末尾追加：

```java
    @Test
    void parsesWheelsFromPypiReleasesJson() {
        String json = """
            { "releases": {
              "1.26.4": [
                {"filename":"numpy-1.26.4-cp312-cp312-win_amd64.whl","size":19098624},
                {"filename":"numpy-1.26.4.tar.gz","size":1000}
              ],
              "1.25.2": [
                {"filename":"numpy-1.25.2-cp312-cp312-manylinux2014_x86_64.whl","size":17800000}
              ]
            }}""";
        List<WheelInfo> wheels = DepsService.parseWheels(json);
        assertEquals(2, wheels.size()); // .tar.gz 被过滤
        assertTrue(wheels.stream().anyMatch(w ->
                w.platformTag().equals("win_amd64") && w.version().equals("1.26.4")));
        assertTrue(wheels.stream().anyMatch(w ->
                w.platformTag().equals("manylinux2014_x86_64") && w.version().equals("1.25.2")));
    }

    @Test
    void parseWheelsEmptyOnGarbage() {
        assertTrue(DepsService.parseWheels("").isEmpty());
        assertTrue(DepsService.parseWheels("not json").isEmpty());
        assertTrue(DepsService.parseWheels(null).isEmpty());
    }

    @Test
    void extractPlatformTagFromWheelFilename() {
        assertEquals("win_amd64",
                DepsService.extractPlatformTag("numpy-1.26.4-cp312-cp312-win_amd64.whl"));
        assertEquals("manylinux2014_x86_64",
                DepsService.extractPlatformTag("numpy-1.26.4-cp312-cp312-manylinux2014_x86_64.whl"));
        assertEquals("any",
                DepsService.extractPlatformTag("pkg-1.0-py3-none-any.whl"));
        // 目录外标签原样透传（pip 仍接受）
        assertEquals("manylinux_2_28_x86_64",
                DepsService.extractPlatformTag("numpy-2.0-pp39-pypy39_pp73-manylinux_2_28_x86_64.whl"));
    }
```

- [ ] **Step 3: 跑测试确认失败** — Run: `mvn -pl SwissKitJ-Plugin-OfflinePython -am test -Dtest=DepsServiceTest`
  Expected: 编译失败（`parseWheels`/`extractPlatformTag` 不存在）。

- [ ] **Step 4: 实现解析** — 在 `DepsService.java` import 区加：

```java
import plugin.swisskit.offlinepython.domain.PlatformCatalog;
import java.util.ArrayList;
import java.util.List;
```

在类内（`parsePyPIWheelSize` 方法之后）追加：

```java
    /** Fetch all wheels for pkg from PyPI JSON (across releases). Empty on any failure. */
    public List<WheelInfo> searchWheels(String pkg) {
        if (pkg == null || pkg.isBlank()) return List.of();
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://pypi.org/pypi/" + pkg.trim() + "/json"))
                    .timeout(Duration.ofSeconds(6)).GET().build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            return parseWheels(resp.body());
        } catch (Exception e) {
            return List.of();
        }
    }

    /** Parse PyPI JSON into wheel entries (version/platform/size/filename). Caps at 50. */
    public static List<WheelInfo> parseWheels(String json) {
        if (json == null || json.isBlank()) return List.of();
        List<WheelInfo> out = new ArrayList<>();
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (!root.has("releases") || !root.get("releases").isJsonObject()) return List.of();
            JsonObject releases = root.getAsJsonObject("releases");
            for (String ver : releases.keySet()) {
                JsonElement arrEl = releases.get(ver);
                if (arrEl == null || !arrEl.isJsonArray()) continue;
                for (JsonElement e : arrEl.getAsJsonArray()) {
                    JsonObject o = e.getAsJsonObject();
                    String fn = o.has("filename") ? o.get("filename").getAsString() : "";
                    if (!fn.endsWith(".whl")) continue; // 跳过 sdist
                    long size = o.has("size") ? o.get("size").getAsLong() : 0L;
                    out.add(new WheelInfo(ver, extractPlatformTag(fn), size, fn));
                }
                if (out.size() >= 50) break;
            }
            return out;
        } catch (Exception ex) {
            return List.of();
        }
    }

    /** Extract the platform tag (PEP 427 末段) from a wheel filename; map to a catalog tag if exact match. */
    public static String extractPlatformTag(String filename) {
        if (filename == null) return "";
        String core = filename.endsWith(".whl")
                ? filename.substring(0, filename.length() - 4) : filename;
        String[] parts = core.split("-");
        String raw = parts.length >= 1 ? parts[parts.length - 1] : core;
        for (PlatformCatalog.Entry e : PlatformCatalog.ALL) {
            if (raw.equals(e.tag())) return e.tag();
        }
        return raw;
    }
```

- [ ] **Step 5: 跑测试确认通过** — Run: `mvn -pl SwissKitJ-Plugin-OfflinePython -am test -Dtest=DepsServiceTest`
  Expected: PASS（含原有 + 新增 3 个）。

- [ ] **Step 6: 提交** —

```bash
git add SwissKitJ-Plugin-OfflinePython/src/main/java/plugin/swisskit/offlinepython/command/WheelInfo.java \
        SwissKitJ-Plugin-OfflinePython/src/main/java/plugin/swisskit/offlinepython/command/DepsService.java \
        SwissKitJ-Plugin-OfflinePython/src/test/java/plugin/swisskit/offlinepython/DepsServiceTest.java
git commit -m "feat(OfflinePython): DepsService wheel parsing for PyPI online search"
```

---

## Task 3: 构建管线按平台集合分组下载

**Files:**
- Modify: `src/main/java/plugin/swisskit/offlinepython/infra/ProcessRunner.java`（`pipDownloadCommand`）
- Modify: `src/main/java/plugin/swisskit/offlinepython/command/BuildService.java`（`build` + `writeManifest` + 新增分组助手）
- Test: `src/test/java/plugin/swisskit/offlinepython/ProcessRunnerTest.java`（改）
- Test: `src/test/java/plugin/swisskit/offlinepython/BuildServiceTest.java`（新）
- Test: `src/test/java/plugin/swisskit/offlinepython/ManifestTest.java`（确认无需改，platforms 仍为 List）

### 3a. ProcessRunner 签名

- [ ] **Step 1: 改写 ProcessRunnerTest** — 整文件替换为：

```java
package plugin.swisskit.offlinepython;

import org.junit.jupiter.api.Test;
import plugin.swisskit.offlinepython.infra.ProcessRunner;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProcessRunnerTest {

    @Test
    void buildsPipDownloadCommandWithInlineSpecsForWindowsTarget() {
        List<String> cmd = ProcessRunner.pipDownloadCommand(
            "/usr/local/bin/python3.12", List.of("numpy==1.26.4"), "output/wheelhouse",
            List.of("win_amd64"), "3.12", "cp", true, true);
        assertEquals(List.of(
            "/usr/local/bin/python3.12", "-m", "pip", "download",
            "numpy==1.26.4",
            "-d", "output/wheelhouse",
            "--platform", "win_amd64",
            "--python-version", "3.12",
            "--implementation", "cp",
            "--only-binary=:all:"
        ), cmd);
    }

    @Test
    void acceptsMultipleInlineSpecsInOrder() {
        List<String> cmd = ProcessRunner.pipDownloadCommand(
            "python3", List.of("numpy==1.26.4", "requests==2.31.0"), "wh",
            List.of("win_amd64"), "3.12", "cp", true, true);
        int i = cmd.indexOf("numpy==1.26.4");
        int j = cmd.indexOf("requests==2.31.0");
        assertTrue(i > 0 && j > i);
    }

    @Test
    void omitsOnlyBinaryFlagWhenFalse() {
        List<String> cmd = ProcessRunner.pipDownloadCommand(
            "python3", List.of("numpy==1.26.4"), "wh", List.of("linux_x86_64"), "3.12", "cp", false, true);
        assertFalse(cmd.contains("--only-binary=:all:"));
        assertTrue(cmd.contains("--platform"));
        assertTrue(cmd.contains("linux_x86_64"));
    }

    @Test
    void emitsOnePlatformFlagPerSelectedPlatformInOrder() {
        List<String> cmd = ProcessRunner.pipDownloadCommand(
            "python3", List.of("numpy==1.26.4"), "wh",
            List.of("win_amd64", "manylinux2014_x86_64"), "3.12", "cp", true, true);
        int i = cmd.indexOf("--platform");
        assertEquals("win_amd64", cmd.get(i + 1));
        int j = i + 1 + cmd.subList(i + 1, cmd.size()).indexOf("--platform");
        assertEquals("manylinux2014_x86_64", cmd.get(j + 1));
        assertEquals(2, cmd.stream().filter("--platform"::equals).count());
    }

    @Test
    void fallsBackToAnyPlatformWhenSelectionEmpty() {
        List<String> cmd = ProcessRunner.pipDownloadCommand(
            "python3", List.of("numpy==1.26.4"), "wh", List.of(), "3.12", "cp", true, true);
        assertTrue(cmd.contains("--platform"));
        assertTrue(cmd.contains("any"));
    }

    @Test
    void addsNoDepsWhenRecursiveFalse() {
        List<String> cmd = ProcessRunner.pipDownloadCommand(
            "python3", List.of("numpy==1.26.4"), "wh", List.of("win_amd64"), "3.12", "cp", true, false);
        assertTrue(cmd.contains("--no-deps"));
    }

    @Test
    void omitsNoDepsWhenRecursiveTrue() {
        List<String> cmd = ProcessRunner.pipDownloadCommand(
            "python3", List.of("numpy==1.26.4"), "wh", List.of("win_amd64"), "3.12", "cp", true, true);
        assertFalse(cmd.contains("--no-deps"));
    }
}
```

- [ ] **Step 2: 改 ProcessRunner.pipDownloadCommand** — 在 `ProcessRunner.java` 把现有 `pipDownloadCommand` 方法整体替换为：

```java
    /** Build the platform-targeted pip download command list. Requirement specs are passed
     *  inline as positional args (pip accepts multiple). One --platform is emitted per selected
     *  platform (empty selection falls back to "any"). recursive=false adds --no-deps. */
    public static List<String> pipDownloadCommand(String python, List<String> requirementSpecs,
                                                  String destDir, List<String> platforms,
                                                  String pythonVersion, String implementation,
                                                  boolean onlyBinary, boolean recursive) {
        List<String> cmd = new ArrayList<>();
        cmd.add(python);
        cmd.addAll(List.of("-m", "pip", "download"));
        if (requirementSpecs != null) cmd.addAll(requirementSpecs);
        cmd.addAll(List.of("-d", destDir));
        List<String> plats = (platforms == null || platforms.isEmpty()) ? List.of("any") : platforms;
        for (String p : plats) cmd.addAll(List.of("--platform", p));
        cmd.addAll(List.of("--python-version", pythonVersion, "--implementation", implementation));
        if (onlyBinary) cmd.add("--only-binary=:all:");
        if (!recursive) cmd.add("--no-deps");
        return cmd;
    }
```

（`run`/`cancel`/`captureQuiet` 不变。）

- [ ] **Step 3: 跑 test-compile 确认编译失败（红）** — Run: `mvn -pl SwissKitJ-Plugin-OfflinePython -am test-compile`
  Expected: 编译失败 —— `BuildService.build()` 仍调用旧的 `pipDownloadCommand(..., String requirements, ...)` 7 参签名。3b 改完 BuildService 后再统一验证（见 Step 8），此步仅确认红态。

### 3b. BuildService 分组下载 + 新增 BuildServiceTest

- [ ] **Step 4: 写分组失败测试** — 新建 `src/test/java/plugin/swisskit/offlinepython/command/BuildServiceTest.java`（放 `command` 包，以访问 `BuildService` 的包级私有 `DepGroup`/`groupByPlatform`/`unionPlatforms`）：

```java
package plugin.swisskit.offlinepython.command;

import org.junit.jupiter.api.Test;
import plugin.swisskit.offlinepython.domain.DependencySpec;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BuildServiceTest {

    @Test
    void groupsDepsByPlatformSet() {
        List<DependencySpec> deps = List.of(
                new DependencySpec("numpy", "==1.26.4", null),
                new DependencySpec("requests", "==2.31.0", null),
                new DependencySpec("flask", "==3.0.0", null));
        Map<String, List<String>> depPlatforms = new java.util.LinkedHashMap<>();
        depPlatforms.put("numpy", List.of("win_amd64"));
        depPlatforms.put("requests", List.of("manylinux2014_x86_64"));
        depPlatforms.put("flask", List.of("win_amd64")); // 与 numpy 同集合 → 同组

        List<BuildService.DepGroup> groups =
                BuildService.groupByPlatform(deps, depPlatforms, List.of("win_amd64"));
        assertEquals(2, groups.size());
        BuildService.DepGroup winGroup = groups.stream()
                .filter(g -> g.platforms.equals(List.of("win_amd64"))).findFirst().orElseThrow();
        assertTrue(winGroup.specs.contains("numpy==1.26.4"));
        assertTrue(winGroup.specs.contains("flask==3.0.0"));
        assertEquals(2, winGroup.specs.size());
    }

    @Test
    void fallsBackToDefaultPlatformWhenDepMissing() {
        List<DependencySpec> deps = List.of(new DependencySpec("numpy", "==1.26.4", null));
        List<BuildService.DepGroup> groups =
                BuildService.groupByPlatform(deps, Map.of(), List.of("manylinux2014_x86_64"));
        assertEquals(1, groups.size());
        assertEquals(List.of("manylinux2014_x86_64"), groups.get(0).platforms);
    }

    @Test
    void normalizesDepNameForKeyLookup() {
        // requirements 里写 "Pillow"，depPlatforms key 是 normalize 后的 "pillow"
        List<DependencySpec> deps = List.of(new DependencySpec("Pillow", "==10.0.0", null));
        Map<String, List<String>> depPlatforms = new java.util.LinkedHashMap<>();
        depPlatforms.put("pillow", List.of("win_amd64"));
        List<BuildService.DepGroup> groups =
                BuildService.groupByPlatform(deps, depPlatforms, List.of("any"));
        assertEquals(List.of("win_amd64"), groups.get(0).platforms);
    }

    @Test
    void unionPlatformsDedupesPreservingOrder() {
        List<BuildService.DepGroup> groups = List.of(
                new BuildService.DepGroup(List.of("win_amd64")),
                new BuildService.DepGroup(List.of("manylinux2014_x86_64", "win_amd64")));
        assertEquals(List.of("win_amd64", "manylinux2014_x86_64"),
                BuildService.unionPlatforms(groups));
    }
}
```

- [ ] **Step 5: 跑测试确认失败** — Run: `mvn -pl SwissKitJ-Plugin-OfflinePython -am test -Dtest=BuildServiceTest`
  Expected: 编译失败（`BuildService.DepGroup`/`groupByPlatform`/`unionPlatforms` 不存在）。

- [ ] **Step 6: 改 BuildService** — 在 `BuildService.java` 把 import 区替换为：

```java
import plugin.swisskit.offlinepython.domain.BuildConfig;
import plugin.swisskit.offlinepython.domain.BuildSummary;
import plugin.swisskit.offlinepython.domain.DependencySpec;
import plugin.swisskit.offlinepython.domain.Manifest;
import plugin.swisskit.offlinepython.domain.RequirementsFile;
import plugin.swisskit.offlinepython.domain.WheelEntry;
import plugin.swisskit.offlinepython.infra.HashUtil;
import plugin.swisskit.offlinepython.infra.JsonStore;
import plugin.swisskit.offlinepython.infra.ProcessRunner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.stream.Stream;
```

把 `build(...)` 方法整体替换为：

```java
    /** @return a BuildSummary describing the build outcome (wheels, cache hits, size, duration). */
    public BuildSummary build(
            Path projectDir, BuildConfig cfg, String pythonExecutable,
            Consumer<String> onLog, ProcessRunner runner) throws Exception {
        Path output = projectDir.resolve(cfg.getRepository().getOutput());
        Path wheelhouse = output.resolve(cfg.getRepository().getWheelDir());
        Files.createDirectories(wheelhouse);

        int preExisting = countWheels(wheelhouse);
        List<DependencySpec> deps = readRequirements(projectDir);
        List<DepGroup> groups = groupByPlatform(deps,
                cfg.getPython().getDepPlatforms(), cfg.getPython().getPlatforms());
        boolean onlyBinary = cfg.getDownload().isOnlyBinary();
        boolean recursive = cfg.getDownload().isRecursive();

        long start = System.currentTimeMillis();
        for (DepGroup g : groups) {
            List<String> cmd = ProcessRunner.pipDownloadCommand(
                    pythonExecutable, g.specs, wheelhouse.toString(), g.platforms,
                    majorMinor(cfg.getPython().getVersion()), cfg.getPython().getImplementation(),
                    onlyBinary, recursive);
            onLog.accept("$ " + String.join(" ", cmd));
            int code = runner.run(cmd, onLog);
            if (code != 0) {
                long duration = System.currentTimeMillis() - start;
                return new BuildSummary(preExisting, preExisting, 0L, duration);
            }
        }
        long duration = System.currentTimeMillis() - start;
        writeManifest(projectDir, cfg, output, wheelhouse, unionPlatforms(groups));
        writeSha256Sums(output);
        return BuildSummary.compute(wheelhouse, preExisting, duration);
    }
```

把 `writeManifest(...)` 方法整体替换为（新增 `platforms` 入参，manifest 写传入的并集；reqNames 改用 `readRequirements`）：

```java
    void writeManifest(Path projectDir, BuildConfig cfg, Path output, Path wheelhouse,
                       List<String> platforms) throws IOException {
        List<WheelEntry> wheels = new ArrayList<>();
        List<String> reqNames = new ArrayList<>();
        for (DependencySpec d : readRequirements(projectDir)) reqNames.add(d.toString());

        try (Stream<Path> files = Files.list(wheelhouse)) {
            List<Path> sorted = files.sorted().toList();
            for (Path f : sorted) {
                if (!f.toString().endsWith(".whl")) continue;
                String name = wheelNamePart(f.getFileName().toString());
                String normName = DependencySpec.normalizeName(name);
                boolean required = reqNames.stream()
                        .anyMatch(r -> DependencySpec.normalizeName(r).startsWith(normName));
                wheels.add(new WheelEntry(
                        name, "", output.relativize(f).toString().replace('\\', '/'),
                        HashUtil.sha256Hex(f), Files.size(f), required));
            }
        }

        Manifest m = new Manifest();
        m.setSchemaVersion(1);
        m.getPython().setVersion(cfg.getPython().getVersion());
        m.getPython().setPlatforms(new ArrayList<>(platforms));
        m.setBuiltAt(java.time.OffsetDateTime.now().toString());
        m.setBuiltOn(System.getProperty("user.name"));
        m.setToolVersion("1.0.0");
        m.getWheels().addAll(wheels);
        m.getRequirements().addAll(reqNames);
        JsonStore.save(m, output.resolve("manifest.json"));
    }
```

在 `wheelNamePart(...)` 方法之前插入分组助手（`DepGroup` + 4 个 static 方法）：

```java
    /** A build group: deps sharing one target-platform set, run in a single pip download. */
    static final class DepGroup {
        final List<String> platforms;
        final List<String> specs = new ArrayList<>();
        DepGroup(List<String> platforms) { this.platforms = platforms; }
    }

    /** Read requirements.txt into DependencySpecs (empty list if absent). */
    static List<DependencySpec> readRequirements(Path projectDir) throws IOException {
        Path reqs = projectDir.resolve("requirements.txt");
        if (!Files.exists(reqs)) return List.of();
        return RequirementsFile.parse(Files.readString(reqs));
    }

    /** Partition deps by their resolved platform set. Each dep's platforms come from
     *  depPlatforms[normalizeName(name)], falling back to defaultPlatforms. Pure, unit-tested. */
    static List<DepGroup> groupByPlatform(List<DependencySpec> deps,
                                          Map<String, List<String>> depPlatforms,
                                          List<String> defaultPlatforms) {
        Map<String, DepGroup> groups = new LinkedHashMap<>();
        for (DependencySpec d : deps) {
            List<String> plats = resolvePlatforms(d.name(), depPlatforms, defaultPlatforms);
            String key = platformKey(plats);
            DepGroup g = groups.computeIfAbsent(key, k -> new DepGroup(new ArrayList<>(plats)));
            g.specs.add(d.toString());
        }
        return new ArrayList<>(groups.values());
    }

    static List<String> resolvePlatforms(String name, Map<String, List<String>> depPlatforms,
                                         List<String> defaultPlatforms) {
        String norm = DependencySpec.normalizeName(name);
        if (depPlatforms != null && depPlatforms.containsKey(norm)) {
            List<String> p = depPlatforms.get(norm);
            if (p != null && !p.isEmpty()) return p;
        }
        return (defaultPlatforms == null || defaultPlatforms.isEmpty())
                ? List.of("win_amd64") : defaultPlatforms;
    }

    /** Stable key for a platform set (dedup + sort). */
    static String platformKey(List<String> plats) {
        return String.join(",", new TreeSet<>(plats));
    }

    /** Union of all group platforms, deduped, insertion-ordered. */
    static List<String> unionPlatforms(List<DepGroup> groups) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (DepGroup g : groups) seen.addAll(g.platforms);
        return new ArrayList<>(seen);
    }

```

（`countWheels`/`writeSha256Sums`/`wheelNamePart`/`majorMinor` 不变。）

- [ ] **Step 7: 跑 BuildServiceTest 确认通过** — Run: `mvn -pl SwissKitJ-Plugin-OfflinePython -am test -Dtest=BuildServiceTest`
  Expected: PASS（4 个用例）。

- [ ] **Step 8: 跑全部测试确认整体绿** — Run: `mvn -pl SwissKitJ-Plugin-OfflinePython -am test`
  Expected: PASS（含改过的 ProcessRunnerTest、BuildConfigTest、DepsServiceTest，以及未受影响的 ManifestTest/其余）。`BuildPanel` 仍用 `getPrimaryPlatform()`（保留），编译通过。

- [ ] **Step 9: 提交** —

```bash
git add SwissKitJ-Plugin-OfflinePython/src/main/java/plugin/swisskit/offlinepython/infra/ProcessRunner.java \
        SwissKitJ-Plugin-OfflinePython/src/main/java/plugin/swisskit/offlinepython/command/BuildService.java \
        SwissKitJ-Plugin-OfflinePython/src/test/java/plugin/swisskit/offlinepython/ProcessRunnerTest.java \
        SwissKitJ-Plugin-OfflinePython/src/test/java/plugin/swisskit/offlinepython/command/BuildServiceTest.java
git commit -m "feat(OfflinePython): per-dep platform grouped pip download + recursive flag"
```

---

## Task 4: PyPISearchDialog 在线搜索对话框

**Files:**
- Create: `src/main/java/plugin/swisskit/offlinepython/ui/dialog/PyPISearchDialog.java`

JavaFX 节点无单测框架；解析逻辑已由 Task 2 的 `DepsServiceTest` 覆盖，本任务靠 DevLauncher 手动验证。

- [ ] **Step 1: 新建对话框** — 文件 `src/main/java/plugin/swisskit/offlinepython/ui/dialog/PyPISearchDialog.java`：

```java
package plugin.swisskit.offlinepython.ui.dialog;

import fan.summer.api.component.GlassNotification;
import fan.summer.api.component.UiUtils;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import plugin.swisskit.offlinepython.command.DepsService;
import plugin.swisskit.offlinepython.command.WheelInfo;
import plugin.swisskit.offlinepython.ui.OpbStyle;

import java.util.List;
import java.util.Optional;

/** Modal PyPI wheel search: type a package name, list its wheels, pick one to return. */
public class PyPISearchDialog {

    private final DepsService deps = new DepsService();
    private final Stage stage = new Stage();
    private final TextField query = new TextField();
    private final TableView<WheelInfo> table = new TableView<>();
    private WheelInfo chosen;

    public PyPISearchDialog(Window owner) {
        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("PyPI 在线搜索");
        buildUi();
    }

    private void buildUi() {
        query.setStyle(UiUtils.fieldStyle());
        query.setPromptText("输入包名，如 numpy");
        Button search = UiUtils.glassBtn("搜索", false);
        search.setOnAction(e -> doSearch());
        query.setOnAction(e -> doSearch());
        HBox bar = new HBox(8, query, search);
        HBox.setHgrow(query, javafx.scene.layout.Priority.ALWAYS);

        TableColumn<WheelInfo, String> cVer = col("版本", WheelInfo::version);
        TableColumn<WheelInfo, String> cPlat = col("平台", WheelInfo::platformTag);
        TableColumn<WheelInfo, String> cSize = col("大小", w -> human(w.sizeBytes()));
        TableColumn<WheelInfo, String> cFn = col("文件名", WheelInfo::filename);
        table.getColumns().addAll(cVer, cPlat, cSize, cFn);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.setPrefHeight(320);
        table.setPlaceholder(new Label("输入包名并搜索"));

        Button ok = UiUtils.glassBtn("确定", true);
        ok.setDisable(true);
        ok.setOnAction(e -> { chosen = table.getSelectionModel().getSelectedItem(); stage.close(); });
        Button cancel = UiUtils.glassBtn("取消", false);
        cancel.setOnAction(e -> stage.close());
        table.getSelectionModel().selectedItemProperty().addListener((o, ov, nv) -> ok.setDisable(nv == null));

        HBox actions = new HBox(8, cancel, ok);
        VBox root = new VBox(10, bar, table, actions);
        root.setPadding(new Insets(16));
        root.setStyle(OpbStyle.card());
        stage.setScene(new Scene(root, 600, 460));
    }

    private static TableColumn<WheelInfo, String> col(String title, java.util.function.Function<WheelInfo, String> get) {
        TableColumn<WheelInfo, String> c = new TableColumn<>(title);
        c.setCellValueFactory(cb -> new SimpleStringProperty(get.apply(cb.getValue())));
        return c;
    }

    private void doSearch() {
        String pkg = query.getText().trim();
        if (pkg.isBlank()) {
            GlassNotification.toast(table, GlassNotification.Type.WARNING, "请输入包名");
            return;
        }
        table.setPlaceholder(new Label("查询中…"));
        table.getItems().clear();
        new Thread(() -> {
            List<WheelInfo> result = deps.searchWheels(pkg);
            Platform.runLater(() -> {
                if (result.isEmpty()) {
                    table.setPlaceholder(new Label("未找到 wheel（包名不存在或无 wheel）"));
                    GlassNotification.toast(table, GlassNotification.Type.WARNING, "未找到 wheel");
                } else {
                    table.getItems().setAll(result);
                }
            });
        }, "opb-pypi-search").start();
    }

    /** The package name the user searched for (trimmed query). */
    public String packageName() { return query.getText().trim(); }

    /** Show modal; return the chosen wheel (empty if cancelled). */
    public Optional<WheelInfo> showAndWait() {
        stage.showAndWait();
        return Optional.ofNullable(chosen);
    }

    private static String human(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }
}
```

- [ ] **Step 2: 编译确认** — Run: `mvn -pl SwissKitJ-Plugin-OfflinePython -am test-compile`
  Expected: BUILD SUCCESS（无编译错误）。

- [ ] **Step 3: 手动验证（DevLauncher）** — 在 IDEA 运行 `plugin.swisskit.offlinepython.DevLauncher`，打开 OfflinePython 插件窗口（依赖面板在第 5 任务的改动后才会接上按钮；本步先确认 `PyPISearchDialog` 编译无误即可，端到端验证放在 Task 5 Step 4）。

- [ ] **Step 4: 提交** —

```bash
git add SwissKitJ-Plugin-OfflinePython/src/main/java/plugin/swisskit/offlinepython/ui/dialog/PyPISearchDialog.java
git commit -m "feat(OfflinePython): PyPISearchDialog — modal PyPI wheel search"
```

---

## Task 5: DepsPanel 三行布局 + per-dep 平台 + 主从编辑

**Files:**
- Modify: `src/main/java/plugin/swisskit/offlinepython/ui/panel/DepsPanel.java`（整文件重写）

JavaFX 面板无单测；靠 DevLauncher 手动验证（Step 4）。纯逻辑（分组/解析/配置序列化）已由前置任务覆盖。

- [ ] **Step 1: 整文件重写 DepsPanel.java** — 替换为：

```java
package plugin.swisskit.offlinepython.ui.panel;

import fan.summer.api.component.GlassNotification;
import fan.summer.api.component.UiUtils;
import fan.summer.api.i18n.I18n;
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
import plugin.swisskit.offlinepython.domain.PlatformCatalog;
import plugin.swisskit.offlinepython.domain.RequirementsFile;
import plugin.swisskit.offlinepython.ui.LogConsole;
import plugin.swisskit.offlinepython.ui.OpbStyle;
import plugin.swisskit.offlinepython.ui.ProjectContext;
import plugin.swisskit.offlinepython.ui.control.PlatformMultiSelect;
import plugin.swisskit.offlinepython.ui.dialog.PyPISearchDialog;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DepsPanel extends CommandPanel {

    /** Editable row backing the table: name/version/size + per-row target platforms. */
    public static class Row {
        public final javafx.beans.property.SimpleStringProperty name = new javafx.beans.property.SimpleStringProperty();
        public final javafx.beans.property.SimpleStringProperty version = new javafx.beans.property.SimpleStringProperty();
        public final javafx.beans.property.SimpleStringProperty size = new javafx.beans.property.SimpleStringProperty("—");
        public final List<String> platforms = new ArrayList<>(List.of("win_amd64"));
        public Row(String n, String v, List<String> plats) {
            name.set(n); version.set(v);
            if (plats != null && !plats.isEmpty()) { platforms.clear(); platforms.addAll(plats); }
        }
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

    // 行2 表单：当前正在新增/编辑的这条依赖
    private final TextField nField = new TextField();
    private final TextField vField = new TextField();
    private final PlatformMultiSelect platformSelect = new PlatformMultiSelect();
    private long pendingSize = 0L; // 在线搜索带回的 wheel 大小，提交时写入行

    public DepsPanel(LogConsole log, ProjectContext project) {
        super(log, project);
        recursive.setSelected(true); wheelFirst.setSelected(true);
        buildUi();
        loadFromProject();
    }

    @SuppressWarnings("unchecked")
    private void buildUi() {
        getChildren().add(titleNode());

        // --- 行1：导入（独占一行） ---
        Button imp = UiUtils.glassBtn("导入 requirements.txt", false);
        imp.setTooltip(new Tooltip("选择本地 requirements.txt 并解析为依赖表"));
        imp.setOnAction(e -> doImport());
        HBox row1 = new HBox(8, imp);

        // --- 行2：包名 / 版本 / 目标平台（per-dep） ---
        nField.setStyle(UiUtils.fieldStyle()); nField.setPromptText("包名");
        vField.setStyle(UiUtils.fieldStyle()); vField.setPromptText("版本 (如 ==1.26.4)");
        HBox row2 = new HBox(8, labeled("包名", nField), labeled("版本", vField), platformBox());
        HBox.setHgrow(nField, Priority.ALWAYS);

        // --- 行3：在线搜索 / 保存配置 ---
        Button search = UiUtils.glassBtn("🔍 在线搜索", false);
        search.setTooltip(new Tooltip("从 PyPI 在线搜索该包的 wheel，选中后回填包名/版本/平台"));
        search.setOnAction(e -> doSearch());
        Button save = UiUtils.glassBtn("保存配置", true);
        save.setTooltip(new Tooltip("将当前包名/版本/平台写入依赖表并保存配置"));
        save.setOnAction(e -> doSave(false));
        Region spring3 = new Region(); HBox.setHgrow(spring3, Priority.ALWAYS);
        HBox row3 = new HBox(8, search, spring3, save);

        // --- 表格列 ---
        TableColumn<Row, String> cName = textCol("包名", 1.4, r -> r.name.get(),
                OpbStyle.tableCellStyle(OpbStyle.TEXT_PRIMARY, true, false));
        TableColumn<Row, String> cVer = textCol("版本约束", 1.0, r -> r.version.get(),
                OpbStyle.tableCellStyle(OpbStyle.TEXT_SECONDARY, false, true));
        TableColumn<Row, String> cPlat = perRowPlatformCol();
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
        table.setRowFactory(tv -> new javafx.scene.control.TableRow<>() {
            @Override protected void updateItem(Row r, boolean empty) {
                super.updateItem(r, empty);
                setStyle(empty || r == null ? "" : OpbStyle.tableRowStyle(getIndex() % 2 == 1, isSelected()));
            }
        });
        // 主从编辑：选中行 → 载入表单；清空 → 重置为新增态
        table.getSelectionModel().selectedItemProperty().addListener((o, ov, nv) -> loadForm(nv));

        // --- 选项 ---
        HBox opts = new HBox(18, recursive, wheelFirst, upgradePip);
        opts.setStyle("-fx-text-fill: " + OpbStyle.TEXT_SECONDARY + ";");

        // --- 底栏：摘要 + 保存并去构建 ---
        Button saveBuild = UiUtils.glassBtn("保存并去构建 →", true);
        saveBuild.setTooltip(new Tooltip("保存当前依赖与配置后跳转构建面板"));
        saveBuild.setOnAction(e -> doSave(true));
        HBox summaryBar = new HBox(14, summary, spacer(), saveBuild);
        summaryBar.setStyle(OpbStyle.card() + " -fx-padding: 10 14 10 14;");
        HBox.setHgrow(summaryBar, Priority.ALWAYS);

        VBox tableBox = new VBox(6, table);
        getChildren().addAll(row1, row2, row3, tableBox, opts, summaryBar);
    }

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

    /** 目标平台列：只读，显示该行自带的平台汇总。 */
    private TableColumn<Row, String> perRowPlatformCol() {
        TableColumn<Row, String> c = new TableColumn<>("目标平台");
        c.setPrefWidth(150);
        c.setStyle(OpbStyle.tableHeaderStyle());
        c.setCellFactory(tc -> new TableCell<>() {
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                if (empty) { setText(null); setStyle(""); return; }
                int idx = getIndex();
                List<Row> items = getTableView().getItems();
                if (idx < 0 || idx >= items.size()) { setText(null); return; }
                setText(PlatformCatalog.summary(items.get(idx).platforms));
                setStyle(OpbStyle.tableCellStyle(WHITE, false, false));
            }
        });
        return c;
    }

    private Label spacer() { Label s = new Label(); HBox.setHgrow(s, Priority.ALWAYS); return s; }

    private HBox labeled(String text, TextField f) {
        HBox h = new HBox(6, UiUtils.subLabel(text), f); HBox.setHgrow(f, Priority.ALWAYS); return h;
    }

    private HBox platformBox() {
        return new HBox(6, UiUtils.subLabel("目标平台"), platformSelect);
    }

    /** 载入行到表单（编辑态）；null → 重置为新增态。 */
    private void loadForm(Row r) {
        if (r == null) {
            nField.clear();
            vField.clear();
            platformSelect.setSelected(defaultPlatforms());
            pendingSize = 0L;
        } else {
            nField.setText(r.name.get());
            vField.setText(r.version.get());
            platformSelect.setSelected(r.platforms);
            pendingSize = 0L;
        }
    }

    private List<String> defaultPlatforms() {
        return (project.getConfig() != null && project.getConfig().getPython() != null
                && project.getConfig().getPython().getPlatforms() != null)
                ? project.getConfig().getPython().getPlatforms() : List.of("win_amd64");
    }

    private void loadFromProject() {
        Path dir = project.getProjectDir();
        if (dir == null) return;
        try {
            Path req = dir.resolve("requirements.txt");
            List<Row> rows = new ArrayList<>();
            if (Files.exists(req)) {
                Map<String, List<String>> dp = (project.getConfig() != null && project.getConfig().getPython() != null)
                        ? project.getConfig().getPython().getDepPlatforms() : new LinkedHashMap<>();
                List<String> defaults = defaultPlatforms();
                for (DependencySpec d : RequirementsFile.parse(Files.readString(req))) {
                    rows.add(new Row(d.name(), d.versionSpec(),
                            dp.getOrDefault(DependencySpec.normalizeName(d.name()), defaults)));
                }
            }
            table.getItems().setAll(rows);
            table.refresh();
            refreshSummary();
        } catch (Exception e) {
            log.log("加载 requirements 失败: " + e.getMessage());
        }
    }

    private void doImport() {
        FileChooser fc = new FileChooser();
        File f = fc.showOpenDialog(getScene().getWindow());
        if (f == null) return;
        try {
            Map<String, List<String>> dp = (project.getConfig() != null && project.getConfig().getPython() != null)
                    ? project.getConfig().getPython().getDepPlatforms() : new LinkedHashMap<>();
            List<String> defaults = defaultPlatforms();
            List<Row> rows = new ArrayList<>();
            for (DependencySpec d : RequirementsFile.parse(Files.readString(f.toPath()))) {
                rows.add(new Row(d.name(), d.versionSpec(),
                        dp.getOrDefault(DependencySpec.normalizeName(d.name()), defaults)));
            }
            table.getItems().setAll(rows);
            table.refresh();
            refreshSummary();
            GlassNotification.toast(this, GlassNotification.Type.SUCCESS, "已导入 requirements.txt");
        } catch (Exception e) {
            GlassNotification.toast(this, GlassNotification.Type.ERROR, "导入失败");
        }
    }

    private void doSearch() {
        PyPISearchDialog dlg = new PyPISearchDialog(getScene().getWindow());
        dlg.showAndWait().ifPresent(w -> {
            nField.setText(dlg.packageName());
            vField.setText("==" + w.version());
            platformSelect.setSelected(List.of(w.platformTag()));
            pendingSize = w.sizeBytes();
        });
    }

    /** 提交表单（更新选中行或新增）并持久化；thenBuild=true 再跳转构建。 */
    private void doSave(boolean thenBuild) {
        Path dir = project.getProjectDir();
        if (dir == null) { GlassNotification.toast(this, GlassNotification.Type.WARNING, "先打开或新建项目"); return; }
        String name = nField.getText().trim();
        boolean committed = false;
        if (!name.isBlank()) {
            String ver = vField.getText().trim();
            List<String> plats = new ArrayList<>(platformSelect.getSelected());
            Row sel = table.getSelectionModel().getSelectedItem();
            if (sel != null) {
                sel.name.set(name); sel.version.set(ver);
                sel.platforms.clear(); sel.platforms.addAll(plats);
                sel.size.set(pendingSize > 0 ? humanSize(pendingSize) : "—");
            } else {
                Row nr = new Row(name, ver, plats);
                nr.size.set(pendingSize > 0 ? humanSize(pendingSize) : "—");
                table.getItems().add(nr);
            }
            pendingSize = 0L;
            table.refresh();
            committed = true;
        }
        try {
            persist(dir);
            GlassNotification.toast(this, GlassNotification.Type.SUCCESS,
                    committed ? (table.getSelectionModel().getSelectedItem() != null ? "已更新依赖" : "已添加依赖") : "已保存配置");
            log.log("已保存 " + table.getItems().size() + " 条依赖");
            if (thenBuild) fireEventBuildNav();
        } catch (Exception e) {
            log.log("保存失败: " + e.getMessage());
            GlassNotification.toast(this, GlassNotification.Type.ERROR, "保存失败");
        }
    }

    private void persist(Path dir) throws Exception {
        List<DependencySpec> specs = new ArrayList<>();
        for (Row r : table.getItems()) specs.add(new DependencySpec(r.name.get(), r.version.get(), null));
        Files.writeString(dir.resolve("requirements.txt"), RequirementsFile.write(specs));
        if (project.getConfig() != null) {
            project.getConfig().getDownload().setRecursive(recursive.isSelected());
            project.getConfig().getDownload().setOnlyBinary(wheelFirst.isSelected());
            project.getConfig().getDownload().setUpgradePip(upgradePip.isSelected());
            Map<String, List<String>> dp = project.getConfig().getPython().getDepPlatforms();
            dp.clear();
            for (Row r : table.getItems()) {
                dp.put(DependencySpec.normalizeName(r.name.get()), new ArrayList<>(r.platforms));
            }
            project.saveConfig();
        }
        refreshSummary();
    }

    private void fireEventBuildNav() {
        if (getScene() != null) getScene().getRoot().fireEvent(
                new plugin.swisskit.offlinepython.ui.NavEvent("build"));
    }

    private void refreshSummary() {
        summary.setText("直接 " + table.getItems().size() + " 个依赖（平台按各自目标）");
    }

    private static String humanSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }

    @Override public String title() { return I18n.get("opb.deps.title"); }
}
```

- [ ] **Step 2: 编译确认** — Run: `mvn -pl SwissKitJ-Plugin-OfflinePython -am test-compile`
  Expected: BUILD SUCCESS。

- [ ] **Step 3: 跑全部测试确认未回归** — Run: `mvn -pl SwissKitJ-Plugin-OfflinePython -am test`
  Expected: PASS（DepsPanel 无单测；其余不受影响）。

- [ ] **Step 4: 手动验证（DevLauncher）** — 在 IDEA 运行 `plugin.swisskit.offlinepython.DevLauncher`，打开依赖配置面板，逐项确认：

  - **布局**：导入按钮独占第一行；包名/版本/目标平台在同一行；在线搜索 + 保存配置在同一行；表格/选项/底栏正常。
  - **手动新增**：包名 `requests`、版本 `==2.31.0`、目标平台勾选 `win_amd64` → 保存配置 → 表格新增一行，目标平台列显示 `win_amd64`；`config.json` 的 `python.depPlatforms` 含 `"requests":["win_amd64"]`，`requirements.txt` 含 `requests==2.31.0`。
  - **per-dep 平台**：再加 `numpy` 选 `manylinux2014_x86_64` → 两行平台不同；`config.json` 两个 key 各自平台正确。
  - **主从编辑**：选中 `requests` 行 → 行2表单载入其值；改版本 → 保存配置 → 该行更新（非新增）。
  - **在线搜索**：点🔍在线搜索 → 弹窗输 `numpy` → 搜索 → 列出 wheel（版本/平台/大小/文件名）→ 选一条 win_amd64 → 确定 → 行2回填 包名=numpy、版本==<选中版本>、平台=win_amd64、（保存后）预估大小非 `—`。
  - **构建**：保存并去构建 → 跳构建面板；启动构建 → 日志按平台集合分组出现多条 `$ pip download <specs…> --platform …`；`manifest.json` 的 `python.platforms` 为所有依赖平台的并集。

- [ ] **Step 5: 提交** —

```bash
git add SwissKitJ-Plugin-OfflinePython/src/main/java/plugin/swisskit/offlinepython/ui/panel/DepsPanel.java
git commit -m "feat(OfflinePython): DepsPanel v2 — 3-row layout, per-dep platform, master-detail, online search"
```

---

## 完成标准

- `mvn -pl SwissKitJ-Plugin-OfflinePython -am test` 全绿。
- DevLauncher 手动验证（Task 5 Step 4）全部通过。
- 5 个提交（Task 1–5 各一），每个提交本身可编译、相关测试通过。

## 已知限制（设计 spec §8 / §9）

- 跨组（不同平台集合）传递依赖各自独立解析，wheelhouse 内 pip 按文件名去重；跨组版本冲突不做统一。
- 目录外的 wheel 平台标签（如 `manylinux_2_28_x86_64`）原样作为该依赖平台写入，pip 仍接受。
- `python.platforms` 仅作"新增依赖默认平台"，UI 不再改写。
