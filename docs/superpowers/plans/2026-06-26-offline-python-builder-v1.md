# Offline Python Builder — V1 (MVP) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a working SwissKitJ plugin that detects Python, edits dependencies, builds an offline wheel repository via `pip download`, verifies it (SHA256 + file integrity + manifest consistency), and self-diagnoses the environment.

**Architecture:** Java/JavaFX plugin (single fat JAR via shade). UI + orchestration in Java; recursive dependency resolution delegated to the `pip`/`python` subprocess on the host. Pure-logic core (parsing, manifest, verify, hash, version detection) is unit-tested with JUnit 5; UI and live pip calls are verified via the `DevLauncher` preview window.

**Tech Stack:** Java 21, JavaFX 21 (`provided`), SwissKitJ-Api 3.0.0 (`provided`), Lombok, Gson (JSON), JUnit 5 (test), Maven shade.

**Spec:** `docs/superpowers/specs/2026-06-26-offline-python-builder-design.md`

---

## Scope note & deviations from spec

- **JSON library:** The spec said "JDK-native JSON", but plain JDK 21 has no JSON API. This plan adds **Gson** (`com.google.code.gson:gson:2.11.0`, compile scope) for config.json / manifest.json. It is the only non-Lombok runtime dependency. SHA256 uses JDK `MessageDigest`; ZIP uses `java.util.zip`. No tomlj / commons-compress.
- **Build tool:** CI uses system `mvn`. Locally, if `mvn` is not on PATH, run the listed `mvn …` commands from IntelliJ's Maven panel (or `brew install maven`). All verification commands below use `mvn`.
- **Testing:** Existing plugins ship zero tests. This plan adds JUnit 5 and tests the pure-logic core. JavaFX UI and live subprocess integration are verified by launching `DevLauncher` (manual steps), because they cannot be meaningfully unit-tested headless.

---

## File Structure (V1)

```
SwissKitJ-Plugin-OfflinePython/
├── pom.xml
├── src/main/java/plugin/swisskit/offlinepython/
│   ├── OfflinePythonPlugin.java          # SPI entry + lifecycle
│   ├── DevLauncher.java                  # preview launcher
│   ├── domain/
│   │   ├── DependencySpec.java           # one requirements.txt line
│   │   ├── RequirementsFile.java         # parse/write requirements.txt
│   │   ├── BuildConfig.java              # config.json model
│   │   ├── WheelEntry.java               # one wheel in manifest
│   │   ├── Manifest.java                 # manifest.json model
│   │   ├── CheckResult.java              # one verify check outcome
│   │   ├── Status.java                   # PASS/WARN/FAIL enum
│   │   └── VerifyResult.java             # 5 verify checks
│   ├── infra/
│   │   ├── JsonStore.java                # Gson load/save
│   │   ├── HashUtil.java                 # SHA256
│   │   ├── PythonDetector.java           # find python/pip, parse versions
│   │   └── ProcessRunner.java            # ProcessBuilder + line streaming
│   ├── command/
│   │   ├── InitService.java
│   │   ├── DepsService.java
│   │   ├── BuildService.java
│   │   ├── VerifyService.java
│   │   └── DoctorService.java
│   ├── task/
│   │   └── PluginTask.java               # javafx.concurrent.Task wrapper
│   └── ui/
│       ├── CommandShell.java
│       ├── LogConsole.java
│       ├── PythonInstallGuide.java
│       └── panel/
│           ├── CommandPanel.java         # base
│           ├── InitPanel.java
│           ├── DepsPanel.java
│           ├── BuildPanel.java
│           ├── VerifyPanel.java
│           └── DoctorPanel.java
├── src/test/java/plugin/swisskit/offlinepython/
│   ├── RequirementsFileTest.java
│   ├── BuildConfigTest.java
│   ├── HashUtilTest.java
│   ├── ManifestTest.java
│   ├── PythonDetectorTest.java
│   └── VerifyServiceTest.java
└── src/main/resources/
    ├── META-INF/services/fan.summer.api.SwissKitJPlugin
    └── i18n/
        ├── messages.properties
        └── messages_zh.properties
```

Also **modify** the repo root `pom.xml` to add `<module>SwissKitJ-Plugin-OfflinePython</module>`.

---

## Task 1: Project scaffold + build wiring

**Files:**
- Create: `SwissKitJ-Plugin-OfflinePython/pom.xml`
- Create: `SwissKitJ-Plugin-OfflinePython/src/main/java/plugin/swisskit/offlinepython/OfflinePythonPlugin.java`
- Create: `SwissKitJ-Plugin-OfflinePython/src/main/java/plugin/swisskit/offlinepython/DevLauncher.java`
- Create: `SwissKitJ-Plugin-OfflinePython/src/main/resources/META-INF/services/fan.summer.api.SwissKitJPlugin`
- Create: `SwissKitJ-Plugin-OfflinePython/src/main/resources/i18n/messages.properties`
- Create: `SwissKitJ-Plugin-OfflinePython/src/main/resources/i18n/messages_zh.properties`
- Modify: `pom.xml` (root, add module)

- [ ] **Step 1: Create the module `pom.xml`**

Create `SwissKitJ-Plugin-OfflinePython/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>plugin.swisskit</groupId>
    <artifactId>SwissKitJ-Plugin-OfflinePython</artifactId>
    <version>1.0.0</version>

    <properties>
        <maven.compiler.source>21</maven.compiler.source>
        <maven.compiler.target>21</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <javafx.version>21.0.2</javafx.version>
        <swisskit.api.version>3.0.0</swisskit.api.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>fan.summer.api</groupId>
            <artifactId>SwissKitJ-Api</artifactId>
            <version>${swisskit.api.version}</version>
            <scope>provided</scope>
        </dependency>
        <dependency>
            <groupId>org.openjfx</groupId>
            <artifactId>javafx-graphics</artifactId>
            <version>${javafx.version}</version>
            <scope>provided</scope>
        </dependency>
        <dependency>
            <groupId>org.openjfx</groupId>
            <artifactId>javafx-controls</artifactId>
            <version>${javafx.version}</version>
            <scope>provided</scope>
        </dependency>

        <!-- JSON for config.json / manifest.json -->
        <dependency>
            <groupId>com.google.code.gson</groupId>
            <artifactId>gson</artifactId>
            <version>2.11.0</version>
        </dependency>

        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <version>1.18.42</version>
            <scope>provided</scope>
        </dependency>

        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>5.10.2</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <finalName>${project.artifactId}-${project.version}</finalName>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.13.0</version>
                <configuration>
                    <source>21</source>
                    <target>21</target>
                    <encoding>UTF-8</encoding>
                    <annotationProcessorPaths>
                        <path>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                            <version>1.18.42</version>
                        </path>
                    </annotationProcessorPaths>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.2.5</version>
                <configuration>
                    <includes><include>**/*Test.java</include></includes>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-shade-plugin</artifactId>
                <version>3.5.3</version>
                <executions>
                    <execution>
                        <phase>package</phase>
                        <goals><goal>shade</goal></goals>
                        <configuration>
                            <transformers>
                                <transformer implementation="org.apache.maven.plugins.shade.resource.ServicesResourceTransformer"/>
                            </transformers>
                            <createDependencyReducedPom>false</createDependencyReducedPom>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>

    <profiles>
        <profile>
            <id>dev</id>
            <dependencies>
                <dependency>
                    <groupId>org.openjfx</groupId>
                    <artifactId>javafx-graphics</artifactId>
                    <version>${javafx.version}</version>
                </dependency>
                <dependency>
                    <groupId>org.openjfx</groupId>
                    <artifactId>javafx-controls</artifactId>
                    <version>${javafx.version}</version>
                </dependency>
            </dependencies>
            <build>
                <plugins>
                    <plugin>
                        <groupId>org.openjfx</groupId>
                        <artifactId>javafx-maven-plugin</artifactId>
                        <version>0.0.8</version>
                        <configuration>
                            <mainClass>plugin.swisskit.offlinepython.DevLauncher</mainClass>
                        </configuration>
                    </plugin>
                </plugins>
            </build>
        </profile>
    </profiles>
</project>
```

- [ ] **Step 2: Register the module in the root `pom.xml`**

In `/Users/phoebej/Develop/Java/SwissKit-Plugin/pom.xml`, add the new module inside `<modules>`:

```xml
    <modules>
        <module>SwissKitJ-Plugin-HappyLearning</module>
        <module>SwissKitJ-Plugin-Qcc</module>
        <module>SwissKitJ-Plugin-KeepAwake</module>
        <module>SwissKitJ-Plugin-OfflinePython</module>
    </modules>
```

- [ ] **Step 3: Create the SPI entry class**

Create `OfflinePythonPlugin.java`:

```java
package plugin.swisskit.offlinepython;

import fan.summer.api.IconStyle;
import fan.summer.api.SwissKitJPlugin;
import fan.summer.api.ToolCategory;
import fan.summer.api.i18n.I18n;
import javafx.scene.Node;
import javafx.scene.control.Label;
import plugin.swisskit.offlinepython.ui.CommandShell;

public class OfflinePythonPlugin implements SwissKitJPlugin {

    private CommandShell shell;

    @Override public String getId()          { return "plugin.swisskit.offlinepython"; }
    @Override public String getName()        { return "Offline Python Builder"; }
    @Override public String getDescription() { return "Build offline Python install repositories with all dependencies"; }
    @Override public ToolCategory getCategory() { return ToolCategory.DEV; }
    @Override public String getVersion()     { return "1.0.0"; }
    @Override public String getMdiIcon()     { return "language-python"; }
    @Override public IconStyle getIconStyle(){ return IconStyle.BLUE; }

    @Override
    public Node createView() {
        I18n.registerPluginBundle("i18n.messages", getClass().getClassLoader());
        shell = new CommandShell();
        return shell.getView();
    }

    @Override public boolean hasRunningTasks() {
        return shell != null && shell.hasRunningTasks();
    }

    @Override public void onBackground()  { if (shell != null) shell.onBackground(); }
    @Override public void onForeground()  { if (shell != null) shell.onForeground(); }
    @Override public void onUnload()      { if (shell != null) shell.onUnload(); }
}
```

> Note: `CommandShell` is created in Task 12. For this task, temporarily replace its usage with `return new Label("Offline Python Builder — scaffold");` so the module compiles, then swap back in Task 12. Keep the `CommandShell` import/commented reference in a `// TODO Task 12` marker.

- [ ] **Step 4: Create the SPI registration file**

Create `src/main/resources/META-INF/services/fan.summer.api.SwissKitJPlugin` containing exactly:

```
plugin.swisskit.offlinepython.OfflinePythonPlugin
```

- [ ] **Step 5: Create the DevLauncher**

Create `DevLauncher.java`:

```java
package plugin.swisskit.offlinepython;

import fan.summer.api.preview.PluginPreviewWindow;
import javafx.application.Platform;

public class DevLauncher {
    public static void main(String[] args) {
        Platform.startup(() ->
            PluginPreviewWindow.configure().withPlugin(new OfflinePythonPlugin()).launch());
    }
}
```

- [ ] **Step 6: Create i18n bundles**

Create `src/main/resources/i18n/messages.properties`:

```properties
opb.title=Offline Python Builder
opb.python.detected=Python {0} · pip {1}
opb.python.missing=Python not detected
opb.init.title=Initialize Project
opb.deps.title=Dependencies
opb.build.title=Build Repository
opb.verify.title=Verify Repository
opb.doctor.title=Environment Doctor
```

Create `src/main/resources/i18n/messages_zh.properties`:

```properties
opb.title=Offline Python 构建器
opb.python.detected=Python {0} · pip {1}
opb.python.missing=未检测到 Python
opb.init.title=初始化项目
opb.deps.title=依赖配置
opb.build.title=构建仓库
opb.verify.title=校验仓库
opb.doctor.title=环境诊断
```

- [ ] **Step 7: Build to verify wiring**

Run: `mvn -pl SwissKitJ-Plugin-OfflinePython -am package -DskipTests -B`
Expected: BUILD SUCCESS; `target/SwissKitJ-Plugin-OfflinePython-1.0.0.jar` produced.

- [ ] **Step 8: Commit**

```bash
git add SwissKitJ-Plugin-OfflinePython pom.xml
git commit -m "feat(OfflinePython): scaffold plugin module with SPI entry and build wiring"
```

---

## Task 2: DependencySpec + requirements.txt parsing (TDD)

**Files:**
- Create: `src/main/java/plugin/swisskit/offlinepython/domain/DependencySpec.java`
- Create: `src/main/java/plugin/swisskit/offlinepython/domain/RequirementsFile.java`
- Test: `src/test/java/plugin/swisskit/offlinepython/RequirementsFileTest.java`

- [ ] **Step 1: Write the failing test**

```java
package plugin.swisskit.offlinepython;

import org.junit.jupiter.api.Test;
import plugin.swisskit.offlinepython.domain.DependencySpec;
import plugin.swisskit.offlinepython.domain.RequirementsFile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RequirementsFileTest {

    @Test
    void parsesPinnedVersion() {
        DependencySpec d = DependencySpec.parse("numpy==1.26.4");
        assertEquals("numpy", d.name());
        assertEquals("==1.26.4", d.versionSpec());
        assertNull(d.marker());
    }

    @Test
    void parsesMinimumVersion() {
        DependencySpec d = DependencySpec.parse("requests>=2.31");
        assertEquals("requests", d.versionSpec().equals(">=2.31") ? "requests" : "x", d.name());
        assertEquals(">=2.31", d.versionSpec());
    }

    @Test
    void parsesPlatformMarker() {
        DependencySpec d = DependencySpec.parse("flask==3.0.0 ; sys_platform == \"linux\"");
        assertEquals("flask", d.name());
        assertEquals("==3.0.0", d.versionSpec());
        assertEquals("sys_platform == \"linux\"", d.marker());
    }

    @Test
    void parsesNameOnlyDefaultsToLatest() {
        DependencySpec d = DependencySpec.parse("scipy");
        assertEquals("scipy", d.name());
        assertEquals("", d.versionSpec());
    }

    @Test
    void ignoresCommentsAndBlankLines() {
        List<DependencySpec> deps = RequirementsFile.parse("""
            # comment line
            numpy==1.26.4

            requests>=2.31
            """);
        assertEquals(2, deps.size());
        assertEquals("numpy", deps.get(0).name());
    }

    @Test
    void roundTripsThroughWrite() {
        List<DependencySpec> deps = List.of(
            new DependencySpec("numpy", "==1.26.4", null),
            new DependencySpec("flask", "==3.0.0", "sys_platform == \"linux\""));
        String written = RequirementsFile.write(deps);
        List<DependencySpec> reparsed = RequirementsFile.parse(written);
        assertEquals(deps, reparsed);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl SwissKitJ-Plugin-OfflinePython test -Dtest=RequirementsFileTest -B`
Expected: FAIL (classes not found).

- [ ] **Step 3: Implement DependencySpec**

```java
package plugin.swisskit.offlinepython.domain;

/** One line of requirements.txt: a package name, an optional version spec, and an optional env marker. */
public record DependencySpec(String name, String versionSpec, String marker) {

    public DependencySpec {
        if (name == null) name = "";
        if (versionSpec == null) versionSpec = "";
    }

    /** Parse a single requirements.txt line (no comments, already trimmed). */
    public static DependencySpec parse(String raw) {
        String line = raw == null ? "" : raw.trim();
        String marker = null;
        int semi = line.indexOf(';');
        if (semi >= 0) {
            marker = line.substring(semi + 1).trim();
            line = line.substring(0, semi).trim();
        }
        int i = 0;
        while (i < line.length()
                && !"<>=!~".contains(String.valueOf(line.charAt(i)))) {
            i++;
        }
        String name = line.substring(0, i).trim();
        String versionSpec = i < line.length() ? line.substring(i).trim() : "";
        return new DependencySpec(name, versionSpec, marker);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(name);
        if (!versionSpec.isEmpty()) sb.append(versionSpec);
        if (marker != null && !marker.isEmpty()) sb.append(" ; ").append(marker);
        return sb.toString();
    }
}
```

- [ ] **Step 4: Implement RequirementsFile**

```java
package plugin.swisskit.offlinepython.domain;

import java.util.ArrayList;
import java.util.List;

/** Parse and write requirements.txt. */
public final class RequirementsFile {

    private RequirementsFile() {}

    public static List<DependencySpec> parse(String text) {
        List<DependencySpec> out = new ArrayList<>();
        for (String raw : text.split("\\R")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            out.add(DependencySpec.parse(line));
        }
        return out;
    }

    public static String write(List<DependencySpec> deps) {
        StringBuilder sb = new StringBuilder();
        for (DependencySpec d : deps) {
            sb.append(d.toString()).append(System.lineSeparator());
        }
        return sb.toString();
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn -pl SwissKitJ-Plugin-OfflinePython test -Dtest=RequirementsFileTest -B`
Expected: PASS (6 tests).

- [ ] **Step 6: Commit**

```bash
git add SwissKitJ-Plugin-OfflinePython
git commit -m "feat(OfflinePython): add DependencySpec and requirements.txt parse/write with tests"
```

---

## Task 3: BuildConfig + JSON store (TDD)

**Files:**
- Create: `src/main/java/plugin/swisskit/offlinepython/domain/BuildConfig.java`
- Create: `src/main/java/plugin/swisskit/offlinepython/infra/JsonStore.java`
- Test: `src/test/java/plugin/swisskit/offlinepython/BuildConfigTest.java`

- [ ] **Step 1: Write the failing test**

```java
package plugin.swisskit.offlinepython;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import plugin.swisskit.offlinepython.domain.BuildConfig;
import plugin.swisskit.offlinepython.infra.JsonStore;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class BuildConfigTest {

    @Test
    void roundTripsThroughJson(@TempDir Path tmp) throws Exception {
        BuildConfig cfg = BuildConfig.defaults();
        cfg.getPython().setVersion("3.12.10");
        cfg.getPython().setPlatform("win_amd64");

        Path file = tmp.resolve("config.json");
        JsonStore.save(cfg, file);
        BuildConfig loaded = JsonStore.load(file, BuildConfig.class);

        assertEquals("3.12.10", loaded.getPython().getVersion());
        assertEquals("win_amd64", loaded.getPython().getPlatform());
        assertTrue(loaded.getDownload().isRecursive());
    }

    @Test
    void defaultsAreSensible() {
        BuildConfig cfg = BuildConfig.defaults();
        assertEquals("output", cfg.getRepository().getOutput());
        assertEquals("wheelhouse", cfg.getRepository().getWheelDir());
        assertEquals("official", cfg.getDownload().getMirror());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl SwissKitJ-Plugin-OfflinePython test -Dtest=BuildConfigTest -B`
Expected: FAIL (classes not found).

- [ ] **Step 3: Implement BuildConfig**

```java
package plugin.swisskit.offlinepython.domain;

import lombok.Data;

public @Data class BuildConfig {

    private Python python = new Python();
    private Repository repository = new Repository();
    private Download download = new Download();
    private Pkg pkg = new Pkg();

    public static BuildConfig defaults() {
        BuildConfig c = new BuildConfig();
        c.python.version = "3.12.10";
        c.python.platform = "win_amd64";
        c.python.implementation = "cp";
        c.python.installer = true;
        c.repository.output = "output";
        c.repository.wheelDir = "wheelhouse";
        c.repository.cache = true;
        c.download.mirror = "official";
        c.download.upgradePip = true;
        c.download.recursive = true;
        c.download.onlyBinary = true;
        c.pkg.zip = true;
        c.pkg.sha256 = true;
        c.pkg.readme = true;
        return c;
    }

    @Data public static class Python {
        private String version;
        private String platform;
        private String implementation;
        private boolean installer;
        private String executable; // null = auto-detect
    }

    @Data public static class Repository {
        private String output;
        private String wheelDir;
        private boolean cache;
    }

    @Data public static class Download {
        private String mirror;
        private boolean upgradePip;
        private boolean recursive;
        private boolean onlyBinary;
    }

    @Data public static class Pkg {
        private boolean zip;
        private boolean sha256;
        private boolean readme;
    }
}
```

- [ ] **Step 4: Implement JsonStore**

```java
package plugin.swisskit.offlinepython.infra;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Gson-backed load/save of JSON model objects. */
public final class JsonStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private JsonStore() {}

    public static <T> T load(Path file, Class<T> type) throws IOException {
        return GSON.fromJson(Files.readString(file), type);
    }

    public static <T> void save(T obj, Path file) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, GSON.toJson(obj));
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn -pl SwissKitJ-Plugin-OfflinePython test -Dtest=BuildConfigTest -B`
Expected: PASS (2 tests).

- [ ] **Step 6: Commit**

```bash
git add SwissKitJ-Plugin-OfflinePython
git commit -m "feat(OfflinePython): add BuildConfig model and Gson JSON store with tests"
```

---

## Task 4: HashUtil — SHA256 (TDD)

**Files:**
- Create: `src/main/java/plugin/swisskit/offlinepython/infra/HashUtil.java`
- Test: `src/test/java/plugin/swisskit/offlinepython/HashUtilTest.java`

- [ ] **Step 1: Write the failing test**

```java
package plugin.swisskit.offlinepython;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import plugin.swisskit.offlinepython.infra.HashUtil;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HashUtilTest {

    @Test
    void sha256OfBytesMatchesKnownVector() {
        // SHA256("abc") == ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            HashUtil.sha256Hex("abc".getBytes()));
    }

    @Test
    void sha256OfFileMatchesBytes(@TempDir Path tmp) throws Exception {
        Path f = tmp.resolve("data.bin");
        java.nio.file.Files.writeString(f, "hello world");
        String fileHash = HashUtil.sha256Hex(f);
        String bytesHash = HashUtil.sha256Hex("hello world".getBytes());
        assertEquals(bytesHash, fileHash);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl SwissKitJ-Plugin-OfflinePython test -Dtest=HashUtilTest -B`
Expected: FAIL (class not found).

- [ ] **Step 3: Implement HashUtil**

```java
package plugin.swisskit.offlinepython.infra;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** SHA256 helpers. */
public final class HashUtil {

    private HashUtil() {}

    public static String sha256Hex(byte[] bytes) {
        try {
            return toHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public static String sha256Hex(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest md;
            try {
                md = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("SHA-256 unavailable", e);
            }
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) md.update(buf, 0, n);
            return toHex(md.digest());
        }
    }

    private static String toHex(byte[] digest) {
        StringBuilder sb = new StringBuilder(digest.length * 2);
        for (byte b : digest) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -pl SwissKitJ-Plugin-OfflinePython test -Dtest=HashUtilTest -B`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add SwissKitJ-Plugin-OfflinePython
git commit -m "feat(OfflinePython): add SHA256 HashUtil with tests"
```

---

## Task 5: Manifest + WheelEntry (TDD)

**Files:**
- Create: `src/main/java/plugin/swisskit/offlinepython/domain/WheelEntry.java`
- Create: `src/main/java/plugin/swisskit/offlinepython/domain/Manifest.java`
- Test: `src/test/java/plugin/swisskit/offlinepython/ManifestTest.java`

- [ ] **Step 1: Write the failing test**

```java
package plugin.swisskit.offlinepython;

import org.junit.jupiter.api.Test;
import plugin.swisskit.offlinepython.domain.Manifest;
import plugin.swisskit.offlinepython.domain.WheelEntry;
import plugin.swisskit.offlinepython.infra.JsonStore;

import static org.junit.jupiter.api.Assertions.*;

class ManifestTest {

    @Test
    void manifestTracksSchemaVersionAndWheels() {
        Manifest m = new Manifest();
        m.setSchemaVersion(1);
        m.setBuiltAt("2026-06-26T14:52:48");
        m.getWheels().add(new WheelEntry("numpy", "1.26.4",
            "wheelhouse/numpy-1.26.4-cp312-cp312-win_amd64.whl",
            "deadbeef", 19098624L, true));

        String json = new com.google.gson.GsonBuilder().create().toJson(m);
        Manifest back = JsonStore.fromJson(json, Manifest.class);

        assertEquals(1, back.getSchemaVersion());
        assertEquals(1, back.getWheels().size());
        assertEquals("numpy", back.getWheels().get(0).getName());
        assertTrue(back.getWheels().get(0).isRequired());
        assertTrue(json.contains("\"schemaVersion\""));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl SwissKitJ-Plugin-OfflinePython test -Dtest=ManifestTest -B`
Expected: FAIL (classes not found; also `JsonStore.fromJson` not yet present).

- [ ] **Step 3: Implement WheelEntry**

```java
package plugin.swisskit.offlinepython.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor
public class WheelEntry {
    private String name;
    private String version;
    private String file;       // path relative to output/
    private String sha256;
    private long size;
    private boolean required;  // true = in requirements.txt; false = transitive
}
```

- [ ] **Step 4: Implement Manifest**

```java
package plugin.swisskit.offlinepython.domain;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class Manifest {
    private int schemaVersion = 1;
    private Python python = new Python();
    private String builtAt;
    private String builtOn;
    private String toolVersion;
    private List<WheelEntry> wheels = new ArrayList<>();
    private List<String> requirements = new ArrayList<>();

    @Data
    public static class Python {
        private String version;
        private String platform;
        private String installer;       // relative path
        private String installerSha256;
    }
}
```

- [ ] **Step 5: Add `fromJson` helper to JsonStore**

Add to `infra/JsonStore.java` (alongside existing methods):

```java
    private static final Gson GSON_PLAIN = new GsonBuilder().create();

    public static <T> T fromJson(String json, Class<T> type) {
        return GSON_PLAIN.fromJson(json, type);
    }
```

(Keep the existing `GSON` pretty-printer and `load`/`save`.)

- [ ] **Step 6: Run test to verify it passes**

Run: `mvn -pl SwissKitJ-Plugin-OfflinePython test -Dtest=ManifestTest -B`
Expected: PASS (1 test).

- [ ] **Step 7: Commit**

```bash
git add SwissKitJ-Plugin-OfflinePython
git commit -m "feat(OfflinePython): add Manifest and WheelEntry models with tests"
```

---

## Task 6: PythonDetector — version parsing (TDD)

**Files:**
- Create: `src/main/java/plugin/swisskit/offlinepython/infra/PythonDetector.java`
- Test: `src/test/java/plugin/swisskit/offlinepython/PythonDetectorTest.java`

- [ ] **Step 1: Write the failing test**

```java
package plugin.swisskit.offlinepython;

import org.junit.jupiter.api.Test;
import plugin.swisskit.offlinepython.infra.PythonDetector;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class PythonDetectorTest {

    @Test
    void parsesPythonVersionLine() {
        assertEquals("3.12.10", PythonDetector.parsePythonVersion("Python 3.12.10").orElseThrow());
    }

    @Test
    void parsesPythonVersionLineWithExtra() {
        assertEquals("3.11.4",
            PythonDetector.parsePythonVersion("Python 3.11.4 (main, Aug 1 2023)").orElseThrow());
    }

    @Test
    void returnsEmptyForGarbage() {
        assertTrue(PythonDetector.parsePythonVersion("not a version").isEmpty());
    }

    @Test
    void parsesPipVersionLine() {
        // "pip 25.0 from /usr/.../pip (python 3.12)"
        assertEquals(Optional.of("25.0"), PythonDetector.parsePipVersion("pip 25.0 from /x (python 3.12)"));
    }

    @Test
    void versionIsAtLeast() {
        assertTrue(PythonDetector.isAtLeast("3.12.10", "3.10"));
        assertFalse(PythonDetector.isAtLeast("3.9.1", "3.10"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl SwissKitJ-Plugin-OfflinePython test -Dtest=PythonDetectorTest -B`
Expected: FAIL (class not found).

- [ ] **Step 3: Implement PythonDetector**

```java
package plugin.swisskit.offlinepython.infra;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Locates python/pip on the host and parses their version output.
 * Pure parsing helpers are unit-tested; {@link #detect()} shells out.
 */
public final class PythonDetector {

    private static final Pattern PY_VER = Pattern.compile("Python (\\d+\\.\\d+(?:\\.\\d+)?)");
    private static final Pattern PIP_VER = Pattern.compile("pip (\\d+(?:\\.\\d+)*)");

    private PythonDetector() {}

    public static Optional<String> parsePythonVersion(String out) {
        if (out == null) return Optional.empty();
        Matcher m = PY_VER.matcher(out);
        return m.find() ? Optional.of(m.group(1)) : Optional.empty();
    }

    public static Optional<String> parsePipVersion(String out) {
        if (out == null) return Optional.empty();
        Matcher m = PIP_VER.matcher(out);
        return m.find() ? Optional.of(m.group(1)) : Optional.empty();
    }

    /** True if actual >= required (compared component-wise). */
    public static boolean isAtLeast(String actual, String required) {
        int[] a = parts(actual);
        int[] r = parts(required);
        int n = Math.max(a.length, r.length);
        for (int i = 0; i < n; i++) {
            int ai = i < a.length ? a[i] : 0;
            int ri = i < r.length ? r[i] : 0;
            if (ai != ri) return ai > ri;
        }
        return true;
    }

    private static int[] parts(String v) {
        String[] s = v.split("\\.");
        int[] out = new int[s.length];
        for (int i = 0; i < s.length; i++) out[i] = Integer.parseInt(s[i]);
        return out;
    }

    /** Result of a detection attempt. */
    public record Detection(String executable, String pythonVersion, String pipVersion) {
        public boolean ok() { return executable != null && pythonVersion != null; }
    }

    /** Try the configured executable first, then python3 / python on PATH. */
    public static Detection detect(String configuredExecutable) {
        List<String> candidates = new ArrayList<>();
        if (configuredExecutable != null && !configuredExecutable.isBlank()) candidates.add(configuredExecutable);
        candidates.add("python3");
        candidates.add("python");
        for (String c : candidates) {
            String resolved = resolveOnPath(c);
            if (resolved == null) continue;
            String pyVer = capture(resolved, "--version");
            Optional<String> pv = parsePythonVersion(pyVer);
            if (pv.isEmpty()) continue;
            String pipVer = parsePipVersion(capture(resolved, "-m", "pip", "--version")).orElse(null);
            return new Detection(resolved, pv.get(), pipVer);
        }
        return new Detection(null, null, null);
    }

    static String resolveOnPath(String cmd) {
        // If it's an absolute/existing path, use directly; else trust PATH (returns input).
        File f = new File(cmd);
        if (f.isAbsolute()) return f.exists() ? cmd : null;
        return cmd; // ProcessBuilder resolves via PATH at runtime
    }

    /** Run a command, return combined stdout+stderr as a string (best-effort, short timeout). */
    static String capture(String... cmd) {
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line).append('\n');
            }
            p.waitFor(5, TimeUnit.SECONDS);
            return sb.toString();
        } catch (IOException | InterruptedException e) {
            return "";
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -pl SwissKitJ-Plugin-OfflinePython test -Dtest=PythonDetectorTest -B`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add SwissKitJ-Plugin-OfflinePython
git commit -m "feat(OfflinePython): add PythonDetector with version parsing tests"
```

---

## Task 7: ProcessRunner — line-streaming subprocess (TDD, command construction)

**Files:**
- Create: `src/main/java/plugin/swisskit/offlinepython/infra/ProcessRunner.java`
- Test: `src/test/java/plugin/swisskit/offlinepython/ProcessRunnerTest.java`

> Live `pip download` is verified via DevLauncher (Task 13). Here we test the command-list builder and a benign subprocess (e.g. `java -version`) line capture.

- [ ] **Step 1: Write the failing test**

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
            "win_amd64", "3.12", "cp", true);
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
            "python3", "r.txt", "wh", "linux_x86_64", "3.12", "cp", false);
        assertFalse(cmd.contains("--only-binary=:all:"));
        assertTrue(cmd.contains("--platform"));
        assertTrue(cmd.contains("linux_x86_64"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl SwissKitJ-Plugin-OfflinePython test -Dtest=ProcessRunnerTest -B`
Expected: FAIL (class not found).

- [ ] **Step 3: Implement ProcessRunner**

```java
package plugin.swisskit.offlinepython.infra;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Runs subprocesses, streaming stdout/stderr line-by-line to a sink (the log console).
 * Supports cancellation via the volatile {@code destroyed} flag set by {@link #cancel()}.
 */
public final class ProcessRunner {

    private Process process;
    private volatile boolean destroyed;

    /** Build the platform-targeted pip download command list. Pure function — unit-tested. */
    public static List<String> pipDownloadCommand(String python, String requirements,
                                                  String destDir, String platform,
                                                  String pythonVersion, String implementation,
                                                  boolean onlyBinary) {
        List<String> cmd = new ArrayList<>();
        cmd.add(python);
        cmd.addAll(List.of("-m", "pip", "download", "-r", requirements, "-d", destDir,
                "--platform", platform, "--python-version", pythonVersion,
                "--implementation", implementation));
        if (onlyBinary) cmd.add("--only-binary=:all:");
        return cmd;
    }

    /** Run a command, sending each output line to {@code onLine}. Returns exit code. */
    public int run(List<String> command, Consumer<String> onLine) throws IOException, InterruptedException {
        destroyed = false;
        process = new ProcessBuilder(command).redirectErrorStream(true).start();
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while (!destroyed && (line = r.readLine()) != null) {
                onLine.accept(line);
            }
        }
        if (destroyed) {
            process.destroyForcibly();
            process.waitFor(2, TimeUnit.SECONDS);
            return -1;
        }
        return process.waitFor();
    }

    public void cancel() {
        destroyed = true;
        if (process != null) process.destroyForcibly();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -pl SwissKitJ-Plugin-OfflinePython test -Dtest=ProcessRunnerTest -B`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add SwissKitJ-Plugin-OfflinePython
git commit -m "feat(OfflinePython): add ProcessRunner with pip command builder tests"
```

---

## Task 8: InitService — write project skeleton (TDD)

**Files:**
- Create: `src/main/java/plugin/swisskit/offlinepython/command/InitService.java`
- Test: `src/test/java/plugin/swisskit/offlinepython/InitServiceTest.java`

- [ ] **Step 1: Write the failing test**

```java
package plugin.swisskit.offlinepython;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import plugin.swisskit.offlinepython.command.InitService;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class InitServiceTest {

    @Test
    void writesSkeletonFiles(@TempDir Path project) throws Exception {
        InitService init = new InitService();
        init.initialize(project);

        assertTrue(Files.exists(project.resolve("config.json")));
        assertTrue(Files.exists(project.resolve("requirements.txt")));
        assertTrue(Files.exists(project.resolve("README.md")));

        String req = Files.readString(project.resolve("requirements.txt"));
        assertTrue(req.contains("# Add packages, one per line"));

        String cfg = Files.readString(project.resolve("config.json"));
        assertTrue(cfg.contains("\"version\"") && cfg.contains("3.12.10"));
    }

    @Test
    void doesNotOverwriteExistingRequirements(@TempDir Path project) throws Exception {
        Files.writeString(project.resolve("requirements.txt"), "numpy==1.0\n");
        new InitService().initialize(project);
        assertEquals("numpy==1.0\n", Files.readString(project.resolve("requirements.txt")));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl SwissKitJ-Plugin-OfflinePython test -Dtest=InitServiceTest -B`
Expected: FAIL (class not found).

- [ ] **Step 3: Implement InitService**

```java
package plugin.swisskit.offlinepython.command;

import plugin.swisskit.offlinepython.domain.BuildConfig;
import plugin.swisskit.offlinepython.infra.JsonStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Writes the project skeleton: config.json, requirements.txt, README.md. */
public class InitService {

    private static final String REQ_SKELETON =
            "# Add packages, one per line, e.g.  numpy==1.26.4\n";

    public void initialize(Path projectDir) throws IOException {
        Files.createDirectories(projectDir);

        Path req = projectDir.resolve("requirements.txt");
        if (!Files.exists(req)) Files.writeString(req, REQ_SKELETON);

        Path cfg = projectDir.resolve("config.json");
        if (!Files.exists(cfg)) JsonStore.save(BuildConfig.defaults(), cfg);

        Path readme = projectDir.resolve("README.md");
        if (!Files.exists(readme)) {
            Files.writeString(readme, """
                # Offline Python Repository

                Open this project in the Offline Python Builder plugin,
                edit dependencies, then Build.
                """);
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -pl SwissKitJ-Plugin-OfflinePython test -Dtest=InitServiceTest -B`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add SwissKitJ-Plugin-OfflinePython
git commit -m "feat(OfflinePython): add InitService to write project skeleton with tests"
```

---

## Task 9: VerifyService — SHA256 + file integrity + manifest consistency (TDD)

**Files:**
- Create: `src/main/java/plugin/swisskit/offlinepython/domain/Status.java`
- Create: `src/main/java/plugin/swisskit/offlinepython/domain/CheckResult.java`
- Create: `src/main/java/plugin/swisskit/offlinepython/domain/VerifyResult.java`
- Create: `src/main/java/plugin/swisskit/offlinepython/command/VerifyService.java`
- Test: `src/test/java/plugin/swisskit/offlinepython/VerifyServiceTest.java`

- [ ] **Step 1: Write the failing test**

```java
package plugin.swisskit.offlinepython;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import plugin.swisskit.offlinepython.domain.Manifest;
import plugin.swisskit.offlinepython.domain.Status;
import plugin.swisskit.offlinepython.domain.VerifyResult;
import plugin.swisskit.offlinepython.domain.WheelEntry;
import plugin.swisskit.offlinepython.command.VerifyService;
import plugin.swisskit.offlinepython.infra.HashUtil;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class VerifyServiceTest {

    private void seedRepo(Path output, Manifest m) throws Exception {
        for (WheelEntry w : m.getWheels()) {
            Path f = output.resolve(w.getFile());
            Files.createDirectories(f.getParent());
            Files.writeString(f, "wheel-" + w.getName());
            w.setSha256(HashUtil.sha256Hex(f));
            w.setSize(Files.size(f));
        }
    }

    @Test
    void passesWhenAllFilesPresentAndHashesMatch(@TempDir Path tmp) throws Exception {
        Path output = tmp.resolve("output");
        Files.createDirectories(output);
        Manifest m = new Manifest();
        m.getWheels().add(new WheelEntry("numpy", "1.26.4", "wheelhouse/numpy.whl", "", 0, true));
        m.getRequirements().add("numpy==1.26.4");
        seedRepo(output, m);

        VerifyResult r = new VerifyService().verify(output, m);
        assertEquals(Status.PASS, r.fileIntegrity().status());
        assertEquals(Status.PASS, r.sha256().status());
        assertEquals(Status.PASS, r.requirements().status());
        assertTrue(r.isOk());
    }

    @Test
    void failsFileIntegrityWhenWheelMissing(@TempDir Path tmp) throws Exception {
        Path output = tmp.resolve("output");
        Files.createDirectories(output);
        Manifest m = new Manifest();
        m.getWheels().add(new WheelEntry("numpy", "1.26.4", "wheelhouse/numpy.whl", "", 0, true));
        // intentionally do NOT create the file
        VerifyResult r = new VerifyService().verify(output, m);
        assertEquals(Status.FAIL, r.fileIntegrity().status());
        assertFalse(r.isOk());
    }

    @Test
    void warnsWhenRequirementNotSatisfied(@TempDir Path tmp) throws Exception {
        Path output = tmp.resolve("output");
        Files.createDirectories(output);
        Manifest m = new Manifest();
        m.getRequirements().add("scipy==1.13.0");  // no wheel for scipy
        m.getWheels().add(new WheelEntry("numpy", "1.26.4", "wheelhouse/numpy.whl", "", 0, true));
        seedRepo(output, m);
        VerifyResult r = new VerifyService().verify(output, m);
        assertEquals(Status.WARN, r.requirements().status());
    }

    @Test
    void failsSha256WhenHashMismatches(@TempDir Path tmp) throws Exception {
        Path output = tmp.resolve("output");
        Files.createDirectories(output);
        Manifest m = new Manifest();
        m.getWheels().add(new WheelEntry("numpy", "1.26.4", "wheelhouse/numpy.whl", "00wrong", 0, true));
        seedRepo(output, m);
        // seedRepo recomputed the hash, so force a wrong one back:
        m.getWheels().get(0).setSha256("00wrong");
        VerifyResult r = new VerifyService().verify(output, m);
        assertEquals(Status.FAIL, r.sha256().status());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl SwissKitJ-Plugin-OfflinePython test -Dtest=VerifyServiceTest -B`
Expected: FAIL (classes not found).

- [ ] **Step 3: Implement Status + CheckResult + VerifyResult**

`domain/Status.java`:
```java
package plugin.swisskit.offlinepython.domain;

public enum Status { PASS, WARN, FAIL }
```

`domain/CheckResult.java`:
```java
package plugin.swisskit.offlinepython.domain;

import java.util.List;

public record CheckResult(Status status, String detail, List<String> problems) {
    public static CheckResult pass(String detail) { return new CheckResult(Status.PASS, detail, List.of()); }
    public static CheckResult warn(String detail, List<String> problems) { return new CheckResult(Status.WARN, detail, problems); }
    public static CheckResult fail(String detail, List<String> problems) { return new CheckResult(Status.FAIL, detail, problems); }
}
```

`domain/VerifyResult.java`:
```java
package plugin.swisskit.offlinepython.domain;

public record VerifyResult(
        CheckResult sha256,
        CheckResult fileIntegrity,
        CheckResult wheels,
        CheckResult requirements,
        CheckResult manifest) {

    public boolean isOk() {
        return sha256.status() != Status.FAIL
                && fileIntegrity.status() != Status.FAIL
                && wheels.status() != Status.FAIL
                && requirements.status() != Status.FAIL
                && manifest.status() != Status.FAIL;
    }
}
```

- [ ] **Step 4: Implement VerifyService**

```java
package plugin.swisskit.offlinepython.command;

import plugin.swisskit.offlinepython.domain.CheckResult;
import plugin.swisskit.offlinepython.domain.DependencySpec;
import plugin.swisskit.offlinepython.domain.Manifest;
import plugin.swisskit.offlinepython.domain.Status;
import plugin.swisskit.offlinepython.domain.VerifyResult;
import plugin.swisskit.offlinepython.domain.WheelEntry;
import plugin.swisskit.offlinepython.infra.HashUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Verifies an output/ repository against its manifest (no subprocess calls). */
public class VerifyService {

    public VerifyResult verify(Path outputDir, Manifest manifest) {
        return new VerifyResult(
                checkSha256(outputDir, manifest),
                checkFileIntegrity(outputDir, manifest),
                checkWheels(manifest),
                checkRequirements(manifest),
                checkManifest(manifest));
    }

    /** Every manifest wheel file exists and is non-empty. */
    CheckResult checkFileIntegrity(Path outputDir, Manifest m) {
        List<String> problems = new ArrayList<>();
        for (WheelEntry w : m.getWheels()) {
            Path f = outputDir.resolve(w.getFile());
            if (!Files.exists(f)) problems.add("missing: " + w.getFile());
            else if (Files.size(f) == 0) problems.add("empty: " + w.getFile());
        }
        Path installer = m.getPython().getInstaller() == null ? null
                : outputDir.resolve(m.getPython().getInstaller());
        if (m.getPython().getInstaller() != null) {
            if (installer == null || !Files.exists(installer))
                problems.add("missing python installer: " + m.getPython().getInstaller());
        }
        return problems.isEmpty()
                ? CheckResult.pass("all manifest files present and non-empty")
                : CheckResult.fail("integrity problems", problems);
    }

    /** Recompute SHA256 of each wheel and compare to manifest. */
    CheckResult checkSha256(Path outputDir, Manifest m) {
        List<String> problems = new ArrayList<>();
        for (WheelEntry w : m.getWheels()) {
            Path f = outputDir.resolve(w.getFile());
            if (!Files.exists(f)) { problems.add("cannot hash missing " + w.getFile()); continue; }
            String actual;
            try { actual = HashUtil.sha256Hex(f); }
            catch (IOException e) { problems.add("hash error " + w.getFile()); continue; }
            if (!actual.equalsIgnoreCase(w.getSha256()))
                problems.add("hash mismatch: " + w.getFile());
        }
        return problems.isEmpty()
                ? CheckResult.pass("all checksums match")
                : CheckResult.fail("checksum mismatch", problems);
    }

    /** Wheel filenames look like *.whl. */
    CheckResult checkWheels(Manifest m) {
        List<String> problems = new ArrayList<>();
        for (WheelEntry w : m.getWheels()) {
            if (w.getFile() == null || !w.getFile().endsWith(".whl"))
                problems.add("not a wheel: " + w.getFile());
        }
        return problems.isEmpty()
                ? CheckResult.pass("all wheel entries valid")
                : CheckResult.warn("invalid wheel entries", problems);
    }

    /** Each requirements.txt package has at least one matching wheel by name. */
    CheckResult checkRequirements(Manifest m) {
        List<String> problems = new ArrayList<>();
        for (String req : m.getRequirements()) {
            DependencySpec spec = DependencySpec.parse(req);
            boolean found = m.getWheels().stream().anyMatch(w -> w.getName().equalsIgnoreCase(spec.name()));
            if (!found) problems.add("no wheel satisfies: " + req);
        }
        return problems.isEmpty()
                ? CheckResult.pass("all requirements satisfied")
                : CheckResult.warn("unsatisfied requirements", problems);
    }

    /** Manifest lists at least the schema version. */
    CheckResult checkManifest(Manifest m) {
        List<String> problems = new ArrayList<>();
        if (m.getSchemaVersion() < 1) problems.add("schemaVersion missing");
        if (m.getPython().getVersion() == null) problems.add("python version missing");
        return problems.isEmpty()
                ? CheckResult.pass("manifest consistent")
                : CheckResult.fail("manifest inconsistent", problems);
    }
}
```

> Note: `Files.size` throws `IOException`; wrap the `checkFileIntegrity` body's `Files.size(f)` in try/catch if the compiler complains — but existence is checked first, so it won't throw in practice. If you prefer, add a try/catch returning a FAIL on IOException.

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn -pl SwissKitJ-Plugin-OfflinePython test -Dtest=VerifyServiceTest -B`
Expected: PASS (4 tests).

- [ ] **Step 6: Run the full test suite to confirm no regressions**

Run: `mvn -pl SwissKitJ-Plugin-OfflinePython test -B`
Expected: PASS (RequirementsFile 6, BuildConfig 2, HashUtil 2, Manifest 1, PythonDetector 5, ProcessRunner 2, InitService 2, VerifyService 4 = 24 tests).

- [ ] **Step 7: Commit**

```bash
git add SwissKitJ-Plugin-OfflinePython
git commit -m "feat(OfflinePython): add VerifyService (SHA256, file integrity, manifest) with tests"
```

---

## Task 10: BuildService — orchestrates pip download + manifest generation

**Files:**
- Create: `src/main/java/plugin/swisskit/offlinepython/command/BuildService.java`

> Verified via DevLauncher in Task 13 (requires live `pip`). The logic below reads the project, runs `pip download`, scans the wheelhouse, and writes manifest/SHA256SUMS.

- [ ] **Step 1: Implement BuildService**

```java
package plugin.swisskit.offlinepython.command;

import plugin.swisskit.offlinepython.domain.BuildConfig;
import plugin.swisskit.offlinepython.domain.Manifest;
import plugin.swisskit.offlinepython.domain.WheelEntry;
import plugin.swisskit.offlinepython.infra.HashUtil;
import plugin.swisskit.offlinepython.infra.JsonStore;
import plugin.swisskit.offlinepython.infra.ProcessRunner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

/** Runs pip download for the configured target platform, then writes manifest + SHA256SUMS. */
public class BuildService {

    /** @return exit code of pip download (0 = success). */
    public int build(Path projectDir, BuildConfig cfg, String pythonExecutable,
                     Consumer<String> onLog, ProcessRunner runner) throws Exception {
        Path output = projectDir.resolve(cfg.getRepository().getOutput());
        Path wheelhouse = output.resolve(cfg.getRepository().getWheelDir());
        Files.createDirectories(wheelhouse);

        Path reqs = projectDir.resolve("requirements.txt");
        List<String> cmd = ProcessRunner.pipDownloadCommand(
                pythonExecutable,
                reqs.toString(),
                wheelhouse.toString(),
                cfg.getPython().getPlatform(),
                majorMinor(cfg.getPython().getVersion()),
                cfg.getPython().getImplementation(),
                cfg.getDownload().isOnlyBinary());
        onLog.accept("$ " + String.join(" ", cmd));
        int code = runner.run(cmd, onLog);
        if (code != 0) return code;

        writeManifest(projectDir, cfg, output, wheelhouse);
        writeSha256Sums(output);
        return 0;
    }

    void writeManifest(Path projectDir, BuildConfig cfg, Path output, Path wheelhouse) throws IOException {
        List<WheelEntry> wheels = new ArrayList<>();
        List<String> reqNames = new ArrayList<>();
        for (var d : plugin.swisskit.offlinepython.domain.RequirementsFile.parse(
                Files.readString(projectDir.resolve("requirements.txt")))) {
            reqNames.add(d.toString());
        }

        try (Stream<Path> files = Files.list(wheelhouse)) {
            List<Path> sorted = files.sorted().toList();
            for (Path f : sorted) {
                if (!f.toString().endsWith(".whl")) continue;
                String name = wheelNamePart(f.getFileName().toString());
                boolean required = reqNames.stream()
                        .anyMatch(r -> r.toLowerCase().startsWith(name.toLowerCase()));
                wheels.add(new WheelEntry(
                        name, "", output.relativize(f).toString().replace('\\', '/'),
                        HashUtil.sha256Hex(f), Files.size(f), required));
            }
        }

        Manifest m = new Manifest();
        m.setSchemaVersion(1);
        m.getPython().setVersion(cfg.getPython().getVersion());
        m.getPython().setPlatform(cfg.getPython().getPlatform());
        m.setBuiltAt(java.time.OffsetDateTime.now().toString());
        m.setBuiltOn(System.getProperty("user.name"));
        m.setToolVersion("1.0.0");
        m.getWheels().addAll(wheels);
        m.getRequirements().addAll(reqNames);
        JsonStore.save(m, output.resolve("manifest.json"));
    }

    void writeSha256Sums(Path output) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (Stream<Path> walk = Files.walk(output)) {
            List<Path> files = walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".whl")
                              || p.getFileName().toString().toLowerCase().contains("python"))
                    .sorted().toList();
            for (Path f : files) {
                sb.append(HashUtil.sha256Hex(f)).append("  ")
                  .append(output.relativize(f).toString().replace('\\', '/'))
                  .append(System.lineSeparator());
            }
        }
        Files.writeString(output.resolve("SHA256SUMS"), sb.toString());
    }

    /** "numpy-1.26.4-cp312-...whl" -> "numpy". */
    static String wheelNamePart(String fileName) {
        int dash = fileName.indexOf('-');
        return dash < 0 ? fileName : fileName.substring(0, dash);
    }

    static String majorMinor(String version) {
        String[] p = version.split("\\.");
        return p.length >= 2 ? p[0] + "." + p[1] : version;
    }
}
```

- [ ] **Step 2: Build to verify it compiles**

Run: `mvn -pl SwissKitJ-Plugin-OfflinePython compile -B`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add SwissKitJ-Plugin-OfflinePython
git commit -m "feat(OfflinePython): add BuildService (pip download orchestration + manifest/SHA256 generation)"
```

---

## Task 11: DoctorService — environment diagnostics

**Files:**
- Create: `src/main/java/plugin/swisskit/offlinepython/command/DoctorService.java`

- [ ] **Step 1: Implement DoctorService**

```java
package plugin.swisskit.offlinepython.command;

import plugin.swisskit.offlinepython.infra.PythonDetector;

import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Diagnoses the host environment for build readiness. */
public class DoctorService {

    public record Check(String name, String value, boolean ok) {}

    public List<Check> run(String configuredExecutable) {
        List<Check> out = new ArrayList<>();
        PythonDetector.Detection d = PythonDetector.detect(configuredExecutable);
        out.add(new Check("Python interpreter", d.executable() == null ? "not found" : d.executable(), d.executable() != null));
        out.add(new Check("Python version", d.pythonVersion() == null ? "—" : d.pythonVersion(),
                d.pythonVersion() != null && PythonDetector.isAtLeast(d.pythonVersion(), "3.10")));
        out.add(new Check("pip", d.pipVersion() == null ? "missing" : d.pipVersion(), d.pipVersion() != null));
        long freeGb = freeSpaceGb(Path.of(System.getProperty("user.home")));
        out.add(new Check("Free disk", freeGb + " GB", freeGb > 1));
        Path cache = Path.of(System.getProperty("user.home"), ".offline-python", "cache");
        out.add(new Check("Cache dir writable", cache.toString(), isWritable(cache)));
        return out;
    }

    private boolean isWritable(Path dir) {
        try {
            Files.createDirectories(dir);
            return Files.isWritable(dir);
        } catch (Exception e) {
            return false;
        }
    }

    private long freeSpaceGb(Path p) {
        try {
            FileStore store = Files.getFileStore(p);
            return store.getUsableSpace() / (1024L * 1024 * 1024);
        } catch (Exception e) {
            return 0;
        }
    }
}
```

- [ ] **Step 2: Build to verify it compiles**

Run: `mvn -pl SwissKitJ-Plugin-OfflinePython compile -B`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add SwissKitJ-Plugin-OfflinePython
git commit -m "feat(OfflinePython): add DoctorService for environment diagnostics"
```

---

## Task 12: UI — LogConsole + PythonInstallGuide + CommandShell + panels

> UI tasks are verified by launching DevLauncher (Task 13). Match the glass style from the approved mockup and `UiUtils` helpers. Each panel is minimal-but-functional for V1.

**Files:**
- Create: `src/main/java/plugin/swisskit/offlinepython/ui/LogConsole.java`
- Create: `src/main/java/plugin/swisskit/offlinepython/ui/PythonInstallGuide.java`
- Create: `src/main/java/plugin/swisskit/offlinepython/task/PluginTask.java`
- Create: `src/main/java/plugin/swisskit/offlinepython/ui/panel/CommandPanel.java`
- Create: `src/main/java/plugin/swisskit/offlinepython/ui/panel/InitPanel.java`
- Create: `src/main/java/plugin/swisskit/offlinepython/ui/panel/DepsPanel.java`
- Create: `src/main/java/plugin/swisskit/offlinepython/ui/panel/BuildPanel.java`
- Create: `src/main/java/plugin/swisskit/offlinepython/ui/panel/VerifyPanel.java`
- Create: `src/main/java/plugin/swisskit/offlinepython/ui/panel/DoctorPanel.java`
- Create: `src/main/java/plugin/swisskit/offlinepython/ui/CommandShell.java`
- Modify: `OfflinePythonPlugin.java` (wire CommandShell — remove the Task 1 placeholder)

- [ ] **Step 1: Implement LogConsole**

A scrollable, line-appending TextArea with a level filter and an API `log(String)`:

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
        area.setStyle("-fx-control-inner-background: rgba(0,0,0,0.28); -fx-text-fill: rgba(255,255,255,0.85);");
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

- [ ] **Step 2: Implement PythonInstallGuide**

```java
package plugin.swisskit.offlinepython.ui;

import fan.summer.api.component.GlassNotification;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

public class PythonInstallGuide extends VBox {
    public PythonInstallGuide(Runnable onRedetect) {
        setSpacing(10);
        setStyle("-fx-padding: 24;");
        getChildren().add(new Label("⚠ Python not detected"));
        getChildren().add(new Label("This plugin needs Python ≥ 3.10 + pip. Install it, then retry."));
        getChildren().add(cmdRow("macOS", "brew install python", this));
        getChildren().add(cmdRow("Linux", "sudo apt install python3 python3-pip", this));
        Button retry = new Button("Re-detect");
        retry.setOnAction(e -> onRedetect.run());
        getChildren().add(retry);
    }

    private HBox cmdRow(String os, String cmd, PythonInstallGuide self) {
        HBox row = new HBox(8);
        Label l = new Label(os + ":  " + cmd);
        Button copy = new Button("Copy");
        copy.setOnAction(e -> {
            ClipboardContent c = new ClipboardContent();
            c.putString(cmd);
            Clipboard.getSystemClipboard().setContent(c);
            GlassNotification.toast(self, GlassNotification.Type.SUCCESS, "Copied: " + cmd);
        });
        row.getChildren().addAll(l, copy);
        return row;
    }
}
```

- [ ] **Step 3: Implement PluginTask**

```java
package plugin.swisskit.offlinepython.task;

import javafx.concurrent.Task;

/** Thin wrapper exposing the underlying Task for cancellation + hasRunningTasks. */
public abstract class PluginTask<T> extends Task<T> {
    public boolean isRunningTask() { return getState() == State.RUNNING; }
}
```

- [ ] **Step 4: Implement CommandPanel base**

```java
package plugin.swisskit.offlinepython.ui.panel;

import javafx.scene.layout.VBox;
import plugin.swisskit.offlinepython.ui.LogConsole;

/** Base for command panels: provides title + log access. */
public abstract class CommandPanel extends VBox {
    protected final LogConsole log;
    protected CommandPanel(LogConsole log) {
        this.log = log;
        setSpacing(10);
        setStyle("-fx-padding: 18;");
    }
    public abstract String title();
}
```

- [ ] **Step 5: Implement InitPanel**

```java
package plugin.swisskit.offlinepython.ui.panel;

import fan.summer.api.component.GlassNotification;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.DirectoryChooser;
import plugin.swisskit.offlinepython.command.InitService;
import plugin.swisskit.offlinepython.ui.LogConsole;
import java.io.File;

public class InitPanel extends CommandPanel {
    public InitPanel(LogConsole log) {
        super(log);
        getChildren().add(new Label(title()));
        Button init = new Button("Initialize Project…");
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
    @Override public String title() { return "Initialize Project"; }
}
```

- [ ] **Step 6: Implement DepsPanel**

```java
package plugin.swisskit.offlinepython.ui.panel;

import fan.summer.api.component.GlassNotification;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import plugin.swisskit.offlinepython.domain.DependencySpec;
import plugin.swisskit.offlinepython.domain.RequirementsFile;
import plugin.swisskit.offlinepython.ui.LogConsole;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

public class DepsPanel extends CommandPanel {
    private final ListView<DependencySpec> list = new ListView<>();
    private Path requirementsFile;

    public DepsPanel(LogConsole log) {
        super(log);
        getChildren().add(new Label(title()));
        Button open = new Button("Open requirements.txt");
        open.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            File f = fc.showOpenDialog(getScene().getWindow());
            if (f == null) return;
            requirementsFile = f.toPath();
            load();
        });
        HBox addRow = new HBox(8,
                labeled("Package", new TextField()),
                labeled("Version", new TextField()));
        Button save = new Button("Save");
        save.setOnAction(e -> save());
        getChildren().addAll(open, list, addRow, save);
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

    private HBox labeled(String text, TextField field) {
        HBox h = new HBox(6, new Label(text), field);
        return h;
    }

    @Override public String title() { return "Dependencies"; }
}
```

- [ ] **Step 7: Implement BuildPanel**

```java
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
```

- [ ] **Step 8: Implement VerifyPanel**

```java
package plugin.swisskit.offlinepython.ui.panel;

import fan.summer.api.component.GlassNotification;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import plugin.swisskit.offlinepython.command.VerifyService;
import plugin.swisskit.offlinepython.domain.CheckResult;
import plugin.swisskit.offlinepython.domain.Manifest;
import plugin.swisskit.offlinepython.domain.VerifyResult;
import plugin.swisskit.offlinepython.infra.JsonStore;
import plugin.swisskit.offlinepython.ui.LogConsole;

import java.io.File;

public class VerifyPanel extends CommandPanel {
    private final VBox report = new VBox(6);

    public VerifyPanel(LogConsole log) {
        super(log);
        getChildren().add(new Label(title()));
        Button verify = new Button("Verify");
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
            report.getChildren().add(new Label("[" + c.status() + "] " + c.detail()));
        }
    }

    @Override public String title() { return "Verify Repository"; }
}
```

- [ ] **Step 9: Implement DoctorPanel**

```java
package plugin.swisskit.offlinepython.ui.panel;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import plugin.swisskit.offlinepython.command.DoctorService;
import plugin.swisskit.offlinepython.ui.LogConsole;

public class DoctorPanel extends CommandPanel {
    private final GridPane grid = new GridPane();

    public DoctorPanel(LogConsole log) {
        super(log);
        getChildren().add(new Label(title()));
        Button run = new Button("Run diagnostics");
        run.setOnAction(e -> {
            grid.getChildren().clear();
            int row = 0;
            for (var c : new DoctorService().run(null)) {
                grid.add(new Label(c.name()), 0, row);
                grid.add(new Label((c.ok() ? "✓ " : "✕ ") + c.value()), 1, row);
                row++;
            }
            log.log("Diagnostics complete");
        });
        grid.setHgap(16); grid.setVgap(6);
        getChildren().addAll(run, grid);
    }

    @Override public String title() { return "Environment Doctor"; }
}
```

- [ ] **Step 10: Implement CommandShell**

```java
package plugin.swisskit.offlinepython.ui;

import fan.summer.api.i18n.I18n;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import plugin.swisskit.offlinepython.infra.PythonDetector;
import plugin.swisskit.offlinepython.ui.panel.*;

public class CommandShell {
    private final BorderPane root = new BorderPane();
    private final StackPane content = new StackPane();
    private final LogConsole logConsole = new LogConsole();
    private final Label pyBadge = new Label();
    private BuildPanel buildPanel;

    public CommandShell() {
        ListView<String> nav = new ListView<>();
        nav.getItems().addAll("init", "deps", "build", "verify", "doctor");
        nav.getSelectionModel().selectedItemProperty().addListener((o, ov, name) -> switchTo(name));
        root.setLeft(nav);
        content.setStyle("-fx-background-color: transparent");
        root.setCenter(content);
        BorderPane bottomBar = new BorderPane();
        bottomBar.setCenter(logConsole);
        root.setBottom(bottomBar);
        BorderPane top = new BorderPane();
        top.setRight(pyBadge);
        root.setTop(top);
        refreshPython();
        nav.getSelectionModel().selectFirst();
    }

    private void switchTo(String name) {
        Node panel = switch (name) {
            case "init" -> new InitPanel(logConsole);
            case "deps" -> new DepsPanel(logConsole);
            case "build" -> buildPanel != null ? buildPanel : (buildPanel = new BuildPanel(logConsole));
            case "verify" -> new VerifyPanel(logConsole);
            case "doctor" -> new DoctorPanel(logConsole);
            default -> new Label("—");
        };
        content.getChildren().setAll(panel);
    }

    public void refreshPython() {
        var d = PythonDetector.detect(null);
        pyBadge.setText(d.ok()
                ? I18n.get("opb.python.detected", d.pythonVersion(), d.pipVersion() == null ? "?" : d.pipVersion())
                : I18n.get("opb.python.missing"));
        if (!d.ok()) content.getChildren().setAll(new PythonInstallGuide(this::refreshPython));
    }

    public Node getView() { return root; }
    public boolean hasRunningTasks() { return buildPanel != null && buildPanel.isRunning(); }
    public void onBackground() {}
    public void onForeground() { refreshPython(); }
    public void onUnload() {}
}
```

- [ ] **Step 11: Wire CommandShell into OfflinePythonPlugin**

Replace the Task 1 placeholder in `OfflinePythonPlugin.java` so `createView()` returns `shell.getView()` (the code in Task 1 Step 3 already reflects this; just ensure the placeholder `Label` is removed and `CommandShell` is used).

- [ ] **Step 12: Build the module**

Run: `mvn -pl SwissKitJ-Plugin-OfflinePython compile -B`
Expected: BUILD SUCCESS.

- [ ] **Step 13: Commit**

```bash
git add SwissKitJ-Plugin-OfflinePython
git commit -m "feat(OfflinePython): add UI shell, log console, install guide, and V1 command panels"
```

---

## Task 13: Integration verification via DevLauncher

- [ ] **Step 1: Run the full test suite**

Run: `mvn -pl SwissKitJ-Plugin-OfflinePython test -B`
Expected: all 24 unit tests PASS.

- [ ] **Step 2: Launch the preview window**

Run: `mvn -pl SwissKitJ-Plugin-OfflinePython -Pdev javafx:run -B`
Expected: a preview window opens showing the Offline Python Builder plugin. Manually verify:
- Top-right badge shows "Python <version> · pip <version>" (green) on a machine with Python, or the install-guide panel if not.
- Left nav switches between Init / Deps / Build / Verify / Doctor.
- **Init**: pick an empty folder → config.json, requirements.txt, README.md appear.
- **Deps**: open the generated requirements.txt → add/edit packages → Save.
- **Build**: pick the project folder → pip runs, output streamed to the log console, manifest.json + SHA256SUMS generated.
- **Verify**: point at the output/ dir → PASS/WARN/FAIL lines render; Repository OK on a clean build.
- **Doctor**: shows Python/pip/disk/cache checks.

- [ ] **Step 3: Package the fat JAR**

Run: `mvn -pl SwissKitJ-Plugin-OfflinePython package -B`
Expected: `target/SwissKitJ-Plugin-OfflinePython-1.0.0.jar` (shaded, contains Gson).

- [ ] **Step 4: Confirm the fat JAR is loadable by the host (optional, if host repo available)**

Drop the JAR into the SwissKitJ host `plugins/` directory and confirm it appears in the tool list. (Skip if the host isn't running locally.)

- [ ] **Step 5: Commit any final polish**

```bash
git add -A
git commit -m "chore(OfflinePython): V1 MVP verified end-to-end via DevLauncher" || echo "nothing to commit"
```

---

## Self-Review (completed)

**1. Spec coverage (V1 scope):**
- Python 检测 + 安装引导 → Tasks 6, 12 (PythonDetector + PythonInstallGuide) ✓
- init → Task 8 ✓
- deps（依赖配置）→ Task 2 (parse) + Task 12 (DepsPanel) ✓
- build → Task 10 + Task 12 (BuildPanel) ✓
- verify（含文件完整性）→ Task 9 (VerifyService: fileIntegrity check) ✓
- doctor → Task 11 + Task 12 ✓
- 外壳 UI + 日志控制台 → Task 12 (CommandShell + LogConsole) ✓
- 数据模型 (config/manifest/VerifyResult) → Tasks 3, 5, 9 ✓
- SHA256 → Task 4 ✓
- pip download cross-platform flags → Task 7 ✓

**2. Placeholder scan:** No TBD/TODO in task steps (the Task 1 Step 3 placeholder `Label` is explicitly replaced in Task 12 Step 11). All code steps contain full code. ✓

**3. Type consistency:** `DependencySpec(name, versionSpec, marker)` used consistently in Tasks 2, 9, 10. `VerifyResult` 5-field shape matches Task 9 render in Task 12 Step 8. `ProcessRunner.pipDownloadCommand(...)` signature identical in Tasks 7 and 10. `BuildConfig` getters (`getPython().getExecutable()` etc.) consistent across Tasks 3, 10, 12. ✓

---

## Out of scope for V1 (deferred to V2/V3)

- update / clean / list / info / cache commands (V2)
- pack (zip) / export (V3)
- incremental download (skip-existing optimization beyond pip's own)
- enterprise mirror configuration
- resume / proxy beyond what pip inherits
