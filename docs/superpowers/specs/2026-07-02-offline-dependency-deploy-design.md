# 离线依赖部署功能 — 设计文档

- **日期**: 2026-07-02
- **状态**: 已确认,待实现
- **作者**: brainstorming session (Superpowers)
- **关联模块**: `SwissKitJ-Plugin-OfflinePython`

## 1. 目标与范围

OfflinePython 模块当前能在联网机上通过 `pip download` 构建 wheelhouse。本功能补齐两个缺失环节,形成离线依赖分发的完整闭环:

1. **打包(联网机)**: 构建后把 wheelhouse + manifest 打包成一个可拷贝的 ZIP。
2. **部署(离线机)**: 在离线电脑上复用本插件,加载 ZIP,检测本机平台筛选 wheel,安装到全局环境或新建虚拟环境。

### 范围内

- ZIP bundle 格式与打包服务
- 部署页 UI(第 4 个 nav)
- 本机平台检测 + wheel 兼容匹配
- 解压 + pip install 流程(全局 / venv)
- 构建后自动/手动打包入口

### 范围外(YAGNI)

- 不下载 Python 安装器(离线机自带 Python)
- 不做远程传输(SCP/SSH/rsync)
- 不改 BuildService 输出结构
- 不生成 install.bat/install.sh 脚本(部署走插件 GUI)
- 不做增量更新/卸载/回滚

## 2. 整体架构与数据流

```
┌─────────────── 联网机(打包) ───────────────┐     ┌─────────── 离线机(部署) ───────────┐
│ ConfigPanel ──→ BuildVerifyPanel            │     │                                     │
│  (配依赖)        (build → output/wheelhouse)│     │  DeployPanel (新增第4个 nav 页)     │
│                     │                        │     │   1. 选 ZIP                          │
│                     ▼                        │     │   2. 读 manifest,检测本机平台        │
│              PackageService (新增)           │     │   3. 筛选适配 wheel,UI 预览          │
│              把 output/ 打包成 bundle.zip    │ ──► │   4. 选目标(全局/venv)               │
│              (含 manifest + wheels/ + SHA)   │ zip │   5. DeployService.pipInstall()      │
└─────────────────────────────────────────────┘     └─────────────────────────────────────┘
```

### 新增核心单元

| 单元 | 职责 | 所在包 |
|---|---|---|
| `PackageService` | 把构建产物打包成 ZIP(读 `output/` → 写 bundle.zip) | `command/` |
| `BundleReader` | 读 ZIP 内 manifest + wheel 列表(纯逻辑,无 FX) | `domain/` |
| `PlatformMatcher` | 给定本机平台标签 + wheel 列表,返回适配子集 | `domain/` |
| `DeployService` | 解压 ZIP 到临时目录,调用 `pip install --no-index` | `command/` |
| `DeployPanel` | 部署页 UI(选包/预览/选目标/执行/日志) | `ui/panel/` |
| `BuildConfig.Bundle` | `BuildConfig` 新增的打包配置段 | `domain/BuildConfig` 内 |

### 复用现有单元

- `ProcessRunner` — 流式跑 pip(打包端不依赖,部署端复用)
- `PluginTask` — 后台任务基类
- `PythonDetector` — 找本机 Python + 版本
- `Manifest` / `WheelEntry` — 筛选数据源
- `CommandShell` nav 机制
- `OpbStyle` / `UiUtils` / `GlassNotification` / `StatTile` / `StatusBadge`

## 3. ZIP 包格式(Bundle Format)

```
<project>-bundle-<yyyymmdd-HHMM>.zip      ← 输出到 output/ 下
└── bundle/                                 ← 解压根目录(固定名)
    ├── manifest.json                       ← 直接拷自构建产物
    ├── SHA256SUMS                          ← 直接拷自构建产物
    └── wheels/                             ← 扁平目录,所有平台 wheel 混放
        ├── numpy-1.26.4-cp312-cp312-win_amd64.whl
        ├── numpy-1.26.4-cp312-cp312-manylinux2014_x86_64.whl
        ├── requests-2.31.0-py3-none-any.whl
        └── ...
```

### 设计要点

1. **manifest.json 是部署端的唯一索引**。它已记录每个 wheel 的 `file`(文件名)、`name`、`version`、`sha256`、`size`。部署端用它做平台筛选,不依赖目录结构。
2. **wheels/ 扁平化**。所有平台的 wheel 平铺在一个目录里,文件名本身已带平台标签(`cp312-win_amd64`、`manylinux2014_x86_64`、`py3-none-any`),`PlatformMatcher` 靠解析文件名 + manifest 即可筛选。纯 Python 包(`*-none-any.whl`)只存一份,全平台共用。
3. **固定解压根 `bundle/`**。解压后目录确定,部署端逻辑稳定,不依赖 ZIP 文件名。
4. **manifest 不做结构改动**。现有 `Manifest` schema 完全够用。只在打包阶段把现有 `output/manifest.json` 原样拷入,零迁移成本。
5. **SHA256SUMS 一并打入**。部署端安装前可校验 wheel 完整性(与现有 `VerifyService` 的 SHA256 逻辑呼应)。

## 4. 平台匹配逻辑(PlatformMatcher)

部署端核心:给定本机环境 + 一堆 wheel 文件名,筛出适配本机的那部分。

### 本机检测

- Python 版本: `PythonDetector` 已有,返回如 `3.12`
- 本机平台标签: 用 `sys.platform` + `platform.machine()` 推导(不依赖目标机装 pip 的能力):

| 系统 | 架构 | 推导出的平台标签集合 |
|---|---|---|
| Windows | x64 | `win_amd64`, `win32`, `any` |
| Windows | x86 | `win32`, `any` |
| Linux | x64 | `manylinux2014_x86_64`, `linux_x86_64`, `any` |
| Linux | arm64 | `manylinux2014_aarch64`, `any` |
| macOS | x64 | `macosx_*_x86_64`(区间匹配), `any` |
| macOS | arm64 | `macosx_*_arm64`(区间匹配), `any` |

### wheel 文件名解析

标准格式 `{name}-{version}-{python_tag}-{abi_tag}-{platform_tag}.whl`,如:

- `numpy-1.26.4-cp312-cp312-win_amd64.whl` → platform=`win_amd64`
- `requests-2.31.0-py3-none-any.whl` → platform=`any`
- `pillow-10.0.0-cp312-cp312-macosx_11_0_arm64.whl` → platform=`macosx_11_0_arm64`

### 匹配规则(简化版 PEP 425)

对每个 wheel 判定 **compatible / incompatible**:

1. **Python 版本**: wheel 的 `python_tag`(`cp312`/`py3`/`pp39`…)须与本机兼容。`cpXY` → 本机 `X.Y` 匹配;`py3` → 任何 Python 3;`pyXY` → 本机 ≥ X.Y。
2. **ABI**: `none`(纯)总是匹配;`cp312` 须等于本机 cp 版本;`abi3` 向前兼容。
3. **platform**:
   - `any` → 永远匹配
   - 精确匹配(`win_amd64`、`manylinux2014_x86_64`)→ 在本机标签集合里即匹配
   - `macosx_<min>_<minor>_<arch>` → 区间匹配:本机 macOS 版本 ≥ wheel 声明的最低版本且架构相同

4. **同包多 wheel 取最优**: 同一个 `name` 可能有多个兼容 wheel(如 `cp312-win_amd64` 和 `py3-none-any`),保留所有兼容项交给 pip 最终决定(pip 在 `--find-links` 下会选最合适的);但 UI 预览按 `name` 去重展示,显示「将安装 N 个包」。

### 输出

```
PlatformMatcher.match(machineTags, pythonVersion, List<WheelEntry> all)
  → List<WheelEntry>  // 适配本机的子集(按 name 分组,保留所有兼容 wheel 文件)
```

### 边界

- 检测不到 Python → 部署页报错,不允许进入安装。
- 匹配后某包**零兼容 wheel**(例如离线机是 Linux 但 ZIP 里只有 win 的编译包)→ 列入「不兼容」清单,UI 标红,允许用户知情后仍继续或中止。
- macOS 区间匹配写最简实现(解析 `macosx_A_B_arch`,比较主次版本号),不引第三方库。

## 5. 配置与打包(PackageService + BundleConfig)

### BuildConfig 新增段

```java
// domain/BuildConfig.java 内新增静态内部类 + 字段
public static class Bundle {
    public boolean autoPackage = false;  // 构建后是否自动打包(默认关)
    public String  name        = "";     // 包名,空=用项目目录名
    public boolean sha256      = true;   // 打包时是否含 SHA256SUMS
}
// BuildConfig 顶层新增字段
public Bundle bundle = new Bundle();
```

持久化到 `config.json` 的 `bundle` 段,Gson 自动序列化,向后兼容(旧 config 无此段 → 默认值)。

### PackageService

单一入口:
```java
public Path packageBundle(Path projectDir, BuildConfig cfg) throws IOException
```

流程:
1. 定位 `output/`: `projectDir/<repository.output>`(复用现有约定,默认 `output`)。
2. 校验: `manifest.json` 不存在 → 抛异常「请先构建」;`wheelhouse/<ver>/` 为空 → 抛异常「无 wheel 可打包」。
3. 定位 wheel 来源: `output/<repository.wheelDir>/<python.version>/`。
4. 组装 ZIP 到 `output/<bundleName>-bundle-<yyyyMMdd-HHmm>.zip`:
   - `bundle/manifest.json` ← 拷贝 `output/manifest.json`
   - `bundle/SHA256SUMS` ← `cfg.bundle.sha256` 为真时拷贝
   - `bundle/wheels/*.whl` ← 拷贝 wheel 目录下全部 `.whl`(扁平化,保留原文件名;遇重名按先到先得跳过并记日志)
5. 返回生成的 ZIP 路径。

### 特性

- 使用 JDK `java.util.zip`(`ZipOutputStream`),零第三方依赖。
- **流式写入**: 边读边压,不在磁盘做中转目录。
- **确定性**: ZIP 内条目按「manifest → SHA256SUMS → wheels(按名字排序)」顺序写入,便于复现。
- **日志**: 通过注入的 `OpbLogger` 记录「打包 N 个 wheel,总 X MB」。

### 触发方式(UI)

1. **BuildVerifyPanel 构建成功后**: 在统计区下方出现「📦 打包成 ZIP」按钮,点击后台跑 `PackageService`,成功后 toast 并显示 ZIP 路径 + 「打开所在文件夹」。
2. **自动打包**: `cfg.bundle.autoPackage=true` 时,构建任务结束自动连跑打包。默认关,避免改变现有构建行为。**自动打包失败仅 toast 警告,不回滚构建产物(构建已成功,wheelhouse 仍可用)。**

## 6. 部署端 UI(DeployPanel)

新增第 4 个 nav 项: `CommandShell` 加 `"deploy"` → 标签「部署」,图标用 MDI 的 `tray-arrow-down`。

### 三段式布局

```
┌─ 部署 ─────────────────────────────────── [刷新本机环境] ┐
│                                                          │
│ ① 选包                                                   │
│   [ 选择 ZIP 包… ]  bundle-xxx.zip                       │
│   检测到本机: Windows 11 · x64 · Python 3.12.10          │
│                                                          │
│ ② 预览（读 manifest + 平台筛选后）                        │
│   将安装 12 个包(适配本机) · 3 个不兼容(已隐藏)            │
│   ┌──────────────────────────────────────┐               │
│   │ numpy      1.26.4   cp312-win_amd64  │               │
│   │ requests   2.31.0   py3-none-any     │               │
│   │ …           (表格,只读)              │               │
│   └──────────────────────────────────────┘               │
│   [显示不兼容 ▾]  ← 默认折叠,点开看哪些装不了及原因        │
│                                                          │
│ ③ 目标环境                                               │
│   ◉ 全局环境  (Python 3.12.10 @ C:\...\python.exe)       │
│   ○ 新建虚拟环境  路径: [________]  [浏览]                │
│                                                          │
│   [ ▶ 开始安装 ]                                         │
│                                                          │
│ ④ 日志（安装时显示）                                      │
│   ┌──────────────────────────────────────┐               │
│   │ Looking in indexes: ... (offline)     │               │
│   │ Installing collected packages: ...    │               │
│   │ Successfully installed numpy-1.26.4 … │               │
│   └──────────────────────────────────────┘               │
└──────────────────────────────────────────────────────────┘
```

### 交互流程

1. **选包**: 文件选择器(`FileChooser`, `.zip` 过滤)。选中后立即在后台读 ZIP 内 `manifest.json`(不解压全部,只读入口),失败 → 标红「无效的 bundle 包」。
2. **环境检测**: `PythonDetector.detect()` → 显示本机 Python 路径 + 版本。检测不到 Python → 整页禁用安装按钮,提示「请先安装 Python 3.10+」。本机平台标签由 `PlatformMatcher.detectHostTags()` 推导。
3. **预览**: `BundleReader.read(zip)` → `List<WheelEntry>` → `PlatformMatcher.match(hostTags, pyVer, wheels)` → 适配表 + 不兼容表。适配表默认显示(按包名排序);不兼容表折叠,点开显示包名 + 不兼容原因。SHA256 校验: 若 ZIP 含 `SHA256SUMS`,预览阶段对适配 wheel 做完整性校验,损坏的标黄并从安装列表剔除。
4. **目标选择**: 单选。
   - 全局: 直接用检测到的 Python。
   - venv: 用户填路径(默认填 `<ZIP所在目录>/<bundle名>-venv`),点「浏览」选目录。安装前 `python -m venv <path>` 创建。
5. **开始安装**: 禁用按钮 → 显示日志区 → 后台 `PluginTask` 跑 `DeployService.install()` → 流式回显 pip 输出 → 完成后 toast「成功安装 N 个包」或显示失败原因。

### 视觉一致性

用 `UiUtils.glassBtn`、`OpbStyle.card()`、`StatTile`、`StatusBadge`(成功/失败/不兼容标记)、`GlassNotification.toast`,与 ConfigPanel/BuildVerifyPanel 视觉一致。

### 空状态

未选 ZIP 时,②③④区域隐藏,只显示选包按钮 + 环境检测结果。

## 7. 部署服务(DeployService)

部署端的执行核心,单一职责——拿到 ZIP + 目标 Python,把适配的 wheel 装进去。

### 入口

```java
public DeployResult install(Path zip, Path pythonExe, DeployTarget target,
                            Consumer<String> onLog) throws Exception
```

`DeployTarget`:
```java
sealed interface DeployTarget {
    record Global(Path pythonExe)                  implements DeployTarget {}
    record Venv(Path pythonExe, Path venvPath)     implements DeployTarget {} // venvPath 待创建
}
```

### 执行流程

1. **解压**: 把 ZIP 解压到临时目录(`Files.createTempDirectory("opb-deploy")`)。只解压 `bundle/wheels/*.whl` + `manifest.json`(按需,不盲目全解)。记录解压目录,安装结束后 `finally` 删除。
2. **(仅 venv)创建虚拟环境**: `ProcessRunner.run(pythonExe, "-m", "venv", venvPath)`。失败 → 抛异常「虚拟环境创建失败」并带 pip 输出。成功后 `pythonExe` 切换为 `venvPath/<bin>/python`(`bin` 在 Windows 上是 `Scripts`)。
3. **筛选**: 从解压出的 manifest 读 wheel 列表 → `PlatformMatcher.match(hostTags, pyVersion, wheels)` → 得到适配 wheel 文件清单。(UI 已预览过,这里复算一次保证一致。)
4. **安装**: 逐包安装,流式执行:
   ```
   <pythonExe> -m pip install --no-index --no-deps --find-links=<解压目录/wheels> <wheel文件名>
   ```
   - `--no-index`: 禁联网,只看本地。
   - `--find-links`: 指向解压出的扁平 wheels 目录。
   - `--no-deps`: 依赖已在 bundle 里显式列出(构建时已 resolve 全),避免 pip 联网找依赖。
   - 流式日志通过 `onLog` 回调实时回显到 UI。
5. **校验(可选)**: 若 ZIP 含 `SHA256SUMS`,安装前对适配 wheel 做 SHA256 校验,损坏则跳过并记日志。
6. **返回 `DeployResult`**:
   ```java
   public record DeployResult(int installed, int skipped, int failed, long durationMs) {}
   ```

### 逐包安装策略

对每个适配 wheel 单独跑 pip,汇总成功/失败数。理由:
- 离线机器上某个编译型 wheel 若与目标 Python 小版本不符(如 cp312 装到 3.11),批量会整体失败;逐包则只该包失败,其余成功。
- 日志按包分段,用户看得清。
- 性能在离线本地安装场景可接受(无网络往返)。

### 错误处理

- pip 非零退出 → 该包计 failed,日志记录,继续下一包。
- 全部结束后若有 failed,UI 标红汇总,不删临时目录(方便排查)——由用户点「重试」或「关闭」时清理。

## 8. 与现有系统的集成点

### 8.1 CommandShell — 新增 nav 项

```java
navEntry(nav, "config",  "package-variant-closed", false);
navEntry(nav, "build",   "hammer-wrench", false);
navEntry(nav, "deploy",  "tray-arrow-down", false);   // ← 新增
navEntry(nav, "doctor",  "stethoscope", false);
```
- `select("deploy")` → 切到 `DeployPanel`。
- i18n 新增 `opb.nav.deploy = 部署`。
- 部署页**不需要项目上下文**(离线机可能没项目),随时可用。

### 8.2 ProjectContext — 不变

打包端复用现有 `project.getProjectDir()` / `project.getConfig()`。部署端不依赖 `ProjectContext`(它只认 ZIP + 本机 Python)。

### 8.3 BuildVerifyPanel — 构建成功后挂打包按钮

构建任务 `succeeded()` 回调里: `cfg.bundle != null && cfg.bundle.enabled` 时显示「📦 打包成 ZIP」按钮。点击后台跑 `PluginTask<Path>` → `PackageService.packageBundle()` → 成功后 toast + 统计区显示 ZIP 名 + 「打开所在文件夹」。

### 8.4 ConfigPanel — 新增勾选项

选项区现有 `递归 / wheel优先 / 升级 pip`,追加「☑ 构建后自动打包」,绑定 `cfg.bundle.autoPackage`。

### 8.5 ProcessRunner / PythonDetector — 复用不改

`DeployService` 的 pip 调用完全走现有 `ProcessRunner.run()`。`PythonDetector.detect()` 直接复用。

### 8.6 不做的事(YAGNI 边界)

- 不下载 Python 安装器
- 不做远程传输(SCP/SSH)
- 不改 BuildService 输出结构(打包是 output/ 之上的薄层)
- 不生成 install.bat/install.sh 脚本(部署走插件 GUI)
- 不做增量更新/卸载/回滚

## 9. 新增/改动文件清单

| 文件 | 类型 | 说明 |
|---|---|---|
| `domain/BuildConfig.java` | 改 | 新增 `Bundle` 内部类 + 字段 |
| `domain/BundleReader.java` | 新 | 读 ZIP 内 manifest + wheel 列表(纯逻辑) |
| `domain/PlatformMatcher.java` | 新 | 本机检测 + wheel 兼容匹配(纯逻辑,可单测) |
| `domain/DeployResult.java` | 新 | install 结果 record |
| `domain/DeployTarget.java` | 新 | Global/Venv 密封接口 |
| `command/PackageService.java` | 新 | 打包 output/ → bundle.zip |
| `command/DeployService.java` | 新 | 解压 + 筛选 + pip install |
| `ui/panel/DeployPanel.java` | 新 | 部署页 UI |
| `ui/CommandShell.java` | 改 | 加 deploy nav |
| `ui/panel/BuildVerifyPanel.java` | 改 | 构建后挂打包按钮 |
| `ui/panel/ConfigPanel.java` | 改 | 加「自动打包」勾选 |
| i18n 资源 | 改 | `opb.nav.deploy` 等 |

**改动面控制**: 3 个现有文件的功能性改动(CommandShell 加 nav、BuildVerifyPanel 挂按钮、ConfigPanel 加勾选),其余全是新增文件——不触碰 BuildService、VerifyService、DoctorService、Manifest 等核心现有逻辑。

## 10. 测试策略

- **PlatformMatcher**: 单测覆盖各平台组合(本机 tag → wheel 兼容/不兼容),含 macOS 区间匹配、纯 Python `any`、同包多 wheel。
- **BundleReader**: 单测读 ZIP 内 manifest(用测试 fixture ZIP)。
- **PackageService**: 单测 mock `output/` 结构 → 生成 ZIP → 解开校验内容/顺序。
- **DeployService**: 集成测试(mock ProcessRunner)验证 pip 命令构造、逐包汇总、venv 创建路径分支。
- 纯逻辑类(PlatformMatcher / BundleReader)优先 TDD,UI 与 Service 集成手动验证。
