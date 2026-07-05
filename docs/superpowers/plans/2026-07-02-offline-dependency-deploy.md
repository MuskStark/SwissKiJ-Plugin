# 离线依赖部署功能 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 OfflinePython 模块能把构建产物打包成 ZIP,并在离线机上加载 ZIP、按本机平台筛选、pip install 到全局或虚拟环境。

**Architecture:** 打包端在现有 `output/` 之上加一层 `PackageService` → bundle ZIP。部署端新增独立 nav 页 `DeployPanel`,只认 ZIP + 本机 Python,通过纯逻辑的 `PlatformMatcher` 筛选 wheel,再用现有 `ProcessRunner` 跑 `pip install`。不触碰 BuildService/VerifyService 核心逻辑。

**Tech Stack:** Java 21,JavaFX 21,Lombok,Gson,JDK `java.util.zip`,JUnit 5。

**Spec:** `docs/superpowers/specs/2026-07-02-offline-dependency-deploy-design.md`

## Global Constraints

- Java 21(`maven.compiler.source/target=21`),JavaFX 21.0.2(均 provided)。
- 仅用现有依赖(gson 2.11.0 / lombok),禁止新增第三方依赖;ZIP 用 JDK `java.util.zip`。
- 模块根: `SwissKitJ-Plugin-OfflinePython/`,源码根 `src/main/java/plugin/swisskit/offlinepython/`,测试根 `src/test/java/plugin/swisskit/offlinepython/`。
- 包名约定: `domain/` 纯逻辑(可单测,无 FX)、`command/` 服务(可注入 ProcessRunner)、`ui/panel/` JavaFX 面板。
- 构建命令: 在 `SwissKitJ-Plugin-OfflinePython/` 下 `mvn -q test`(或 `-pl SwissKitJ-Plugin-OfflinePython -am test` 从父 pom)。
- 现有约定: BuildConfig 用 Lombok `@Data` + 静态内部类;wheelhouse 路径 = `output/wheelhouse/<pythonVersion>/*.whl`;manifest 在 `output/manifest.json`。
- i18n 文件: `src/main/resources/i18n/messages_zh.properties` + `messages.properties`(英文),key 前缀 `opb.`。

---

## File Structure

| 文件 | 类型 | 责任 |
|---|---|---|
| `domain/PlatformMatcher.java` | 新 | 纯逻辑:本机平台检测 + wheel 兼容匹配 |
| `domain/BundleReader.java` | 新 | 纯逻辑:从 ZIP 读 manifest + wheel 列表 |
| `domain/DeployTarget.java` | 新 | 密封接口:Global / Venv |
| `domain/DeployResult.java` | 新 | record:install 结果 |
| `domain/BuildConfig.java` | 改 | 新增 `Bundle` 内部类 + `bundle` 字段 |
| `command/PackageService.java` | 新 | 打包 output/ → bundle.zip |
| `command/DeployService.java` | 新 | 解压 + 筛选 + 逐包 pip install |
| `ui/panel/DeployPanel.java` | 新 | 部署页 UI |
| `ui/panel/BuildVerifyPanel.java` | 改 | 构建后挂「打包」按钮 |
| `ui/panel/ConfigPanel.java` | 改 | 加「构建后自动打包」勾选 |
| `ui/CommandShell.java` | 改 | 加 deploy nav |
| `i18n/messages*.properties` | 改 | 新 key |

任务依赖顺序: Task 1 (PlatformMatcher) → Task 2 (BundleReader) → Task 3 (BuildConfig.Bundle + PackageService) → Task 4 (DeployService) → Task 5 (i18n + CommandShell nav) → Task 6 (DeployPanel) → Task 7 (ConfigPanel + BuildVerifyPanel 集成) → Task 8 (端到端冒烟)。

---

### Task 1: PlatformMatcher(纯逻辑)

**Files:**
- Create: `src/main/java/plugin/swisskit/offlinepython/domain/PlatformMatcher.java`
- Test: `src/test/java/plugin/swisskit/offlinepython/domain/PlatformMatcherTest.java`

**Interfaces:**
- Produces:
  - `record HostTags(String os, String arch, String pythonVersion, List<String> compatibleTags)`
  - `static HostTags detectHost(String pythonVersion)` — 用 `System.getProperty("os.name")` + `os.arch` 推导
  - `static List<WheelEntry> match(HostTags host, List<WheelEntry> wheels)` — 返回适配子集
  - `static String incompatReason(HostTags host, WheelEntry wheel)` — 不兼容原因(null=兼容),供 UI 显示

- [ ] **Step 1: Write failing tests**

Create `src/test/java/plugin/swisskit/offlinepython/domain/PlatformMatcherTest.java`:

```java
package plugin.swisskit.offlinepython.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PlatformMatcherTest {

    private WheelEntry wheel(String file) {
        return new WheelEntry("x", "1.0", file, null, 0, true);
    }

    @Test
    void purePythonAnyWheelAlwaysMatches() {
        PlatformMatcher.HostTags host = new PlatformMatcher.HostTags(
                "win", "x64", "3.12", List.of("win_amd64", "win32", "any"));
        var wheels = List.of(wheel("requests-2.31.0-py3-none-any.whl"));
        List<WheelEntry> matched = PlatformMatcher.match(host, wheels);
        assertEquals(1, matched.size());
    }

    @Test
    void exactPlatformMatch() {
        PlatformMatcher.HostTags host = new PlatformMatcher.HostTags(
                "win", "x64", "3.12", List.of("win_amd64", "win32", "any"));
        var wheels = List.of(wheel("numpy-1.26.4-cp312-cp312-win_amd64.whl"));
        assertEquals(1, PlatformMatcher.match(host, wheels).size());
    }

    @Test
    void wrongPlatformExcluded() {
        PlatformMatcher.HostTags linux = new PlatformMatcher.HostTags(
                "linux", "x64", "3.12", List.of("manylinux2014_x86_64", "linux_x86_64", "any"));
        var wheels = List.of(wheel("numpy-1.26.4-cp312-cp312-win_amd64.whl"));
        assertTrue(PlatformMatcher.match(linux, wheels).isEmpty());
        assertNotNull(PlatformMatcher.incompatReason(linux, wheels.get(0)));
    }

    @Test
    void cpTagMustMatchPythonVersion() {
        PlatformMatcher.HostTags host = new PlatformMatcher.HostTags(
                "win", "x64", "3.11", List.of("win_amd64", "win32", "any"));
        var wheels = List.of(wheel("numpy-1.26.4-cp312-cp312-win_amd64.whl"));
        assertTrue(PlatformMatcher.match(host, wheels).isEmpty());
    }

    @Test
    void macosVersionRangeMatchesNewer() {
        PlatformMatcher.HostTags mac = new PlatformMatcher.HostTags(
                "mac", "arm64", "3.12", List.of("macosx_11_0_arm64", "any"));
        // wheel 声明 11.0,本机 14.0 → 兼容
        var wheels = List.of(wheel("pillow-10.0.0-cp312-cp312-macosx_11_0_arm64.whl"));
        assertEquals(1, PlatformMatcher.match(mac, wheels).size());
    }

    @Test
    void macosVersionRangeRejectsOlder() {
        PlatformMatcher.HostTags mac = new PlatformMatcher.HostTags(
                "mac", "arm64", "3.12", List.of("macosx_10_9_arm64", "any"));
        // wheel 声明 11.0,本机兼容下限 10.9,但本机解析的标签里没有 macosx_11_0_arm64
        // 此测试验证:若 host 标签不含该具体 tag,则不匹配(除非 any)
        var wheels = List.of(wheel("pillow-10.0.0-cp312-cp312-macosx_12_0_arm64.whl"));
        assertTrue(PlatformMatcher.match(mac, wheels).isEmpty());
    }

    @Test
    void detectHostProducesTags() {
        PlatformMatcher.HostTags h = PlatformMatcher.detectHost("3.12");
        assertNotNull(h);
        assertFalse(h.compatibleTags().isEmpty());
        assertTrue(h.compatibleTags().contains("any"));
    }

    @Test
    void samePackageMultipleWheelsAllCompatibleKept() {
        PlatformMatcher.HostTags host = new PlatformMatcher.HostTags(
                "win", "x64", "3.12", List.of("win_amd64", "win32", "any"));
        var wheels = List.of(
                wheel("numpy-1.26.4-cp312-cp312-win_amd64.whl"),
                wheel("numpy-1.26.4-py3-none-any.whl"));
        // 两个都兼容 → 都保留(UI 去重展示,DeployService 都交给 pip)
        assertEquals(2, PlatformMatcher.match(host, wheels).size());
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q test -pl SwissKitJ-Plugin-OfflinePython -Dtest=PlatformMatcherTest`
Expected: compile error — `PlatformMatcher` does not exist.

- [ ] **Step 3: Implement PlatformMatcher**

Create `src/main/java/plugin/swisskit/offlinepython/domain/PlatformMatcher.java`:

```java
package plugin.swisskit.offlinepython.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * 纯逻辑:给定本机平台标签 + wheel 列表,返回适配本机的子集。
 * 不依赖 JavaFX,可单测。匹配规则是 PEP 425 的简化版。
 */
public final class PlatformMatcher {

    private PlatformMatcher() {}

    /** 本机环境信息 + 推导出的兼容平台标签集合。 */
    public record HostTags(String os, String arch, String pythonVersion, List<String> compatibleTags) {}

    /** 用 JVM 系统属性推导本机平台标签。pythonVersion 形如 "3.12"。 */
    public static HostTags detectHost(String pythonVersion) {
        String osName = System.getProperty("os.name", "").toLowerCase();
        String osArch = System.getProperty("os.arch", "");
        String os;
        String arch;
        if (osName.contains("win")) {
            os = "win";
            arch = osArch.contains("64") ? "x64" : "x86";
        } else if (osName.contains("mac") || osName.contains("darwin")) {
            os = "mac";
            arch = osArch.contains("aarch64") || osArch.contains("arm") ? "arm64" : "x64";
        } else {
            os = "linux";
            arch = osArch.contains("aarch64") || osArch.contains("arm") ? "arm64" : "x64";
        }
        return new HostTags(os, arch, pythonVersion, tagsFor(os, arch));
    }

    private static List<String> tagsFor(String os, String arch) {
        List<String> tags = new ArrayList<>();
        tags.add("any");
        switch (os) {
            case "win" -> {
                if ("x64".equals(arch)) tags.add("win_amd64");
                tags.add("win32");
            }
            case "linux" -> {
                if ("x64".equals(arch)) {
                    tags.add("manylinux2014_x86_64");
                    tags.add("linux_x86_64");
                } else {
                    tags.add("manylinux2014_aarch64");
                }
            }
            case "mac" -> {
                // macOS 用区间匹配:这里放入一个代表当前最低版本(取运行机 macOS 版本)的 tag,
                // match() 内对 macosx_A_B_arch 做区间比较,不依赖此列表精确枚举。
                int[] v = macVersion();
                if ("arm64".equals(arch)) {
                    tags.add("macosx_" + v[0] + "_" + v[1] + "_arm64");
                } else {
                    tags.add("macosx_" + v[0] + "_" + v[1] + "_x86_64");
                }
            }
        }
        return tags;
    }

    private static int[] macVersion() {
        String v = System.getProperty("os.version", "11.0");
        String[] p = v.split("\\.");
        int major = p.length > 0 ? parseIntSafe(p[0], 11) : 11;
        int minor = p.length > 1 ? parseIntSafe(p[1], 0) : 0;
        return new int[]{major, minor};
    }

    private static int parseIntSafe(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }

    /** 返回适配 host 的 wheel 子集(保留所有兼容 wheel,按输入顺序)。 */
    public static List<WheelEntry> match(HostTags host, List<WheelEntry> wheels) {
        List<WheelEntry> out = new ArrayList<>();
        for (WheelEntry w : wheels) {
            if (isCompatible(host, w)) out.add(w);
        }
        return out;
    }

    /** 不兼容原因(null = 兼容)。 */
    public static String incompatReason(HostTags host, WheelEntry wheel) {
        String[] tags = parseWheelTags(wheel.getFile());
        if (tags == null) return "无法解析 wheel 文件名";
        if (!pythonTagOk(tags[0], host.pythonVersion())) return "Python 标签 " + tags[0] + " 与本机 " + host.pythonVersion() + " 不兼容";
        if (!abiTagOk(tags[1], host.pythonVersion())) return "ABI 标签 " + tags[1] + " 不兼容";
        if (!platformTagOk(tags[2], host)) return "平台 " + tags[2] + " 不适配本机";
        return null;
    }

    private static boolean isCompatible(HostTags host, WheelEntry wheel) {
        return incompatReason(host, wheel) == null;
    }

    /** 解析 wheel 文件名 → [pythonTag, abiTag, platformTag]。 */
    static String[] parseWheelTags(String fileName) {
        if (fileName == null) return null;
        String name = fileName.endsWith(".whl") ? fileName.substring(0, fileName.length() - 4) : fileName;
        String[] parts = name.split("-");
        if (parts.length < 5) return null;
        // {name}-{version}-{pythonTag}-{abiTag}-{platformTag}
        return new String[]{parts[parts.length - 3], parts[parts.length - 2], parts[parts.length - 1]};
    }

    private static boolean pythonTagOk(String tag, String pythonVersion) {
        if (tag == null) return false;
        int[] pv = verParts(pythonVersion);
        if (tag.startsWith("py3") || tag.equals("py3")) return true;
        if (tag.startsWith("py2")) return false;
        if (tag.startsWith("cp") || tag.startsWith("pp")) {
            int[] tv = tagParts(tag);
            return tv != null && tv[0] == pv[0] && tv[1] == pv[1];
        }
        if (tag.startsWith("py")) {
            int[] tv = tagParts(tag);
            return tv != null && (tv[0] > pv[0] || (tv[0] == pv[0] && tv[1] >= pv[1]));
        }
        return false;
    }

    private static boolean abiTagOk(String abi, String pythonVersion) {
        if (abi == null || "none".equals(abi)) return true;
        if ("abi3".equals(abi)) return true;
        if (abi.startsWith("cp")) {
            int[] pv = verParts(pythonVersion);
            int[] tv = tagParts(abi);
            return tv != null && tv[0] == pv[0] && tv[1] == pv[1];
        }
        return false;
    }

    private static boolean platformTagOk(String platform, HostTags host) {
        if (platform == null || "any".equals(platform)) return true;
        if (host.compatibleTags().contains(platform)) return true;
        // macOS 区间匹配:macosx_<minMajor>_<minMinor>_<arch>
        if (platform.startsWith("macosx_") && "mac".equals(host.os())) {
            return macosRangeOk(platform, host);
        }
        return false;
    }

    private static boolean macosRangeOk(String platform, HostTags host) {
        String[] p = platform.split("_");
        if (p.length < 4) return false;
        int wheelMajor = parseIntSafe(p[1], -1);
        int wheelMinor = parseIntSafe(p[2], -1);
        String wheelArch = p[3];
        if (!wheelArch.equals(host.arch())) return false;
        int[] hostMac = macVersion();
        if (hostMac[0] > wheelMajor) return true;
        if (hostMac[0] == wheelMajor && hostMac[1] >= wheelMinor) return true;
        return false;
    }

    private static int[] verParts(String v) {
        if (v == null) return new int[]{0, 0};
        String[] p = v.split("\\.");
        return new int[]{parseIntSafe(p[0], 0), p.length > 1 ? parseIntSafe(p[1], 0) : 0};
    }

    /** "cp312" → [3,12];"py39" → [3,9]。 */
    private static int[] tagParts(String tag) {
        StringBuilder digits = new StringBuilder();
        for (char c : tag.toCharArray()) if (Character.isDigit(c)) digits.append(c);
        if (digits.length() < 2) return null;
        String d = digits.toString();
        return new int[]{Integer.parseInt(d.substring(0, 1)),
                         Integer.parseInt(d.substring(1))};
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q test -pl SwissKitJ-Plugin-OfflinePython -Dtest=PlatformMatcherTest`
Expected: PASS — 8 tests.

- [ ] **Step 5: Commit**

```bash
cd SwissKitJ-Plugin-OfflinePython
git add src/main/java/plugin/swisskit/offlinepython/domain/PlatformMatcher.java \
        src/test/java/plugin/swisskit/offlinepython/domain/PlatformMatcherTest.java
git commit -m "feat(Deploy): add PlatformMatcher — host detect + wheel compat matching"
```

---

### Task 2: BundleReader(纯逻辑,读 ZIP 内 manifest)

**Files:**
- Create: `src/main/java/plugin/swisskit/offlinepython/domain/BundleReader.java`
- Test: `src/test/java/plugin/swisskit/offlinepython/domain/BundleReaderTest.java`

**Interfaces:**
- Consumes: `Manifest` (existing), `WheelEntry` (existing), Gson `JsonStore` (existing)
- Produces:
  - `record Bundle(Manifest manifest, List<WheelEntry> wheels)` — wheels 为 manifest 里全部条目
  - `static Bundle read(Path zip) throws IOException` — 从 ZIP 读 `bundle/manifest.json`
  - `static List<String> listWheelFiles(Path zip) throws IOException` — 列出 `bundle/wheels/*.whl` 文件名

- [ ] **Step 1: Write failing tests**

Create `src/test/java/plugin/swisskit/offlinepython/domain/BundleReaderTest.java`:

```java
package plugin.swisskit.offlinepython.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class BundleReaderTest {

    @Test
    void readsManifestFromZip(@TempDir Path tmp) throws IOException {
        Path zip = tmp.resolve("b.zip");
        Manifest m = new Manifest();
        m.setSchemaVersion(1);
        m.getPython().setVersion("3.12.10");
        m.getWheels().add(new WheelEntry("numpy", "1.26.4",
                "wheels/numpy-1.26.4-cp312-cp312-win_amd64.whl", "abc", 1000, true));
        m.getRequirements().add("numpy==1.26.4");
        String json = plugin.swisskit.offlinepython.infra.JsonStore.toJson(m);
        try (ZipOutputStream z = new ZipOutputStream(Files.newOutputStream(zip))) {
            z.putNextEntry(new ZipEntry("bundle/manifest.json"));
            z.write(json.getBytes());
            z.closeEntry();
            z.putNextEntry(new ZipEntry("bundle/wheels/numpy-1.26.4-cp312-cp312-win_amd64.whl"));
            z.write(new byte[]{1, 2, 3});
            z.closeEntry();
        }

        BundleReader.Bundle b = BundleReader.read(zip);
        assertEquals("3.12.10", b.manifest().getPython().getVersion());
        assertEquals(1, b.wheels().size());
        assertEquals("numpy", b.wheels().get(0).getName());
    }

    @Test
    void listWheelFilesReturnsWhlNames(@TempDir Path tmp) throws IOException {
        Path zip = tmp.resolve("b.zip");
        try (ZipOutputStream z = new ZipOutputStream(Files.newOutputStream(zip))) {
            z.putNextEntry(new ZipEntry("bundle/wheels/a.whl"));
            z.write(new byte[]{1});
            z.closeEntry();
            z.putNextEntry(new ZipEntry("bundle/wheels/b.whl"));
            z.write(new byte[]{2});
            z.closeEntry();
            z.putNextEntry(new ZipEntry("bundle/manifest.json"));
            z.write("{}".getBytes());
            z.closeEntry();
        }
        List<String> files = BundleReader.listWheelFiles(zip);
        assertEquals(List.of("a.whl", "b.whl"), files);
    }

    @Test
    void throwsOnMissingManifest(@TempDir Path tmp) throws IOException {
        Path zip = tmp.resolve("b.zip");
        try (ZipOutputStream z = new ZipOutputStream(Files.newOutputStream(zip))) {
            z.putNextEntry(new ZipEntry("bundle/wheels/a.whl"));
            z.write(new byte[]{1});
            z.closeEntry();
        }
        assertThrows(IOException.class, () -> BundleReader.read(zip));
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q test -pl SwissKitJ-Plugin-OfflinePython -Dtest=BundleReaderTest`
Expected: compile error — `BundleReader` / `JsonStore.toJson` does not exist.

- [ ] **Step 3: Add JsonStore.toJson helper (if missing)**

Check `infra/JsonStore.java` for a public `toJson(Object)` method. If absent, add:

```java
/** Serialize an object to JSON (pretty, UTF-8). */
public static String toJson(Object obj) {
    return GSON.toJson(obj);
}
```
(Use the existing `GSON` field name — verify by reading `JsonStore.java`; if it's named differently, match it.)

- [ ] **Step 4: Implement BundleReader**

Create `src/main/java/plugin/swisskit/offlinepython/domain/BundleReader.java`:

```java
package plugin.swisskit.offlinepython.domain;

import plugin.swisskit.offlinepython.infra.JsonStore;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 纯逻辑:从 bundle ZIP 读取 manifest + wheel 列表。不修改 ZIP,不依赖 JavaFX。
 */
public final class BundleReader {

    private static final String MANIFEST_ENTRY = "bundle/manifest.json";
    private static final String WHEELS_DIR = "bundle/wheels/";

    private BundleReader() {}

    public record Bundle(Manifest manifest, List<WheelEntry> wheels) {}

    /** 读 ZIP 内 bundle/manifest.json,返回 Manifest + 其 wheels 列表。 */
    public static Bundle read(Path zip) throws IOException {
        try (ZipFile zf = new ZipFile(zip.toFile())) {
            ZipEntry entry = zf.getEntry(MANIFEST_ENTRY);
            if (entry == null) throw new IOException("无效的 bundle 包:缺少 " + MANIFEST_ENTRY);
            try (InputStream in = zf.getInputStream(entry)) {
                String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                Manifest m = JsonStore.fromJson(json, Manifest.class);
                List<WheelEntry> wheels = m.getWheels() != null ? m.getWheels() : new ArrayList<>();
                return new Bundle(m, wheels);
            }
        }
    }

    /** 列出 bundle/wheels/ 下的 .whl 文件名(仅文件名,不含路径)。 */
    public static List<String> listWheelFiles(Path zip) throws IOException {
        List<String> out = new ArrayList<>();
        try (ZipFile zf = new ZipFile(zip.toFile())) {
            java.util.Enumeration<? extends ZipEntry> e = zf.entries();
            while (e.hasMoreElements()) {
                ZipEntry ze = e.nextElement();
                String name = ze.getName();
                if (name.startsWith(WHEELS_DIR) && name.endsWith(".whl") && !name.endsWith("/")) {
                    out.add(Path.of(name).getFileName().toString());
                }
            }
        }
        out.sort(null);
        return out;
    }
}
```

> Note: also add `fromJson(String, Class)` to `JsonStore` if it lacks a public JSON-string parser (read the file first; the existing `load(Path, Class)` likely wraps one — expose it).

- [ ] **Step 5: Run tests to verify they pass**

Run: `mvn -q test -pl SwissKitJ-Plugin-OfflinePython -Dtest=BundleReaderTest`
Expected: PASS — 3 tests.

- [ ] **Step 6: Commit**

```bash
cd SwissKitJ-Plugin-OfflinePython
git add src/main/java/plugin/swisskit/offlinepython/domain/BundleReader.java \
        src/main/java/plugin/swisskit/offlinepython/infra/JsonStore.java \
        src/test/java/plugin/swisskit/offlinepython/domain/BundleReaderTest.java
git commit -m "feat(Deploy): add BundleReader — read manifest + wheel list from bundle ZIP"
```

---

### Task 3: BuildConfig.Bundle + PackageService

**Files:**
- Modify: `src/main/java/plugin/swisskit/offlinepython/domain/BuildConfig.java`
- Create: `src/main/java/plugin/swisskit/offlinepython/command/PackageService.java`
- Test: `src/test/java/plugin/swisskit/offlinepython/command/PackageServiceTest.java`

**Interfaces:**
- Consumes: `BuildConfig`, existing `output/` structure (`manifest.json`, `wheelhouse/<ver>/*.whl`, `SHA256SUMS`)
- Produces:
  - `Path packageBundle(Path projectDir, BuildConfig cfg) throws IOException` — 返回生成的 ZIP 路径
  - `BuildConfig.Bundle` config section: fields `autoPackage` (bool), `name` (String), `sha256` (bool)

- [ ] **Step 1: Add Bundle section to BuildConfig**

Modify `BuildConfig.java`. After the `Pkg` class, add a new `Bundle` static class and a `bundle` field. In the class body:

```java
    private Pkg pkg = new Pkg();
    private Bundle bundle = new Bundle();   // ← 新增
```

Add the inner class (after `Pkg`):

```java
    /** 部署 bundle 打包配置(打包 output/ → bundle.zip)。 */
    @Data public static class Bundle {
        /** 构建后是否自动打包(默认关)。手动打包按钮恒可用。 */
        private boolean autoPackage;
        /** bundle 名,空 = 用项目目录名。 */
        private String name = "";
        /** 是否在 ZIP 内包含 SHA256SUMS。 */
        private boolean sha256 = true;
    }
```

Also in `defaults()`, set: `c.bundle.sha256 = true;` (autoPackage stays false by default).

- [ ] **Step 2: Write failing test**

Create `src/test/java/plugin/swisskit/offlinepython/command/PackageServiceTest.java`:

```java
package plugin.swisskit.offlinepython.command;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import plugin.swisskit.offlinepython.domain.BuildConfig;
import plugin.swisskit.offlinepython.domain.Manifest;
import plugin.swisskit.offlinepython.domain.WheelEntry;
import plugin.swisskit.offlinepython.infra.JsonStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

class PackageServiceTest {

    @Test
    void packagesOutputIntoBundleZip(@TempDir Path tmp) throws IOException {
        // 搭建 output/ 结构
        Path projectDir = tmp.resolve("proj");
        Path output = projectDir.resolve("output");
        Path wheels = output.resolve("wheelhouse").resolve("3.12.10");
        Files.createDirectories(wheels);
        // manifest.json
        Manifest m = new Manifest();
        m.setSchemaVersion(1);
        m.getPython().setVersion("3.12.10");
        m.getWheels().add(new WheelEntry("numpy", "1.26.4",
                "wheelhouse/3.12.10/numpy-1.26.4-cp312-cp312-win_amd64.whl", "abc", 1000, true));
        JsonStore.save(m, output.resolve("manifest.json"));
        // 一个 wheel
        Files.writeString(wheels.resolve("numpy-1.26.4-cp312-cp312-win_amd64.whl"), "fake-wheel");
        // SHA256SUMS
        Files.writeString(output.resolve("SHA256SUMS"), "abc  wheelhouse/3.12.10/numpy-1.26.4-cp312-cp312-win_amd64.whl\n");

        BuildConfig cfg = BuildConfig.defaults();
        cfg.getPython().setVersion("3.12.10");

        Path zip = new PackageService().packageBundle(projectDir, cfg);

        assertTrue(Files.exists(zip));
        try (ZipFile zf = new ZipFile(zip.toFile())) {
            assertNotNull(zf.getEntry("bundle/manifest.json"));
            assertNotNull(zf.getEntry("bundle/SHA256SUMS"));
            assertNotNull(zf.getEntry("bundle/wheels/numpy-1.26.4-cp312-cp312-win_amd64.whl"));
        }
    }

    @Test
    void throwsWhenManifestMissing(@TempDir Path tmp) throws IOException {
        Path projectDir = tmp.resolve("proj");
        Files.createDirectories(projectDir.resolve("output"));
        BuildConfig cfg = BuildConfig.defaults();
        assertThrows(IOException.class, () -> new PackageService().packageBundle(projectDir, cfg));
    }

    @Test
    void throwsWhenNoWheels(@TempDir Path tmp) throws IOException {
        Path projectDir = tmp.resolve("proj");
        Path output = projectDir.resolve("output");
        Files.createDirectories(output);
        JsonStore.save(new Manifest(), output.resolve("manifest.json"));
        BuildConfig cfg = BuildConfig.defaults();
        assertThrows(IOException.class, () -> new PackageService().packageBundle(projectDir, cfg));
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `mvn -q test -pl SwissKitJ-Plugin-OfflinePython -Dtest=PackageServiceTest`
Expected: compile error — `PackageService` / `getBundle()` does not exist.

- [ ] **Step 4: Implement PackageService**

Create `src/main/java/plugin/swisskit/offlinepython/command/PackageService.java`:

```java
package plugin.swisskit.offlinepython.command;

import plugin.swisskit.offlinepython.domain.BuildConfig;
import plugin.swisskit.offlinepython.infra.OpbLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 把构建产物 output/ 打包成一个 bundle ZIP,供离线机部署使用。
 * ZIP 结构:bundle/{manifest.json, SHA256SUMS?, wheels/*.whl}
 */
public class PackageService {

    private static final String BUNDLE_ROOT = "bundle/";
    private static final String WHEELS_DIR = BUNDLE_ROOT + "wheels/";

    private final OpbLogger log;

    public PackageService() { this(null); }
    public PackageService(OpbLogger log) { this.log = log; }

    /** 打包 projectDir/output 为 bundle ZIP,返回生成的 ZIP 路径。 */
    public Path packageBundle(Path projectDir, BuildConfig cfg) throws IOException {
        Path output = projectDir.resolve(cfg.getRepository().getOutput());
        Path manifest = output.resolve("manifest.json");
        if (!Files.exists(manifest)) {
            throw new IOException("请先构建:未找到 output/manifest.json");
        }
        Path wheelhouse = output.resolve(cfg.getRepository().getWheelDir())
                .resolve(cfg.getPython().getVersion());
        if (!Files.exists(wheelhouse) || countWheels(wheelhouse) == 0) {
            throw new IOException("无 wheel 可打包:wheelhouse 为空");
        }

        String bundleName = (cfg.getBundle() != null && cfg.getBundle().getName() != null
                && !cfg.getBundle().getName().isBlank())
                ? cfg.getBundle().getName()
                : projectDir.getFileName().toString();
        String stamp = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"));
        Path zip = output.resolve(bundleName + "-bundle-" + stamp + ".zip");

        boolean includeSha = cfg.getBundle() == null || cfg.getBundle().isSha256();
        int count = 0;
        long bytes = 0;

        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip))) {
            // manifest.json
            addEntry(zos, BUNDLE_ROOT + "manifest.json", manifest);
            // SHA256SUMS
            Path sums = output.resolve("SHA256SUMS");
            if (includeSha && Files.exists(sums)) {
                addEntry(zos, BUNDLE_ROOT + "SHA256SUMS", sums);
            }
            // wheels(扁平化,按文件名排序)
            try (Stream<Path> files = Files.list(wheelhouse)) {
                List<Path> sorted = files.filter(p -> p.toString().endsWith(".whl"))
                        .sorted(Comparator.comparing(p -> p.getFileName().toString())).toList();
                for (Path whl : sorted) {
                    String entryName = WHEELS_DIR + whl.getFileName().toString();
                    zos.putNextEntry(new ZipEntry(entryName));
                    Files.copy(whl, zos);
                    zos.closeEntry();
                    count++;
                    bytes += Files.size(whl);
                }
            }
        }

        if (log != null) log.log("打包完成:" + count + " wheels · " + humanBytes(bytes) + " · " + zip.getFileName());
        return zip;
    }

    private long countWheels(Path dir) throws IOException {
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(p -> p.toString().endsWith(".whl")).count();
        }
    }

    private void addEntry(ZipOutputStream zos, String name, Path src) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        Files.copy(src, zos);
        zos.closeEntry();
    }

    private static String humanBytes(long b) {
        if (b < 1024 * 1024) return (b / 1024) + " KB";
        return String.format("%.1f MB", b / (1024.0 * 1024));
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `mvn -q test -pl SwissKitJ-Plugin-OfflinePython -Dtest=PackageServiceTest`
Expected: PASS — 3 tests.

- [ ] **Step 6: Commit**

```bash
cd SwissKitJ-Plugin-OfflinePython
git add src/main/java/plugin/swisskit/offlinepython/domain/BuildConfig.java \
        src/main/java/plugin/swisskit/offlinepython/command/PackageService.java \
        src/test/java/plugin/swisskit/offlinepython/command/PackageServiceTest.java
git commit -m "feat(Deploy): add Bundle config + PackageService — pack output/ into bundle ZIP"
```

---

### Task 4: DeployTarget, DeployResult, DeployService

**Files:**
- Create: `src/main/java/plugin/swisskit/offlinepython/domain/DeployTarget.java`
- Create: `src/main/java/plugin/swisskit/offlinepython/domain/DeployResult.java`
- Create: `src/main/java/plugin/swisskit/offlinepython/command/DeployService.java`
- Test: `src/test/java/plugin/swisskit/offlinepython/command/DeployServiceTest.java`

**Interfaces:**
- Consumes: `PlatformMatcher` (Task 1), `BundleReader` (Task 2), `ProcessRunner` (existing), `PythonDetector` (existing)
- Produces:
  - `DeployTarget` sealed interface with `Global(Path pythonExe)` and `Venv(Path pythonExe, Path venvPath)`
  - `DeployResult(int installed, int skipped, int failed, long durationMs)`
  - `DeployService.install(Path zip, DeployTarget target, java.util.function.Consumer<String> onLog) throws Exception` → `DeployResult`

- [ ] **Step 1: Create DeployTarget + DeployResult**

Create `src/main/java/plugin/swisskit/offlinepython/domain/DeployTarget.java`:

```java
package plugin.swisskit.offlinepython.domain;

import java.nio.file.Path;

/**
 * 安装目标:全局现有 Python,或新建虚拟环境。
 */
public sealed interface DeployTarget {

    /** 装到检测到的全局 Python 的 site-packages。 */
    record Global(Path pythonExe) implements DeployTarget {}

    /** 新建虚拟环境(python -m venv venvPath)后装进去。 */
    record Venv(Path pythonExe, Path venvPath) implements DeployTarget {}
}
```

Create `src/main/java/plugin/swisskit/offlinepython/domain/DeployResult.java`:

```java
package plugin.swisskit.offlinepython.domain;

/** 部署(逐包安装)的结果汇总。 */
public record DeployResult(int installed, int skipped, int failed, long durationMs) {
    public boolean ok() { return failed == 0; }
}
```

- [ ] **Step 2: Write failing test**

Create `src/test/java/plugin/swisskit/offlinepython/command/DeployServiceTest.java`:

```java
package plugin.swisskit.offlinepython.command;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import plugin.swisskit.offlinepython.domain.DeployTarget;
import plugin.swisskit.offlinepython.domain.Manifest;
import plugin.swisskit.offlinepython.domain.WheelEntry;
import plugin.swisskit.offlinepython.infra.JsonStore;
import plugin.swisskit.offlinepython.infra.ProcessRunner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeployServiceTest {

    /** 用一个全平台兼容的纯 Python wheel 构造 bundle ZIP。 */
    private Path makeBundle(Path tmp) throws IOException {
        Path zip = tmp.resolve("b.zip");
        Manifest m = new Manifest();
        m.getPython().setVersion("3.12.10");
        m.getWheels().add(new WheelEntry("requests", "2.31.0",
                "wheels/requests-2.31.0-py3-none-any.whl", "abc", 1000, true));
        String json = JsonStore.toJson(m);
        try (ZipOutputStream z = new ZipOutputStream(Files.newOutputStream(zip))) {
            z.putNextEntry(new ZipEntry("bundle/manifest.json"));
            z.write(json.getBytes());
            z.closeEntry();
            z.putNextEntry(new ZipEntry("bundle/wheels/requests-2.31.0-py3-none-any.whl"));
            z.write(new byte[]{1});
            z.closeEntry();
        }
        return zip;
    }

    @Test
    void installRunsPipPerWheel(@TempDir Path tmp) throws Exception {
        Path zip = makeBundle(tmp);
        // mock ProcessRunner:所有命令返回 0
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(anyList(), any())).thenReturn(0);

        DeployTarget target = new DeployTarget.Global(Path.of("/usr/bin/python3"));
        List<String> logs = new ArrayList<>();
        Consumer<String> onLog = logs::add;

        DeployResult r = new DeployService(runner).install(zip, target, onLog);

        assertEquals(1, r.installed());
        assertEquals(0, r.failed());
        // 至少调用了一次 pip install
        verify(runner, atLeastOnce()).run(anyList(), any());
    }

    @Test
    void failedWheelDoesNotAbortOthers(@TempDir Path tmp) throws Exception {
        Path zip = makeBundle(tmp);
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(anyList(), any())).thenReturn(1); // 全失败

        DeployTarget target = new DeployTarget.Global(Path.of("/usr/bin/python3"));
        DeployResult r = new DeployService(runner).install(zip, target, s -> {});

        assertEquals(0, r.installed());
        assertEquals(1, r.failed());
    }
}
```

> Note: this test uses Mockito. Add Mockito to `pom.xml` test deps if not present (see Step 3).

- [ ] **Step 3: Add Mockito to pom.xml (if missing)**

In `pom.xml`, within `<dependencies>`, add after the junit-jupiter dep:

```xml
        <dependency>
            <groupId>org.mockito</groupId>
            <artifactId>mockito-core</artifactId>
            <version>5.12.0</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.mockito</groupId>
            <artifactId>mockito-junit-jupiter</artifactId>
            <version>5.12.0</version>
            <scope>test</scope>
        </dependency>
```

> Verify: check `pom.xml` first; if Mockito already present, skip.

- [ ] **Step 4: Run test to verify it fails**

Run: `mvn -q test -pl SwissKitJ-Plugin-OfflinePython -Dtest=DeployServiceTest`
Expected: compile error — `DeployService` does not exist.

- [ ] **Step 5: Implement DeployService**

Create `src/main/java/plugin/swisskit/offlinepython/command/DeployService.java`:

```java
package plugin.swisskit.offlinepython.command;

import plugin.swisskit.offlinepython.domain.BundleReader;
import plugin.swisskit.offlinepython.domain.DeployResult;
import plugin.swisskit.offlinepython.domain.DeployTarget;
import plugin.swisskit.offlinepython.domain.PlatformMatcher;
import plugin.swisskit.offlinepython.domain.WheelEntry;
import plugin.swisskit.offlinepython.infra.ProcessRunner;
import plugin.swisskit.offlinepython.infra.PythonDetector;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 部署执行核心:解压 bundle ZIP → 按本机平台筛选 wheel → 逐包 pip install。
 * 不依赖 JavaFX;ProcessRunner 注入便于测试。
 */
public class DeployService {

    private final ProcessRunner runner;

    public DeployService() { this(new ProcessRunner()); }
    public DeployService(ProcessRunner runner) { this.runner = runner; }

    /**
     * 把 ZIP 中适配本机的 wheel 装到目标环境(全局或新建 venv)。
     *
     * @param zip      bundle ZIP 路径
     * @param target   安装目标
     * @param onLog    日志回调(每行一条)
     * @return 安装结果汇总
     */
    public DeployResult install(Path zip, DeployTarget target, Consumer<String> onLog) throws Exception {
        long start = System.currentTimeMillis();

        // 1. 解压 wheels 到临时目录
        Path tmpDir = Files.createTempDirectory("opb-deploy");
        Path wheelsDir = tmpDir.resolve("wheels");
        Files.createDirectories(wheelsDir);
        try {
            extractWheels(zip, wheelsDir, onLog);

            // 2. (仅 venv)创建虚拟环境
            Path pythonExe = resolvePython(target, onLog);

            // 3. 读 manifest + 筛选适配本机的 wheel
            BundleReader.Bundle bundle = BundleReader.read(zip);
            PythonDetector.Detection det = PythonDetector.detect(null);
            PlatformMatcher.HostTags host = PlatformMatcher.detectHost(det.pythonVersion());
            List<WheelEntry> matched = PlatformMatcher.match(host, bundle.wheels());
            onLog.accept("适配本机的 wheel:" + matched.size() + " / " + bundle.wheels().size());

            // 4. 逐包安装
            int installed = 0, failed = 0, skipped = 0;
            for (WheelEntry w : matched) {
                String whlFile = Path.of(w.getFile()).getFileName().toString();
                Path whlPath = wheelsDir.resolve(whlFile);
                if (!Files.exists(whlPath)) {
                    onLog.accept("跳过(ZIP 内缺失文件):" + whlFile);
                    skipped++;
                    continue;
                }
                List<String> cmd = List.of(
                        pythonExe.toString(), "-m", "pip", "install",
                        "--no-index", "--no-deps",
                        "--find-links", wheelsDir.toString(),
                        whlFile);
                onLog.accept("$ " + String.join(" ", cmd));
                try {
                    int code = runner.run(cmd, onLog);
                    if (code == 0) {
                        installed++;
                        onLog.accept("✓ " + w.getName());
                    } else {
                        failed++;
                        onLog.accept("✗ " + w.getName() + " (exit " + code + ")");
                    }
                } catch (Exception ex) {
                    failed++;
                    onLog.accept("✗ " + w.getName() + " : " + ex.getMessage());
                }
            }

            long dur = System.currentTimeMillis() - start;
            return new DeployResult(installed, skipped, failed, dur);
        } finally {
            // 清理临时目录(失败时保留以便排查?此处统一清理,日志已有记录)
            deleteRecursively(tmpDir);
        }
    }

    /** 解压 bundle/wheels/*.whl 到 destDir。 */
    private void extractWheels(Path zip, Path destDir, Consumer<String> onLog) throws IOException {
        try (ZipFile zf = new ZipFile(zip.toFile())) {
            java.util.Enumeration<? extends ZipEntry> e = zf.entries();
            while (e.hasMoreElements()) {
                ZipEntry ze = e.nextElement();
                if (ze.getName().startsWith("bundle/wheels/") && ze.getName().endsWith(".whl") && !ze.isDirectory()) {
                    String fname = Path.of(ze.getName()).getFileName().toString();
                    Path out = destDir.resolve(fname);
                    try (var in = zf.getInputStream(ze)) {
                        Files.copy(in, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        }
    }

    /** 解析目标 Python 可执行文件路径;venv 先创建环境再返回其 python 路径。 */
    private Path resolvePython(DeployTarget target, Consumer<String> onLog) throws Exception {
        return switch (target) {
            case DeployTarget.Global g -> g.pythonExe();
            case DeployTarget.Venv v -> {
                List<String> cmd = List.of(v.pythonExe().toString(), "-m", "venv", v.venvPath().toString());
                onLog.accept("$ " + String.join(" ", cmd));
                int code = runner.run(cmd, onLog);
                if (code != 0) throw new IllegalStateException("虚拟环境创建失败(exit " + code + ")");
                yield venvPython(v.venvPath());
            }
        };
    }

    /** venv 内 python 路径:Windows = venv/Scripts/python.exe,其他 = venv/bin/python。 */
    private Path venvPython(Path venvRoot) {
        boolean win = System.getProperty("os.name", "").toLowerCase().contains("win");
        Path exe = win ? venvRoot.resolve("Scripts").resolve("python.exe")
                       : venvRoot.resolve("bin").resolve("python");
        return exe;
    }

    private void deleteRecursively(Path dir) {
        if (!Files.exists(dir)) return;
        try (var walk = Files.walk(dir)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ignored) {}
            });
        } catch (IOException ignored) {}
    }
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `mvn -q test -pl SwissKitJ-Plugin-OfflinePython -Dtest=DeployServiceTest`
Expected: PASS — 2 tests.

- [ ] **Step 7: Run full test suite to check no regressions**

Run: `mvn -q test -pl SwissKitJ-Plugin-OfflinePython`
Expected: all green (existing tests still pass).

- [ ] **Step 8: Commit**

```bash
cd SwissKitJ-Plugin-OfflinePython
git add pom.xml \
        src/main/java/plugin/swisskit/offlinepython/domain/DeployTarget.java \
        src/main/java/plugin/swisskit/offlinepython/domain/DeployResult.java \
        src/main/java/plugin/swisskit/offlinepython/command/DeployService.java \
        src/test/java/plugin/swisskit/offlinepython/command/DeployServiceTest.java
git commit -m "feat(Deploy): add DeployService — extract + platform-filter + per-wheel pip install"
```

---

### Task 5: i18n + CommandShell deploy nav

**Files:**
- Modify: `src/main/resources/i18n/messages_zh.properties`
- Modify: `src/main/resources/i18n/messages.properties`
- Modify: `src/main/java/plugin/swisskit/offlinepython/ui/CommandShell.java`

**Interfaces:**
- Consumes: existing nav mechanism (`navLabels`, `navEntry`, `select`)
- Produces: new `opb.nav.deploy` key; `select("deploy")` routes to a `DeployPanel` (created in Task 6 — here we add the label + nav button + switch arm wiring a lazy `DeployPanel` field)

- [ ] **Step 1: Add i18n keys**

In `messages_zh.properties`, after `opb.nav.doctor=工具`:

```
opb.nav.deploy=部署
opb.deploy.title=离线部署
opb.deploy.selectZip=选择 ZIP 包…
opb.deploy.noPython=未检测到 Python — 请先安装 Python 3.10+
opb.deploy.invalidZip=无效的 bundle 包
opb.deploy.targetGlobal=全局环境
opb.deploy.targetVenv=新建虚拟环境
opb.deploy.install=▶ 开始安装
opb.deploy.installing=正在安装…
opb.deploy.done=成功安装 {0} 个包
opb.deploy.partial=完成:{0} 成功,{1} 失败
opb.deploy.failed=安装失败
```

In `messages.properties`, add the same keys with English values:

```
opb.nav.deploy=Deploy
opb.deploy.title=Offline Deploy
opb.deploy.selectZip=Select ZIP bundle…
opb.deploy.noPython=No Python detected — please install Python 3.10+ first
opb.deploy.invalidZip=Invalid bundle ZIP
opb.deploy.targetGlobal=Global environment
opb.deploy.targetVenv=New virtual environment
opb.deploy.install=▶ Start install
opb.deploy.installing=Installing…
opb.deploy.done=Installed {0} packages successfully
opb.deploy.partial=Done: {0} succeeded, {1} failed
opb.deploy.failed=Install failed
```

- [ ] **Step 2: Wire deploy into CommandShell**

In `CommandShell.java`:

(a) Add import:
```java
import plugin.swisskit.offlinepython.ui.panel.DeployPanel;
```

(b) Add field next to `buildVerifyPanel`:
```java
    private DeployPanel deployPanel;
```

(c) In the constructor, after `navLabels.put("doctor", ...)`, add:
```java
        navLabels.put("deploy",  I18n.get("opb.nav.deploy"));
```

(d) In `buildNav()`, after the build entry and before doctor, add:
```java
        navEntry(nav, "deploy",  "tray-arrow-down", false);
```

(e) In `select(String key)`, add a case to the switch (before `default`):
```java
            case "deploy" -> {
                if (deployPanel == null) deployPanel = new DeployPanel(logger);
                yield deployPanel;
            }
```

> Note: `DeployPanel` is created in Task 6. If running Task 5 before Task 6, the build will fail on the missing import — that's expected; commit together with Task 6, OR create a minimal placeholder `DeployPanel` first. Recommended order: do Task 5 + Task 6 together as one commit.

- [ ] **Step 3: Commit (together with Task 6)**

Deferred — see Task 6 step.

---

### Task 6: DeployPanel UI

**Files:**
- Create: `src/main/java/plugin/swisskit/offlinepython/ui/panel/DeployPanel.java`

**Interfaces:**
- Consumes: `BundleReader`, `PlatformMatcher`, `DeployService`, `DeployTarget`, `PythonDetector`, `OpbLogger`, `OpbStyle`, `UiUtils`, `GlassNotification`, `I18n`, `PluginTask`
- Produces: a `CommandPanel` subclass shown when `select("deploy")` runs; constructor `DeployPanel(OpbLogger log)`

- [ ] **Step 1: Implement DeployPanel**

Create `src/main/java/plugin/swisskit/offlinepython/ui/panel/DeployPanel.java`:

```java
package plugin.swisskit.offlinepython.ui.panel;

import fan.summer.api.component.GlassNotification;
import fan.summer.api.component.UiUtils;
import fan.summer.api.i18n.I18n;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import plugin.swisskit.offlinepython.command.DeployService;
import plugin.swisskit.offlinepython.domain.BundleReader;
import plugin.swisskit.offlinepython.domain.DeployResult;
import plugin.swisskit.offlinepython.domain.DeployTarget;
import plugin.swisskit.offlinepython.domain.PlatformMatcher;
import plugin.swisskit.offlinepython.domain.WheelEntry;
import plugin.swisskit.offlinepython.infra.OpbLogger;
import plugin.swisskit.offlinepython.infra.PythonDetector;
import plugin.swisskit.offlinepython.task.PluginTask;
import plugin.swisskit.offlinepython.ui.OpbStyle;
import plugin.swisskit.offlinepython.ui.control.PanelHeader;

import java.nio.file.Path;
import java.util.List;

/**
 * 部署页:离线机上加载 bundle ZIP → 检测本机平台 → 筛选 wheel → 选目标 → 安装。
 * 不依赖 ProjectContext(随时可用)。
 */
public class DeployPanel extends CommandPanel {

    private final TableView<WheelEntry> matchTable = new TableView<>();
    private final Label envLabel = new Label();
    private final Label summary = new Label();
    private final RadioButton rbGlobal = new RadioButton(I18n.get("opb.deploy.targetGlobal"));
    private final RadioButton rbVenv = new RadioButton(I18n.get("opb.deploy.targetVenv"));
    private final TextField venvPath = new TextField();
    private final Button installBtn = UiUtils.glassBtn(I18n.get("opb.deploy.install"), true);
    private final ProgressBar progress = new ProgressBar(0);
    private final TextArea logArea = new TextArea();
    private final VBox previewBox = new VBox(6);
    private final VBox targetBox = new VBox(6);

    private Path selectedZip;
    private PythonDetector.Detection detection;
    private List<WheelEntry> matchedWheels = List.of();

    public DeployPanel(OpbLogger log) {
        super(log, null);  // no project context needed
        buildUi();
        detectEnv();
    }

    private void buildUi() {
        PanelHeader header = new PanelHeader(I18n.get("opb.deploy.title"));

        // ① 选包
        Button choose = UiUtils.glassBtn(I18n.get("opb.deploy.selectZip"), false);
        choose.setOnAction(e -> chooseZip());
        envLabel.setStyle("-fx-text-fill: " + OpbStyle.TEXT_SECONDARY + "; -fx-padding: 4 0;");
        VBox selectBox = new VBox(8, choose, envLabel);
        selectBox.setStyle(OpbStyle.card() + " -fx-padding: 14;");

        // ② 预览
        summary.setStyle("-fx-text-fill: " + OpbStyle.TEXT_PRIMARY + "; -fx-font-weight: 500;");
        TableColumn<WheelEntry, String> cName = new TableColumn<>("包名");
        cName.setCellValueFactory(cb -> new javafx.beans.property.SimpleStringProperty(cb.getValue().getName()));
        TableColumn<WheelEntry, String> cVer = new TableColumn<>("版本");
        cVer.setCellValueFactory(cb -> new javafx.beans.property.SimpleStringProperty(cb.getValue().getVersion()));
        TableColumn<WheelEntry, String> cFile = new TableColumn<>("wheel");
        cFile.setCellValueFactory(cb -> {
            String f = cb.getValue().getFile();
            int slash = f.lastIndexOf('/');
            return new javafx.beans.property.SimpleStringProperty(slash < 0 ? f : f.substring(slash + 1));
        });
        cFile.setCellFactory(tc -> new TableCell<>() {
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                setText(empty || v == null ? null : v);
                setStyle("-fx-font-size: 11px; -fx-text-fill: " + OpbStyle.TEXT_SECONDARY + ";");
            }
        });
        matchTable.getColumns().addAll(cName, cVer, cFile);
        matchTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        matchTable.setFixedCellSize(28);
        matchTable.setMinHeight(150);
        matchTable.setMaxHeight(260);
        matchTable.getStyleClass().add("sk-table");
        previewBox.getChildren().addAll(summary, matchTable);
        previewBox.setStyle(OpbStyle.card() + " -fx-padding: 14;");
        previewBox.setVisible(false);
        previewBox.setManaged(false);

        // ③ 目标环境
        ToggleGroup tg = new ToggleGroup();
        rbGlobal.setToggleGroup(tg);
        rbVenv.setToggleGroup(tg);
        rbGlobal.setSelected(true);
        venvPath.setPromptText("虚拟环境路径");
        venvPath.setStyle(UiUtils.fieldStyle());
        Button browse = UiUtils.glassBtn("浏览", false);
        browse.setOnAction(e -> {
            DirectoryChooser dc = new DirectoryChooser();
            var d = dc.showDialog(getScene().getWindow());
            if (d != null) venvPath.setText(d.getAbsolutePath());
        });
        HBox venvRow = new HBox(8, venvPath, browse);
        HBox.setHgrow(venvPath, Priority.ALWAYS);
        targetBox.getChildren().addAll(rbGlobal, new HBox(6, rbVenv), venvRow, installBtn);
        targetBox.setStyle(OpbStyle.card() + " -fx-padding: 14;");
        targetBox.setVisible(false);
        targetBox.setManaged(false);

        // ④ 日志
        progress.setPrefHeight(6);
        progress.setVisible(false);
        logArea.setEditable(false);
        logArea.setStyle("-fx-font-family: monospace; -fx-font-size: 11px;");
        logArea.setVisible(false);
        logArea.setManaged(false);

        installBtn.setOnAction(e -> startInstall());

        VBox content = new VBox(10, selectBox, previewBox, targetBox, progress, logArea);
        content.setFillWidth(true);
        getChildren().addAll(header, content);
    }

    private void detectEnv() {
        detection = PythonDetector.detect(null);
        if (detection.ok()) {
            PlatformMatcher.HostTags host = PlatformMatcher.detectHost(detection.pythonVersion());
            envLabel.setText("检测到本机: " + host.os() + " · " + host.arch()
                    + " · Python " + detection.pythonVersion()
                    + "  @ " + detection.executable());
        } else {
            envLabel.setText(I18n.get("opb.deploy.noPython"));
            installBtn.setDisable(true);
        }
    }

    private void chooseZip() {
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("ZIP", "*.zip"));
        var f = fc.showOpenDialog(getScene().getWindow());
        if (f == null) return;
        try {
            selectedZip = f.toPath();
            BundleReader.Bundle b = BundleReader.read(selectedZip);
            PythonDetector.Detection det = PythonDetector.detect(null);
            if (!det.ok()) { GlassNotification.toast(this, GlassNotification.Type.WARNING, I18n.get("opb.deploy.noPython")); return; }
            PlatformMatcher.HostTags host = PlatformMatcher.detectHost(det.pythonVersion());
            matchedWheels = PlatformMatcher.match(host, b.wheels());
            matchTable.getItems().setAll(matchedWheels);
            int incompat = b.wheels().size() - matchedWheels.size();
            summary.setText("将安装 " + matchedWheels.size() + " 个包(适配本机)" +
                    (incompat > 0 ? " · " + incompat + " 个不兼容(已隐藏)" : ""));
            previewBox.setVisible(true); previewBox.setManaged(true);
            targetBox.setVisible(true); targetBox.setManaged(true);
        } catch (Exception ex) {
            GlassNotification.toast(this, GlassNotification.Type.ERROR, I18n.get("opb.deploy.invalidZip") + ": " + ex.getMessage());
        }
    }

    private void startInstall() {
        if (selectedZip == null || detection == null || !detection.ok()) return;
        DeployTarget target;
        if (rbVenv.isSelected()) {
            String p = venvPath.getText().trim();
            if (p.isBlank()) { GlassNotification.toast(this, GlassNotification.Type.WARNING, "请填写虚拟环境路径"); return; }
            target = new DeployTarget.Venv(Path.of(detection.executable()), Path.of(p));
        } else {
            target = new DeployTarget.Global(Path.of(detection.executable()));
        }
        installBtn.setDisable(true);
        progress.setProgress(0);
        progress.setVisible(true);
        logArea.clear();
        logArea.setVisible(true);
        logArea.setManaged(true);

        PluginTask<DeployResult> task = new PluginTask<>() {
            @Override protected DeployResult call() throws Exception {
                return new DeployService().install(selectedZip, target, line -> {
                    log.log(line);
                    javafx.application.Platform.runLater(() -> logArea.appendText(line + "\n"));
                });
            }
        };
        task.setOnSucceeded(e -> Platform.runLater(() -> {
            DeployResult r = task.getValue();
            progress.setProgress(1);
            installBtn.setDisable(false);
            if (r.ok()) {
                GlassNotification.toast(this, GlassNotification.Type.SUCCESS,
                        java.text.MessageFormat.format(I18n.get("opb.deploy.done"), r.installed()));
            } else {
                GlassNotification.toast(this, GlassNotification.Type.WARNING,
                        java.text.MessageFormat.format(I18n.get("opb.deploy.partial"), r.installed(), r.failed()));
            }
        }));
        task.setOnFailed(e -> Platform.runLater(() -> {
            log.log("ERROR: " + task.getException().getMessage());
            logArea.appendText("ERROR: " + task.getException().getMessage() + "\n");
            GlassNotification.toast(this, GlassNotification.Type.ERROR, I18n.get("opb.deploy.failed"));
            installBtn.setDisable(false);
        }));
        Thread t = new Thread(task, "OfflinePython-Deploy");
        t.setDaemon(true);
        t.start();
    }

    @Override public String title() { return I18n.get("opb.deploy.title"); }
}
```

- [ ] **Step 2: Compile (CommandShell + DeployPanel together)**

Run: `mvn -q test-compile -pl SwissKitJ-Plugin-OfflinePython`
Expected: BUILD SUCCESS — both `DeployPanel` and the `CommandShell` changes compile.

- [ ] **Step 3: Commit (Task 5 + Task 6 together)**

```bash
cd SwissKitJ-Plugin-OfflinePython
git add src/main/resources/i18n/messages_zh.properties \
        src/main/resources/i18n/messages.properties \
        src/main/java/plugin/swisskit/offlinepython/ui/CommandShell.java \
        src/main/java/plugin/swisskit/offlinepython/ui/panel/DeployPanel.java
git commit -m "feat(Deploy): add deploy nav + DeployPanel UI (select/preview/target/install)"
```

> **Note on CommandPanel base ctor:** `CommandPanel` is the existing base. Verify its constructor signature — `super(log, null)` assumes `CommandPanel(OpbLogger, ProjectContext)`. If it requires a non-null ProjectContext, change to `super(log, new ProjectContext())`. Read `CommandPanel.java` first to confirm.

---

### Task 7: ConfigPanel auto-package toggle + BuildVerifyPanel package button

**Files:**
- Modify: `src/main/java/plugin/swisskit/offlinepython/ui/panel/ConfigPanel.java`
- Modify: `src/main/java/plugin/swisskit/offlinepython/ui/panel/BuildVerifyPanel.java`

**Interfaces:**
- Consumes: `BuildConfig.Bundle.autoPackage`, `PackageService`, `PluginTask`
- Produces: a checkbox wired to `cfg.bundle.autoPackage`; a package button shown after successful build

- [ ] **Step 1: Add auto-package checkbox to ConfigPanel**

In `ConfigPanel.buildUi()`, the options HBox currently is:
```java
        HBox opts = new HBox(18, recursive, wheelFirst, upgradePip);
```
Add a checkbox after the field declarations (near `recursive`/`wheelFirst`):
```java
    private final CheckBox autoPackage = new CheckBox("构建后自动打包");
```
And include it in the HBox:
```java
        HBox opts = new HBox(18, recursive, wheelFirst, upgradePip, autoPackage);
```

In `loadFromProject()`, after loading config, sync the checkbox (read `cfg.bundle.autoPackage`). Add near the top of `loadFromProject`:
```java
        if (project.getConfig() != null && project.getConfig().getBundle() != null) {
            autoPackage.setSelected(project.getConfig().getBundle().isAutoPackage());
        }
```

In `persist(Path dir)`, persist the checkbox state. After the existing `project.getConfig().getDownload()...` block, add:
```java
            if (project.getConfig().getBundle() != null) {
                project.getConfig().getBundle().setAutoPackage(autoPackage.isSelected());
            }
```

- [ ] **Step 2: Add package button to BuildVerifyPanel**

In `BuildVerifyPanel`, add fields:
```java
    private final Button packageBtn = UiUtils.glassBtn("📦 打包成 ZIP", false);
```

In the constructor, after building the build section and before `getChildren().add(buildSection)`, add the package button into the build section (e.g. in a new row under tiles). Set it hidden by default:
```java
        packageBtn.setVisible(false);
        packageBtn.setManaged(false);
        packageBtn.setOnAction(e -> runPackage());
        HBox pkgRow = new HBox(8, packageBtn);
        buildSection.getChildren().add(pkgRow);
```

Add the `runPackage()` method:
```java
    private void runPackage() {
        Path dir = project.getProjectDir();
        if (dir == null) return;
        packageBtn.setDisable(true);
        PluginTask<Path> t = new PluginTask<>() {
            @Override protected Path call() throws Exception {
                BuildConfig cfg = JsonStore.load(dir.resolve("config.json"), BuildConfig.class);
                return new PackageService(log).packageBundle(dir, cfg);
            }
        };
        t.setOnSucceeded(e -> Platform.runLater(() -> {
            Path zip = t.getValue();
            log.log("打包完成: " + zip);
            GlassNotification.toast(this, GlassNotification.Type.SUCCESS, "已打包: " + zip.getFileName());
            packageBtn.setDisable(false);
        }));
        t.setOnFailed(e -> Platform.runLater(() -> {
            log.log("打包失败: " + t.getException().getMessage());
            GlassNotification.toast(this, GlassNotification.Type.ERROR, "打包失败");
            packageBtn.setDisable(false);
        }));
        new Thread(t, "OfflinePython-Package").start();
    }
```

Add imports at top:
```java
import plugin.swisskit.offlinepython.command.PackageService;
```

In the build task's `setOnSucceeded` handler (where `renderTiles(s)` is called), show the package button after a successful build:
```java
            packageBtn.setVisible(true);
            packageBtn.setManaged(true);
            // 自动打包
            if (project.getConfig() != null && project.getConfig().getBundle() != null
                    && project.getConfig().getBundle().isAutoPackage()) {
                runPackage();
            }
```

- [ ] **Step 3: Compile + run full tests**

Run: `mvn -q test -pl SwissKitJ-Plugin-OfflinePython`
Expected: all green.

- [ ] **Step 4: Commit**

```bash
cd SwissKitJ-Plugin-OfflinePython
git add src/main/java/plugin/swisskit/offlinepython/ui/panel/ConfigPanel.java \
        src/main/java/plugin/swisskit/offlinepython/ui/panel/BuildVerifyPanel.java
git commit -m "feat(Deploy): wire auto-package toggle + post-build package button"
```

---

### Task 8: End-to-end smoke + cleanup

**Files:** none (manual verification + doc note)

- [ ] **Step 1: Build the plugin jar**

Run: `mvn -q clean package -pl SwissKitJ-Plugin-OfflinePython -DskipTests`
Expected: BUILD SUCCESS, jar produced in `target/`.

- [ ] **Step 2: Run full test suite one final time**

Run: `mvn -q test -pl SwissKitJ-Plugin-OfflinePython`
Expected: all green.

- [ ] **Step 3: Manual smoke checklist (document in commit message)**

Verify by inspection / runtime (if a JavaFX runtime is available):
1. Nav shows 4 items: 配置 / 构建校验 / 部署 / 工具。
2. ConfigPanel has 「构建后自动打包」勾选;勾选 + 保存 → `config.json` 出现 `bundle.autoPackage=true`。
3. 构建成功后出现 「📦 打包成 ZIP」按钮;点击 → `output/<name>-bundle-<ts>.zip` 生成;ZIP 内含 `bundle/{manifest.json, SHA256SUMS, wheels/*.whl}`。
4. 切到「部署」页:显示本机环境(若装了 Python);选 ZIP → 预览表显示适配本机的 wheel;选「全局」或「venv」→ 点「开始安装」→ 日志流式回显 → toast 结果。
5. venv 模式:填路径 → 安装后 `venv` 目录生成,`venv/bin/python`(或 Scripts\python.exe)能 import 装上的包。

- [ ] **Step 4: Commit any cleanup**

```bash
cd SwissKitJ-Plugin-OfflinePython
git add -A
git commit -m "chore(Deploy): end-to-end smoke verified" --allow-empty
```

---

## Self-Review(写完后自查)

**1. Spec coverage:**
- §2 ZIP 格式 → Task 3 (PackageService) ✓
- §3 平台匹配 → Task 1 (PlatformMatcher) ✓
- §5 配置 + 打包 → Task 3 ✓
- §6 部署 UI → Task 6 (DeployPanel) ✓
- §7 部署服务 → Task 4 (DeployService) ✓
- §8 集成点(nav/build/config) → Task 5 + Task 7 ✓
- §4 本机检测 → Task 1 detectHost + Task 6 detectEnv ✓

**2. Placeholder scan:** 无 TBD/TODO;所有代码块完整;命令带预期输出。✓

**3. Type consistency:** `HostTags` / `Bundle` / `DeployTarget` / `DeployResult` 在定义任务与使用任务中签名一致;`getBundle()`/`isAutoPackage()` 与 Lombok `@Data` 生成一致;`ProcessRunner.run(List,Consumer)` 与现有签名一致。✓

**4. 风险点(执行者注意):**
- Task 2/4 依赖 `JsonStore.toJson` / `fromJson` 公开方法——先读 `JsonStore.java` 确认,缺则补。
- Task 6 的 `CommandPanel` 基类构造签名需先读确认(`super(log, null)` vs `super(log, new ProjectContext())`)。
- Mockito 若已是 test 依赖则跳过 Task 4 Step 3。
- `tray-arrow-down` MDI 图标名需确认存在于 `MdiIconUtil`;若不存在换成已知可用的下载类图标(如 `download` / `package-down`)。
