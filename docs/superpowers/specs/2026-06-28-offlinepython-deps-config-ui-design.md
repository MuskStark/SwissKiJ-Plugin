# OfflinePython 依赖配置面板 — UI 重构设计

- 日期：2026-06-28
- 分支：`feature/offline-python-builder-v1`
- 模块：`SwissKitJ-Plugin-OfflinePython`（JavaFX UI）
- 状态：已通过设计评审，待编写实现计划
- 关联：`2026-06-26-offline-python-builder-design.md` §9（DepsPanel）、`2026-06-28-offlinepython-ui-glass-design.md` §7.4（漏掉的 `.glass-table` 样式）

## 1. 背景与问题

`DepsPanel`（依赖配置面板，`SwissKitJ-Plugin-OfflinePython/src/main/java/plugin/swisskit/offlinepython/ui/panel/DepsPanel.java`，252 行）在 §9 UI 重建（提交 a6b20fb）后存在三个可用性问题：

1. **表格数据难看清**：5 列全用默认 `TableCell` 渲染，文字 `rgba(255,255,255,0.85)` 低对比白字浮在透明玻璃背景上；未设字号、行高、内边距、斑马行、选中高亮。glass 设计 §7.4 原要的 `.glass-table` 样式漏掉了。
2. **平台选择反人类**：平台是行内自由文本框（`DepsPanel.java:102`，prompt「平台 (如 win_amd64)」），加载时硬编码成 `win_amd64`（`toRows:177`），保存时又被丢弃（`doSave:215`）——本质是装饰性的、不驱动任何东西。用户要的是**多选下拉**。
3. **蓝色按钮含义模糊**：面板内有 3 个蓝色(primary)按钮 `＋`/`保存`/`保存并去构建 →`，外加 3 个次要按钮，**无一带 Tooltip**，`＋` 与 `保存` 又挨得近。

**下游现状（关键约束）**：构建管线当前是**严格单平台**——`BuildConfig.Python.platform` 是单个 `String`（`BuildConfig.java:33`），`BuildService.build()` 只读一次（`BuildService.java:36`），`ProcessRunner.pipDownloadCommand` 只发一个 `--platform`（`ProcessRunner.java:29`），manifest 也只存一个平台（`BuildService.java:83`、`Manifest.java:21`）。**但 pip `download` 原生支持重复 `--platform` 标签**，因此多平台打通可行。

## 2. 目标 / 非目标

**目标**
- 表格可读：明确字号、行高、对比、对齐、斑马、选中高亮。
- 平台改为工具栏全局**多选下拉**，所见即所得——选中的平台真的会为每个平台拉 wheel。
- 蓝色按钮收敛为唯一主 CTA，其余次要 + Tooltip + 明确文案。
- 打通构建层：`config.python.platforms[]` → 多 `--platform` → manifest 记录全部。

**非目标**
- 不动左栏导航、顶栏、日志 dock 等其他面板（仅 `DepsPanel`、构建管线、配置/manifest 数据模型）。
- 不改 `requirements.txt` 格式（仍只存 name + versionSpec；平台不进 requirements.txt，留在 config.json）。
- 不做 per-dependency 的平台差异化（平台是全局的，所有依赖统一为所选平台集合构建）。
- 不引入新的第三方依赖（继续用 Lombok `@Data` + Gson）。

## 3. 设计总览

五块改动（A–E）：
- **A** 表格可读性（`DepsPanel` 渲染 + `OpbStyle` 助手）
- **B** 全局平台多选下拉（新增 `PlatformMultiSelect`，删行内列 + pill）
- **C** 按钮收敛 + Tooltip（`DepsPanel`）
- **D** 打通构建层（`BuildConfig` / `Manifest` / `ProcessRunner` / `BuildService` / `BuildPanel`）
- **E** 测试

## 4. 详细设计

### 4.A 表格可读性

表格 `TableView<DepsPanel.Row>`（`DepsPanel.java:52`），重构后为 **5 列**（包名 / 版本约束 / 目标平台 / 预估大小 / 删除 ✕）。其中「目标平台」列**镜像全局多选下拉的当前选择**（平台是全局驱动构建的，故每行显示相同值；不再行内可编辑、不再硬编码 `win_amd64`，见 4.B）。

新增 `OpbStyle` CSS 助手（`OpbStyle.java`，集中管理内联 CSS）：
- `tableHeaderStyle()` — 表头：`TEXT_SECONDARY`、11px、加粗、大写、左对齐。
- `tableRowStyle(boolean odd, boolean selected)` — 行底：隔行 `GLASS_BG` / `GLASS_BG_HOVER`（斑马）；选中 `-accent-soft` 底 + `ACCENT` 左边框。
- `tableCellStyle(boolean bold, String align, boolean mono)` — 单元格：13px、`rgba(255,255,255,0.92)`、`-fx-padding: 4 8 4 8`、可选加粗/对齐/等宽。

列渲染（每列一个 `cellFactory`）：
- **包名**：左对齐 + 加粗（`TEXT_PRIMARY`）。
- **版本约束**：居中、`TEXT_SECONDARY`、等宽（`-fx-font-family: monospace`）。
- **目标平台**：居中；glass 底色（`tableRowStyle`）+ **全不透明白字 `rgba(255,255,255,1.0)`** 增强对比；显示全局选择的紧凑汇总（如 `win_amd64、manylinux +1`，沿用 `PlatformMultiSelect` 的汇总文案）。cell factory 不读行数据，而是读当前全局 `PlatformMultiSelect.getSelected()`；下拉变化时 `table.refresh()` 刷新全列。
- **预估大小**：右对齐、等宽；无值时显示 `—`（`TEXT_TERTIARY`）。
- **删除 ✕**：沿用现有 `UiUtils.glassBtn("✕", false)`（`DepsPanel.java:74-80`）。

表格本身：`setFixedCellSize(30)`（行高）、`-fx-font-size: 13px`、保留 `CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS`、`setMinHeight(150)`。

### 4.B 全局「目标平台」多选下拉

**保留并改造**（`DepsPanel.java`）：
- 「目标平台」列（`:71`）**保留**，但改为只读镜像全局选择（cell factory 读 `PlatformMultiSelect`，见 4.A）；不再行内可编辑。

**删除**（`DepsPanel.java`）：
- 平台输入框 `pField`（`:102`）及其在 add 流程（`:104-111`）的使用。
- 只读 `platformPill`（`:122, 144-149`）（其职能已由表格目标平台列 + 工具栏多选下拉覆盖）。
- `toRows()`（`:175-179`）里硬编码的 `"win_amd64"`（行模型不再带平台）。
- `Row.platform` 字段及其 `platformProperty()` 访问器（`:34-49`）——目标平台列改为读全局，行模型只保留 `name / version / size`。

**新增** `PlatformMultiSelect extends MenuButton`（新文件 `ui/control/PlatformMultiSelect.java`）：
- JavaFX idiomatic 多选下拉：`MenuButton` + 每个平台一个 `CheckMenuItem`，绑定选中状态。
- **汇总文案**：选中 1 个显示该平台；≥2 显示 `win_amd64、manylinux +1`；用 `getPrimaryPlatform()` + 计数。
- **平台清单**（pip 合法 `--platform` 标签 + 中文显示名），定义在新文件 `domain/PlatformCatalog.java`：

  | 标签 | 显示名 |
  |---|---|
  | `win_amd64` | Windows x64 |
  | `win32` | Windows x86 |
  | `manylinux2014_x86_64` | Linux x64 |
  | `manylinux2014_aarch64` | Linux ARM64 |
  | `macosx_10_15_x86_64` | macOS Intel |
  | `macosx_11_0_arm64` | macOS Apple Silicon |
  | `any` | 通用（纯 Python） |

  （清单可后续增删；默认全部可选，首启默认勾选 `win_amd64`，沿用现状。）
- **至少保留 1 个**：取消最后一个选中项时回滚并 toast 提示「至少需要一个目标平台」。
- API：`List<String> getSelected()`、`void setSelected(List<String>)`。

**预估大小**：`doPyPIFetch`（`DepsPanel.java:194-208`）改用 `platformMultiSelect.getPrimaryPlatform()` 取 wheel size；汇总条注明 `×N 平台（按主平台估算）`。

### 4.C 按钮收敛 + Tooltip

`DepsPanel` 按钮最终态（全部 `UiUtils.glassBtn`，加 `Tooltip`）：

| 位置 | 文案 | 样式 | Tooltip |
|---|---|---|---|
| 工具栏 | `导入 requirements.txt` | 次要 | 选择本地 `requirements.txt` 并解析为依赖表 |
| 工具栏 | `PyPI 查询版本` | 次要 | 为选中行从 PyPI 查询最新版本与 wheel 大小 |
| 工具栏右侧 | `+ 添加依赖` | 次要 | 添加一行依赖，平台跟随全局目标 |
| 行内 | `✕` | 次要 | 删除该行（沿用） |
| 底部 | `保存 requirements.txt` | 次要 | 仅保存依赖与配置，不构建 |
| 底部 | `保存并去构建 →` | **唯一蓝色 primary** | 保存后跳转构建面板 |

「目标平台」多选下拉放在工具栏（导入/查询/添加之后）。

### 4.D 打通构建层

1. **`BuildConfig.Python`**（`BuildConfig.java:31-37`）：`private String platform;` → **`private List<String> platforms;`**。Lombok `@Data` 自动生成 getter/setter；Gson 透明序列化（`JsonStore` 无自定义 adapter）。默认 `["win_amd64"]`（`defaults()` `:15`）。新增便捷方法 `getPrimaryPlatform()`（返回 `platforms` 非空时的首个，否则 `"win_amd64"`）。
2. **旧配置兼容（field-initializer 兜底，无显式迁移）**：`BuildConfig.Python.platforms` 字段初始化器默认 `["win_amd64"]`。旧 `config.json` 里的 `"platform"` 单值键因字段已删除而被 Gson 忽略，`platforms` 走初始化器兜底为 `["win_amd64"]`——不报错、列表合法。**非默认的旧 platform 取值不迁移**（本分支未发布，且 DepsPanel 此前从不持久化平台，可接受）。该行为由 `BuildConfigTest.loadsLegacySinglePlatformConfigGracefully` 固化。`ProjectContext.reloadConfig()` 无需改动。
3. **`ProcessRunner.pipDownloadCommand`**（`ProcessRunner.java:22-33`）：签名 `..., String platform, ...` → `..., List<String> platforms, ...`；对每个平台 `cmd.addAll(List.of("--platform", p))`。`platforms` 为空时兜底用 `["any"]`（防 pip 报错）。
4. **`BuildService.build()`**（`BuildService.java:23-50`）：`cfg.getPython().getPlatform()` → `cfg.getPython().getPlatforms()`，传给 `pipDownloadCommand`。
5. **`Manifest.Python`**（`Manifest.java:21`）：`platform: String` → `platforms: List<String>`。`BuildService.writeManifest`（`:83`）写全部平台。读取侧（`VerifyService` 等）按需取 `platforms`（verify 不按平台匹配，无影响）。
6. **读取点更新**：
   - `BuildPanel` banner（`BuildPanel.java:57`）：`getPlatform()` → `getPrimaryPlatform()`。
   - `DepsPanel.currentPlatform()`（`:151-154`）：平台已提升为全局、行内不再带平台，该 helper 成为死代码——**删除**（其唯一调用点 platformPill / add 流程已在 4.B 移除）。

### 4.E 数据流

```
PlatformMultiSelect (选中)
   └─ doSave() ─> project.getConfig().getPython().setPlatforms(sel); project.saveConfig()
                    (config.json: { "python": { "platforms": [...] } })
   └─ BuildPanel.start() ─> cfg.getPython().getPlatforms()
        └─ BuildService.build()
             └─ ProcessRunner.pipDownloadCommand(..., platforms)
                  └─ pip download ... --platform win_amd64 --platform manylinux2014_x86_64 ...
             └─ Manifest.python.platforms = platforms
```

## 5. 错误处理 / 边界

- **空选择**：`PlatformMultiSelect` 拦截清空（回滚 + toast）。构建层 `platforms` 空时 `pipDownloadCommand` 兜底 `["any"]`。
- **pip 无某平台 wheel**：pip 自身会在 log dock 输出 warning（如 `--only-binary` 下找不到该平台 wheel 跳过），沿用现有日志输出，不额外处理。
- **预估大小取不到平台 wheel**：`DepsService.parsePyPIWheelSize` 已有回退（首个 wheel size，`DepsService.java:73-74`），不变。
- **旧 config.json**：见 4.D.2（field-initializer 兜底，无显式迁移）。
- **保存与构建解耦**：`保存 requirements.txt` 只写 requirements.txt + config，不构建；`保存并去构建 →` 同 `doSave(true)`，沿用 `NavEvent("build")`（`:234-237`）。

## 6. 测试

- **`ProcessRunnerTest`（扩）**：多 `--platform` 拼接顺序稳定、单平台回归、空集合兜底 `any`。
- **`BuildConfigTest`（扩）**：Gson 往返含 `platforms`；旧 JSON（仅残留 `platform` 单值键）经 field-initializer 兜底为 `["win_amd64"]`（非默认值不迁移，由 `loadsLegacySinglePlatformConfigGracefully` 固化）；`getPrimaryPlatform()` 兜底。
- **`ManifestTest`（扩）**：读写 `platforms`。
- **`PlatformMultiSelect`（手动验证）**：JavaFX 节点无测试框架；选中/汇总/至少一个的逻辑由 `PlatformCatalogTest` 覆盖，节点连线靠 DevLauncher 手动验证。
- **`PlatformCatalogTest`（新）**：清单标签均为 pip 合法格式（正则校验）。
- **`DepsServiceTest`（确认）**：size 按平台匹配（已有用例，确认主平台入参）。
- 现有 `DepsPanel` 相关用例若有断言行内列，需同步更新（移除平台列断言）。

## 7. 受影响文件

| 文件 | 改动 |
|---|---|
| `ui/panel/DepsPanel.java` | 主改：表格渲染、目标平台列改镜像全局/pill 移除、工具栏多选下拉、按钮收敛 + Tooltip、保存写 `platforms` |
| `ui/control/PlatformMultiSelect.java` | **新增**：`MenuButton` 多选下拉 |
| `domain/PlatformCatalog.java` | **新增**：平台清单（标签 + 显示名） |
| `ui/OpbStyle.java` | +3 助手：`tableHeaderStyle` / `tableRowStyle` / `tableCellStyle` |
| `domain/BuildConfig.java` | `Python.platform` → `platforms: List<String>` + `getPrimaryPlatform()` |
| `domain/Manifest.java` | `Python.platform` → `platforms: List<String>` |
| `infra/ProcessRunner.java` | `pipDownloadCommand` 改 `List<String> platforms`，循环发 `--platform` |
| `command/BuildService.java` | 读 `getPlatforms()`，manifest 写全部 |
| `ui/panel/BuildPanel.java` | banner 用 `getPrimaryPlatform()` |
| 测试（多处） | 见 §6 |

## 8. 风险

- **pip 多 `--platform` 行为**：当同时给 `--only-binary`（wheelFirst）且某平台无 wheel 时，pip 会跳过该包，可能导致部分平台缺包。→ 日志已有 pip 输出可观测；后续可考虑在构建报告里按平台汇总缺失项（非本次范围）。
- **manifest 字段改名**：旧 manifest 文件读取兼容——`Manifest.Python.platforms` 为空时容错（verify 不依赖平台，影响低）。
