# Offline Python Builder — SwissKitJ 插件设计

- **日期**: 2026-06-26
- **状态**: 设计已确认，待编写实现计划
- **插件 ID**: `plugin.swisskit.offlinepython`
- **来源需求**: `开发需求.md`（Offline Python Builder PRD）

---

## 1. 目标

提供一个 SwissKitJ 插件，在**联网**机器上一键构建**离线 Python 安装仓库**：下载 Python 官方安装程序与所有第三方 wheel 依赖，生成 manifest、SHA256、README、安装脚本，并可打包导出。构建产物可整体复制到**离线** Windows / Linux 环境完成 Python 与依赖安装，无需联网。

核心定位：**Java/JavaFX 做 UI 与编排，依赖解析与 wheel 下载交给目标机器上安装的 `python`/`pip` 子进程**。插件不维护第二套 Python CLI，自包含为单个 fat JAR。

---

## 2. 范围与分阶段

PRD 描述了 11 个子命令，单插件内实现，但分三个阶段交付，每个阶段对应独立的实现计划。

| 阶段 | 包含命令 | 产出 |
|------|---------|------|
| **V1（MVP 闭环）** | Python 检测 + 安装引导、init、deps（依赖配置）、build、verify、doctor、整体外壳 UI + 日志控制台 | 可构建可用离线仓库并自校验 |
| **V2** | update（增量）、clean、cache、list、info | 增量更新、清理、缓存管理 |
| **V3** | pack（zip）、export | 打包压缩、导出到外部介质 |

本文档描述**整体设计**；每个阶段拆分时引用本文档作为依据。

---

## 3. 插件元数据

| 项 | 值 |
|----|----|
| 名称 | Offline Python Builder |
| ID | `plugin.swisskit.offlinepython` |
| 基础包 | `plugin.swisskit.offlinepython` |
| 分类 | `ToolCategory.DEV` |
| MDI 图标 | `language-python` |
| 图标风格 | `IconStyle.BLUE` |
| 版本 | `1.0.0` |

---

## 4. 架构（分层）

```
┌─────────────────────────────────────────────────────────────┐
│  UI 层     CommandShell（顶栏 + 左栏导航 + 右侧面板 + 底部日志控制台） │
├─────────────────────────────────────────────────────────────┤
│  Task 层   JavafxTask 封装（取消 / 进度 / hasRunningTasks）       │
├─────────────────────────────────────────────────────────────┤
│  Command 层  11 个 Service：Init / Deps / Build / Update /       │
│              Verify / Clean / Pack / Export / List / Info /      │
│              Cache / Doctor                                      │
├─────────────────────────────────────────────────────────────┤
│  Domain 层   BuildConfig / Manifest / WheelEntry / VerifyResult  │
│              / 共享模型（均 JSON 序列化）                          │
├─────────────────────────────────────────────────────────────┤
│  Infra 层    PythonDetector / ProcessRunner / HashUtil /          │
│              InstallerDownloader / JsonConfig / ZipUtil           │
└─────────────────────────────────────────────────────────────┘
```

**奠基组件：**

- **PythonDetector** — 探测 `python`/`python3`/`pip`，缓存版本结果。未检测到时 UI 切换为安装引导面板，阻断一切命令。
- **ProcessRunner** — `ProcessBuilder` 封装：逐行流式捕获 pip 的 stdout/stderr 推送到 UI 日志控制台；支持超时、重试、取消（`destroyForcibly()`）。
- **HashUtil** — SHA256 计算（`MessageDigest`）。
- **InstallerDownloader** — 从 python.org 下载官方安装程序（带重试与进度）。
- **JsonConfig** — JDK 原生 JSON 读写 config.json / manifest.json。
- **ZipUtil** — `java.util.zip` 打包。

**Infra 层零第三方依赖**（除 Lombok），全部使用 JDK 21 能力。

---

## 5. 项目结构

```
SwissKitJ-Plugin-OfflinePython/
├── pom.xml
├── src/main/java/plugin/swisskit/offlinepython/
│   ├── OfflinePythonPlugin.java          # SPI 入口
│   ├── DevLauncher.java                  # 模块系统旁路 + PluginPreviewWindow
│   ├── infra/
│   │   ├── PythonDetector.java
│   │   ├── ProcessRunner.java
│   │   ├── HashUtil.java
│   │   ├── InstallerDownloader.java
│   │   ├── JsonConfig.java
│   │   └── ZipUtil.java
│   ├── domain/
│   │   ├── BuildConfig.java              # config.json 模型
│   │   ├── Manifest.java                 # manifest.json 模型
│   │   ├── WheelEntry.java
│   │   ├── DependencySpec.java           # requirements.txt 单条依赖
│   │   └── VerifyResult.java             # 校验结果（含 5 项）
│   ├── command/                          # Command 层
│   │   ├── InitService.java
│   │   ├── DepsService.java
│   │   ├── BuildService.java
│   │   ├── UpdateService.java
│   │   ├── VerifyService.java
│   │   ├── CleanService.java
│   │   ├── PackService.java
│   │   ├── ExportService.java
│   │   ├── ListService.java
│   │   ├── InfoService.java
│   │   ├── CacheService.java
│   │   └── DoctorService.java
│   ├── task/
│   │   └── PluginTask.java               # javafx Task 封装（取消/进度）
│   └── ui/
│       ├── CommandShell.java             # 顶栏 + 左导航 + StackPane + 日志控制台
│       ├── PythonInstallGuide.java       # 未检测到 Python 时的引导面板
│       ├── LogConsole.java               # 共享日志控制台
│       └── panel/
│           ├── InitPanel.java
│           ├── DepsPanel.java            # 依赖配置（依赖清单编辑器）
│           ├── BuildPanel.java
│           ├── VerifyPanel.java
│           ├── PackPanel.java
│           ├── ExportPanel.java
│           ├── UpdatePanel.java
│           ├── CleanPanel.java
│           ├── ListPanel.java
│           ├── InfoPanel.java
│           ├── CachePanel.java
│           ├── DoctorPanel.java
│           └── CommandPanel.java         # 面板公共基类
├── src/main/resources/
│   ├── META-INF/services/fan.summer.api.SwissKitJPlugin
│   └── i18n/
│       ├── messages.properties
│       └── messages_zh.properties
```

SPI 注册文件 `META-INF/services/fan.summer.api.SwissKitJPlugin` 内容：
```
plugin.swisskit.offlinepython.OfflinePythonPlugin
```

---

## 6. 数据模型（JSON）

### 6.1 config.json

```json
{
  "python": {
    "version": "3.12.10",
    "platform": "win_amd64",
    "implementation": "cp",
    "installer": true,
    "executable": null
  },
  "repository": {
    "output": "output",
    "wheelDir": "wheelhouse",
    "cache": true
  },
  "download": {
    "mirror": "official",
    "upgradePip": true,
    "recursive": true,
    "onlyBinary": true
  },
  "package": {
    "zip": true,
    "sha256": true,
    "readme": true
  }
}
```

`python.executable` 为用户手动指定的解释器路径；为 `null` 时走自动探测。

### 6.2 requirements.txt

由「依赖配置」面板生成，标准 pip 格式：

```
numpy==1.26.4
pandas==2.2.0
requests>=2.31
flask==3.0.0 ; sys_platform == "linux"
pywin32==306 ; sys_platform == "win32"
```

平台限定通过 pip 原生的 environment markers（`; sys_platform == "win32"`）表达，不另造语法。

### 6.3 manifest.json

每次 build / update 重新生成：

```json
{
  "schemaVersion": 1,
  "python": {
    "version": "3.12.10",
    "platform": "win_amd64",
    "installer": "python/python-3.12.10-amd64.exe",
    "installerSha256": "abc123..."
  },
  "builtAt": "2026-06-26T14:52:48",
  "builtOn": "Phoebe-Mac",
  "toolVersion": "1.0.0",
  "wheels": [
    {
      "name": "numpy",
      "version": "1.26.4",
      "file": "wheelhouse/numpy-1.26.4-cp312-cp312-win_amd64.whl",
      "sha256": "def456...",
      "size": 19098624,
      "required": true
    }
  ],
  "requirements": ["numpy==1.26.4", "pandas==2.2.0", "requests>=2.31"]
}
```

`required` 区分 requirements.txt 显式声明的依赖（true）与 pip 递归解析出的传递依赖（false）。

### 6.4 SHA256SUMS

每行 `<sha256>  <相对 output/ 的路径>`，覆盖 python installer 与所有 wheel。

### 6.5 VerifyResult（校验结果，对应需求第 13 节）

```java
enum Status { PASS, WARN, FAIL }

record CheckResult(Status status, String detail, List<String> problems) {}

record VerifyResult(
    CheckResult sha256,          // SHA256SUMS 每行重算比对
    CheckResult fileIntegrity,   // 文件完整性：manifest 列出的文件都存在、大小匹配、无 0 字节/损坏
    CheckResult wheels,          // wheel 文件名规范 + 可被 pip 识别
    CheckResult requirements,    // requirements.txt 每个包在 wheelhouse 有满足的 wheel
    CheckResult manifest         // manifest.json 与磁盘实际文件集合一致
) {
    boolean isOk() { /* 全部非 FAIL */ }
}
```

---

## 7. Python 检测与安装引导

### 7.1 探测逻辑（`PythonDetector`）

启动时执行一次，每个命令执行前复用缓存：

1. 候选可执行：`config.json.executable`（若有）→ `python3` → `python`（PATH 查找）。
2. PATH 未命中时查常见安装路径：
   - macOS：`/usr/local/bin`、`/opt/homebrew/bin`
   - Windows：`%LOCALAPPDATA%\Programs\Python\*`、`C:\Python*`
   - Linux：`/usr/bin/python3`、`/usr/local/bin/python3`
3. 解析版本：
   - `<python> --version` → `Python 3.12.10`
   - `<python> -m pip --version` → `pip 25.0 ... (python 3.12)`
4. 缓存 `(path, pythonVersion, pipVersion)`；`config.python.version` 与检测版本不一致给 WARN。

### 7.2 未检测到 → 安装引导面板

整个右侧内容区替换为 `PythonInstallGuide`，**阻断所有命令按钮**：

```
⚠ 未检测到 Python
本插件需要 Python ≥ 3.10 + pip。

  macOS：  brew install python          [复制命令]
  Windows：https://www.python.org/downloads   [打开浏览器]
  Linux：   sudo apt install python3    [复制命令]

[安装后点此重新检测]   [手动指定路径…]
```

- `手动指定路径`：文件选择器定位到自定义 `python.exe`，写入 `config.json.executable`。
- `重新检测` 成功 → 顶栏徽章转绿，解除阻断。
- 版本低于 3.10 或缺 pip → 同样进入引导，提示升级。

---

## 8. 命令行为（Command 层 → pip 映射）

| 命令 | Java 行为 | pip 调用 |
|------|----------|---------|
| **init** | 在项目目录写 config.json / requirements.txt 骨架 / README.md | — |
| **deps（依赖配置）** | 编辑依赖列表，保存 requirements.txt；查最新版填充面板 | `pip index versions <pkg>` |
| **build** | PythonDetector 检查 → 下载 wheel → 下载 installer → 生成 manifest/SHA256/README/install | `pip install --upgrade pip`（可选）<br>`pip download -r requirements.txt -d output/wheelhouse --platform <P> --python-version <V> --implementation cp --only-binary=:all:` |
| **update** | 读新 requirements，wheelhouse 已有的 wheel 跳过，只下差异；更新 manifest/SHA256 | `pip download ...`（同 build） |
| **verify** | 纯 Java 文件 / 哈希比对，不调 pip | — |
| **clean** | Java 扫描旧版本 / 重复 wheel，按 manifest 决定保留 | — |
| **pack** | `java.util.zip` 压缩 output/ → `output-<python-version>.zip` | — |
| **export** | Java 复制 output/ 到目标路径 | — |
| **list** | 读 manifest.json 展示概览 | — |
| **info** | 读写 config.json | — |
| **cache** | 管理 `~/.offline-python/cache`（统计 / 清理） | — |
| **doctor** | PythonDetector + ping PyPI + 磁盘空间 + 目录可写检查 | `python --version`、`pip --version` |

**关键设计：递归依赖解析完全交给 pip。** `pip download` 原生解析递归依赖；通过 `--platform` / `--python-version` / `--only-binary=:all:` 让 pip 下载**目标平台**的 wheel（例如在 macOS 上构建 Windows 离线包）。Java 不实现任何依赖解析器。

---

## 9. UI 设计

布局见定稿原型（`.superpowers/brainstorm/`）。结构：

- **顶栏**：项目目录选择器 + 新建/打开项目 + Python 状态徽章（绿=已检测 / 黄=WARN / 阻断=引导面板）。
- **左栏导航**：11 个命令分两组——「仓库操作」（init / deps / build / update / verify / clean / pack / export）与「查看与工具」（list / info / cache / doctor）。deps 带依赖数量角标。
- **右侧内容**：`StackPane` 按选中切换 11 个 `*Panel`；Python 未检测时整体替换为 `PythonInstallGuide`。
- **底部日志控制台**（共享、可折叠）：`ProcessRunner` 输出实时流入；级别过滤（全部/INFO/WARN/ERROR）；命令完成用 `GlassNotification.toast` 非阻塞提示。
- **面板共性**：表单区 + 执行/取消按钮 + 进度/结果区。组件使用 `UiUtils.glassBtn` / `sectionTitle` 等 API 保持玻璃风一致。

**关键面板：**

- **DepsPanel（依赖配置）**：依赖表格（包名 / 版本约束 / 目标平台 / 预估大小 / 删除）+ 添加行 + 工具栏（搜索、导入 requirements.txt、PyPI 搜索添加、保存）+ 选项（递归、wheel 优先、升级 pip）+ 汇总条（直接/解析后数量、预估大小、目标平台 pill、「保存并去构建」）。
- **BuildPanel**：当前依赖 banner + 执行/取消 + 进度条 + 结果 tile（已下载/耗时/大小/缓存命中）+ 实时状态。
- **VerifyPanel**：校验范围分段（全量 / 仅完整性 / 仅 SHA256）+ 逐项 PASS/WARN/FAIL 报告 + Repository OK 结论。
- **DoctorPanel**：环境检查表（解释器 / 版本 / pip / pip download 可用 / 网络 / 磁盘 / 缓存目录）。

---

## 10. 输出目录结构（build 后）

```
<项目目录>/
├── config.json
├── requirements.txt
├── output/
│   ├── python/
│   │   └── python-3.12.10-amd64.exe
│   ├── wheelhouse/
│   │   ├── numpy-1.26.4-cp312-cp312-win_amd64.whl
│   │   └── ...
│   ├── manifest.json
│   ├── SHA256SUMS
│   ├── README.md
│   └── install.bat
└── ~/.offline-python/        # 全局缓存
    ├── cache/
    ├── logs/
    └── config/
```

### 10.1 install.bat（离线安装脚本）

```bat
@echo off
echo Installing Python 3.12.10 ...
python-3.12.10-amd64.exe /quiet InstallAllUsers=1
echo Installing wheels ...
pip install --no-index --find-links wheelhouse -r requirements.txt
echo Done.
```

Linux 产物对应 `install.sh`。

---

## 11. 取消、后台与生命周期

- 每个 Command Service 持有当前 `PluginTask`（继承 `javafx.concurrent.Task`）。
- `[取消]` → `task.cancel()` → `ProcessRunner.destroyForcibly()` 终止 pip 子进程。
- `OfflinePythonPlugin.hasRunningTasks()` 返回 `task != null && task.isRunning()`，构建中途切换插件/命令任务继续后台运行（符合 `SwissKitJPlugin` 契约）。
- `onUnload()` 停止所有运行中的 task 与子进程，释放文件句柄。

---

## 12. 日志

`~/.offline-python/logs/` 下 `build.log` / `download.log` / `verify.log`，级别 INFO/WARN/ERROR/DEBUG。同时实时输出到 UI 日志控制台。使用 `LoggerFactory.getLogger(...)`。

---

## 13. 缓存

`~/.offline-python/cache/`：下载 wheel / installer 前先查缓存，命中则复用、未命中再下载。缓存 key 为文件名 + sha256。V2 阶段的 `cache` 命令提供统计与清理。

---

## 14. 非功能性

- Java ≥ 21（与宿主一致）；JavaFX 21（provided）。
- 支持断点续传（HTTP Range，installer 下载）。
- 支持代理（`pip` 继承系统代理；installer 下载读 JVM proxy）。
- 支持镜像源（`download.mirror`，V2 扩展企业镜像）。
- 支持并发下载（installer 与 wheel 独立）。
- 支持重试与超时（`ProcessRunner` 配置）。
- i18n：`messages.properties`（英）/ `messages_zh.properties`（中），`createView()` 注册 bundle。

---

## 15. pom.xml 要点

- parent：SwissKitJ-Api（`provided`）、javafx-graphics / javafx-controls（`provided`）。
- Lombok（`provided`）。
- **不引入** tomlj / commons-compress（配置用 JSON、打包用 JDK zip）。
- shade 插件打 fat JAR，`ServicesResourceTransformer` 合并 SPI。
- `dev` profile + `javafx-maven-plugin` 跑 `DevLauncher`。

---

## 16. 待编写实现计划

按第 2 节分阶段：
1. **V1 计划**：骨架 + PythonDetector + 安装引导 + init/deps/build/verify/doctor + 外壳 UI + 日志控制台。
2. **V2 计划**：update/clean/cache/list/info。
3. **V3 计划**：pack/export。

下一步进入 writing-plans，先产出 V1 实现计划。
