# OfflinePython 依赖配置面板 v2 — per-dep 平台 + 布局重排 + PyPI 在线搜索

- 日期：2026-06-29
- 分支：`feature/offline-python-builder-v1`
- 模块：`SwissKitJ-Plugin-OfflinePython`（JavaFX UI）
- 状态：已通过设计评审，待编写实现计划
- 关联：取代 `2026-06-28-offlinepython-deps-config-ui-design.md`（v1 的"全局平台"决策被本设计回退为 per-dependency 平台）

## 1. 背景与问题

v1 设计（`2026-06-28-offlinepython-deps-config-ui-design.md`）把目标平台做成**全局多选**（`PlatformMultiSelect` 在工具栏，驱动整张表所有依赖；构建层 `ProcessRunner.pipDownloadCommand` 单次 `pip download -r requirements.txt --platform <全局[]>`）。v1 还原了表格可读性、按钮收敛、构建层打通。

本次（v2）在 v1 基础上做三件事，源于明确的用户诉求：

1. **布局重排**：导入文件单独一行；包名/版本/目标平台一行；搜索+保存一行。
2. **per-dependency 平台**：用户要求"每个依赖单独设平台"——回退 v1 的全局平台决策。若构建层不改，平台又会退化为 v1 修复掉的"装饰性字段"，因此构建层必须同步改为按依赖平台分组下载。
3. **新增 PyPI 在线搜索**：点"在线搜索"弹窗输入包名 → 返回该包的 wheel 列表（版本/平台/大小/文件名）→ 用户选中一条 → 自动回填该依赖的 包名/版本/目标平台。

## 2. 目标 / 非目标

**目标**
- 三行式布局：导入独占一行；包名+版本+目标平台一行；在线搜索+保存配置一行。
- 平台为 **per-dependency**：表格每行携带自己的平台集合；新增/编辑依赖时在行2表单选择其平台。
- 构建层按平台集合分组 `pip download`，真正落地每个依赖各自的平台。
- PyPI 在线搜索按 wheel 条目返回，选中回填表单。
- 主从编辑：选中表格行 → 行2表单载入该行供编辑。

**非目标**
- 不改左栏导航、顶栏、日志 dock、构建面板主体（仅 DepsPanel、构建管线、配置/manifest 数据模型）。
- 不改 `requirements.txt` 文本格式（仍只存 name+version+marker；平台不进 requirements.txt，留在 config.json）。
- 不做跨组传递依赖的版本统一解析（见 §9 风险）。
- 不引入新的第三方依赖（继续用 Lombok `@Data` + Gson + JDK HttpClient）。

## 3. 设计总览

五块改动（A–E）：
- **A** 布局重排（`DepsPanel` 三行结构 + 按钮语义 + 主从编辑）
- **B** per-dep 平台数据模型（`BuildConfig.Python.depPlatforms` map；`Row` 加 `platforms`）
- **C** 构建管线按平台分组下载（`ProcessRunner` 签名改 specs 列表 + `recursive`→`--no-deps`；`BuildService` 分组循环；manifest 写平台并集）
- **D** PyPI 在线搜索对话框（新增 `PyPISearchDialog` + `DepsService.searchWheels` + `WheelInfo`）
- **E** 测试

## 4. 详细设计

### 4.A 布局重排 + 按钮语义

`DepsPanel.buildUi()` 重排为如下自上而下的行（沿用 v1 的玻璃卡片/表格样式与 `OpbStyle` 助手）：

```
依赖配置（titleNode）
┌─ 行1 ─ [导入 requirements.txt]                                   （独占一行）
├─ 行2 ─ [包名] [版本号(==1.26.4)] [目标平台 ▾]                     （per-dep 平台）
├─ 行3 ─ [🔍 在线搜索]                          [保存配置]           （搜索/保存一行）
├─ 表格 ─ 包名 │ 版本约束 │ 目标平台 │ 预估大小 │ ✕                  （per-row 平台列，只读）
├─ 选项 ─ ☑递归  ☑wheel优先  ☐升级pip
└─ 底栏 ─ 摘要 ……                            [保存并去构建 →]
```

**按钮语义**
- **导入 requirements.txt**（行1）：FileChooser → 解析为表格行（沿用 v1 `doImport`）。
- **目标平台 ▾**（行2）：一个 `PlatformMultiSelect`，代表**当前行2表单正在编辑/新增的这条依赖**的平台（不再是全局）。默认值取 `config.python.platforms`（"新增依赖默认平台"）。
- **🔍 在线搜索**（行3）：打开 `PyPISearchDialog`（§4.D）；用户选定 wheel 后，把 包名/版本/平台**回填进行2表单**（不自动提交）。
- **保存配置**（行3）：提交行2表单——若表格有选中行则**更新**该行，否则**新增**一行；随后持久化（写 `requirements.txt` + `config.json` 含 `depPlatforms`）。
- **保存并去构建 →**（底栏）：等同先 `保存配置` 再 `fireEventBuildNav()`（沿用 v1 `doSave(true)`）。
- **✕**（行内）：删除该行（沿用）。

**主从编辑**：表格 `selectionModel` 选中变化 → 行2表单的 包名/版本/平台 载入该行；清空选中 → 表单重置为新增态（平台重置为默认）。这样编辑现有依赖的平台走"选中→改平台→保存配置"。

**移除**（相对 v1）：工具栏全局 `PlatformMultiSelect`（平台下沉到行2 + 表格 per-row 列）、顶部"搜索包…"过滤框（`filterTable` 连同 `search` 字段，本就是 v1 留作占位的空实现）、"PyPI 查询版本"按钮（`doPyPIFetch`，被在线搜索取代）。手动输入的依赖预估大小显示 `—`。

### 4.B per-dependency 平台数据模型

**`Row`（`DepsPanel` 内部行模型）**：在 v1 的 `name/version/size` 基础上加 `platforms`：
```java
public final SimpleListProperty<String> platforms = ...; // 默认 ["win_amd64"]
```
（实现上用 `List<String>` 字段 + `platformsProperty` 访问器即可；`PlatformCatalog.summary(platforms)` 给表格列文案。）

**`BuildConfig.Python`**：
- 保留 `private List<String> platforms = ["win_amd64"]`，语义**降级为"新增依赖的默认平台"**（不再是构建驱动）。`getPrimaryPlatform()` 保留，给"新增默认"取首项。
- 新增 `private java.util.Map<String, java.util.List<String>> depPlatforms = new java.util.LinkedHashMap<>();` —— key 为 `DependencySpec.normalizeName(name)`，value 为该依赖的平台集合。Lombok `@Data` 生成 getter/setter；Gson 透明序列化 `LinkedHashMap`（`JsonStore` 无自定义 adapter，保持插入顺序）。

**保存（`DepsPanel.doSave`）**：
- 写 `requirements.txt`（name+version+marker，不变）。
- 重建 `depPlatforms`：`cfg.getPython().getDepPlatforms().clear();` 然后对每行 `put(normalizeName(name), new ArrayList<>(row.platforms))`。
- **不修改** `cfg.getPython().getPlatforms()`：`platforms` 字段语义是"新增依赖的默认平台"，只在 `defaults()` 与老配置加载时设定，UI 全程不改写，避免误覆盖。
- `project.saveConfig()`。

**加载（`DepsPanel.loadFromProject`）**：
- 读 `requirements.txt` → name/version。
- 每行 platforms = `depPlatforms.getOrDefault(normalizeName(name), cfg.getPython().getPlatforms())`（兜底默认平台）。
- 装配 `Row`。

### 4.C 构建管线按平台分组下载

**`ProcessRunner.pipDownloadCommand`** 签名改为接收**需求规格列表**而非 requirements 文件路径，并接入 `recursive`：
```java
public static List<String> pipDownloadCommand(String python, List<String> requirementSpecs,
                                              String destDir, List<String> platforms,
                                              String pythonVersion, String implementation,
                                              boolean onlyBinary, boolean recursive)
```
- 命令体：`python -m pip download <spec1> <spec2> … -d destDir --platform p1 --platform p2 … --python-version V --implementation I [--only-binary=:all:] [--no-deps]`。
- specs 直接作为位置参数追加（替代 `-r requirements`）；空 specs 视为无操作（`BuildService` 不应传入空组）。
- `platforms` 空 → 兜底 `["any"]`（沿用 v1）。
- `recursive == false` → 追加 `--no-deps`（让 v1 的死复选框生效；pip download 默认递归）。

**`BuildService.build()`** 重构为分组循环：
1. 读 `requirements.txt` → `List<DependencySpec> deps`（抽出为方法复用，`writeManifest` 也用）。
2. 对每个 dep 计算 `platforms = cfg.getPython().getDepPlatforms().getOrDefault(normalizeName(name), cfg.getPython().getPlatforms())`。
3. 按 `platforms` 集合（有序列表的稳定 key，如 `String.join(",", sortedUnique)`）**分组**；每组得到 `(platforms, List<DependencySpec>)`。
4. 对每组：`specs = deps.map(DependencySpec::toString)`；`cmd = pipDownloadCommand(python, specs, wheelhouse, groupPlatforms, ver, impl, onlyBinary, recursive)`；`onLog.accept("$ " + join)`；`code = runner.run(cmd, onLog)`；任一组 `code != 0` → 提前返回失败 `BuildSummary`（沿用 v1 的 preExisting 计数策略）。
5. 全部成功后 `writeManifest`（manifest 平台写并集，见下）+ `writeSha256Sums` + 返回 `BuildSummary.compute`。

**`Manifest.python.platforms`**：改为写**所有依赖平台的并集**（`new ArrayList<>(unionOfAllDepPlatforms)`，去重保序）。读取侧（`VerifyService` 等）不按平台匹配，无影响。

**`recursive` 来源**：`BuildService.build` 已收 `cfg`，取 `cfg.getDownload().isRecursive()` 传入（v1 未接线，本次接上）。

### 4.D PyPI 在线搜索对话框

**新增 `command/WheelInfo`（record）**（放 `command` 包与 `DepsService` 同包，便于测试）：
```java
public record WheelInfo(String version, String platformTag, long sizeBytes, String filename) {}
```

**`DepsService` 新增**（纯解析可单测）：
```java
public List<WheelInfo> searchWheels(String pkg)        // 拉 https://pypi.org/pypi/<pkg>/json，后台调用
public static List<WheelInfo> parseWheels(String json) // 纯解析：遍历 releases，收集 .whl
```
- 解析：`root.releases` 是 `{version: [file…]}`；对每个 version 的每个 file，`filename.endsWith(".whl")` 才取，`size = file.size`，`platformTag = extractPlatformTag(filename)`。
- `extractPlatformTag(filename)`：wheel 文件名形如 `numpy-1.26.4-cp312-cp312-win_amd64.whl`，去掉 `.whl` 后按 `-` 切分，末段（或末几段含下划线的连续段）为平台标签；与 `PlatformCatalog.ALL` 的 tag 比对，命中则用该 tag，否则保留原始末段（如 `macosx_10_9_x86_64` 不在精简目录时原样显示）。
- 排序：version 降序（按 PyPI 返回的 releases 顺序，PyPI 通常已近降序；不做严格语义版本排序，保持轻量）。限 ~50 条。
- 网络/解析异常 → 返回空列表（调用方据空列表提示）。

**新增 `ui/dialog/PyPISearchDialog`（独立 `Stage`）**：
- 布局：`[包名 TextField][搜索 Button]` + `TableView<WheelInfo>`（列：版本 / 平台 / 大小 / 文件名）+ `[确定][取消]`。
- 搜索：后台线程调 `deps.searchWheels(pkg)`，`Platform.runLater` 填表；期间按钮置灰 + 提示"查询中…"。空结果 → toast/行内提示"未找到 wheel"。
- 确定：取选中 `WheelInfo`，`showAndWait` 返回 `Optional<WheelInfo>` 给 `DepsPanel`。
- 边界：包名空 → 不发请求；网络失败 → toast 错误；窗口模态（`initModality(APPLICATION_MODAL)` + `initOwner`）。

**回填（`DepsPanel`）**：拿到 `WheelInfo` 后，行2表单 `nField = pkg`、`vField = "==" + version`、`platformSelect.setSelected(List.of(platformTag))`（若 platformTag 在目录内；否则只设该单值，`PlatformMultiSelect.setSelected` 接受任意单值）。**不自动提交**，用户确认后点"保存配置"。

### 4.E 数据流

```
行2表单 (name/version/platforms)
   └─ 保存配置 ─> table 行更新/新增 + 写 requirements.txt + config.depPlatforms + saveConfig
在线搜索 ─> PyPISearchDialog ─> WheelInfo ─> 回填行2表单
构建 ─> BuildService.build()
   ├─ 读 requirements.txt → deps；每 dep 平台 = depPlatforms[norm] ?: python.platforms
   ├─ 按 platform-set 分组
   ├─ 每组 pip download <specs…> --platform <组平台…> [--no-deps]
   └─ manifest.python.platforms = 全部平台并集
```

## 5. 错误处理 / 边界

- **空选择平台**：`PlatformMultiSelect` 拦截清空（回滚 + toast，沿用 v1），保证每条依赖至少 1 个平台。
- **构建层空 specs**：`BuildService` 不产生空组（deps 非空才进入构建；deps 空则在 `BuildPanel`/`BuildService` 入口拦截提示）。
- **某组 pip 失败**：提前返回失败 summary，log dock 已有该组命令与 pip 输出可观测。
- **depPlatforms 缺某依赖**：加载/构建均用 `python.platforms` 兜底。
- **老 config.json**：无 `depPlatforms` 键 → Gson 反序列化为 `null`/空 map（字段初始化器兜底空 map）；`python.platforms` 沿用 v1 field-initializer 兜底 `["win_amd64"]`。不报错。
- **在线搜索无 wheel / 包不存在**：空列表 + 提示；网络失败 → toast。
- **wheel 平台标签不在目录**：原样显示并作为单值写入该依赖平台（pip 接受任意合法 `--platform`）。

## 6. 测试

- **`ProcessRunnerTest`（改）**：specs 列表拼接（多 spec、多 `--platform`）、`recursive=false`→`--no-deps`、`recursive=true` 无 `--no-deps`、空平台兜底 `any`。
- **`BuildConfigTest`（扩）**：Gson 往返含 `depPlatforms` map；老 JSON（无 `depPlatforms`）兜底空 map 不报错；`python.platforms` 默认兜底沿用 v1 用例。
- **`ManifestTest`（扩）**：`python.platforms` 写并集（构造多平台并集断言）。
- **`DepsServiceTest`（扩）**：`parseWheels` 用离线 JSON fixture——多 release、含 .whl 与 .tar.gz（过滤）、平台标签提取（win_amd64/manylinux2014_x86_64/目录外标签）、限 ~50、version 降序、异常 JSON 返回空。
- **`BuildService` 分组（新，纯逻辑可测）**：把"deps + depPlatforms → 分组"抽成纯方法（如 `groupByPlatform(List<DependencySpec>, Map<String,List<String>>, List<String> default)`），单测分组结果（不同平台集合分组、兜底默认、normalizeName key）。pip 调用本身靠手动/DevLauncher 验证。
- **`PyPISearchDialog`**：JavaFX 节点，靠 DevLauncher 手动验证；解析逻辑由 `DepsServiceTest` 覆盖。
- 现有 `DepsPanel` 相关断言若有"全局平台"假设需同步（DepsPanel 无单测，主要是 DevLauncher 手动）。

## 7. 受影响文件

| 文件 | 改动 |
|---|---|
| `ui/panel/DepsPanel.java` | 三行布局；行2 per-dep `PlatformMultiSelect`；表格 per-row 目标平台列；主从编辑；移除全局平台/搜索框/PyPI按钮；保存写 `depPlatforms` |
| `ui/dialog/PyPISearchDialog.java` | **新增**：独立 Stage 搜索 wheel → 选定回填 |
| `command/DepsService.java` | +`searchWheels(pkg)` + `parseWheels(json)` + `extractPlatformTag` |
| `command/WheelInfo.java` | **新增**：record(version, platformTag, sizeBytes, filename) |
| `domain/BuildConfig.java` | `Python` +`depPlatforms` map；`platforms` 降为默认（UI 不再写） |
| `infra/ProcessRunner.java` | `pipDownloadCommand` 改 `List<String> specs` + `recursive`→`--no-deps` |
| `command/BuildService.java` | 读 requirements 抽方法；按平台集合分组下载；manifest 写平台并集；接 `recursive` |
| 测试 | `ProcessRunnerTest`/`BuildConfigTest`/`ManifestTest`/`DepsServiceTest` 改 + 新增分组/解析用例 |

## 8. 风险

- **跨组传递依赖解析不统一**：不同平台集合的依赖各自一组 `pip download`，共享传递依赖的版本约束不做跨组统一；wheelhouse 内 pip 按文件名去重（已存在则跳过）。离线 wheel 收集场景可接受；后续可在构建报告按平台汇总（非本次范围）。
- **`pip download <spec> --platform` 行为**：`--only-binary=:all:` 下某平台无 wheel 时 pip 跳过该包并告警（log dock 可观测）。
- **在线搜索平台标签提取**：依赖 wheel 命名规范（PEP 427）；少数非标准命名可能提取出目录外标签——按"原样显示并单值写入"兜底，不阻断流程。
- **manifest 平台字段语义变化**：由"全局平台"变为"全部依赖平台并集"；读取侧（verify）不依赖平台，影响低。
