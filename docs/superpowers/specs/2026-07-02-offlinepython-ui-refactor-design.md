# OfflinePython 模块 UI 重构设计

> **Date:** 2026-07-02
> **Scope:** `SwissKitJ-Plugin-OfflinePython/src/main/java/plugin/swisskit/offlinepython/ui/**`
> **Goal:** 按 SwissKitJ 官方 UI 设计规范(`docs/ui-design/`)对 OfflinePython 插件 UI 做一次系统性重构 —— 合并侧边栏、统一视觉、改善交互、抽取可复用组件。
> **Non-goal:** 不改 `command/`、`domain/`、`infra/`、`task/` 下的业务逻辑;不改服务层签名;不引入新功能命令。

---

## 1. 背景与问题诊断

当前 UI 已完成 V1 功能闭环(init / deps / build / verify / doctor),但围绕**瑞士规范一致性**、**信息架构**、**交互体验**、**组件复用**四方面存在系统性欠债。本次重构聚焦这四点,不动业务逻辑。

### 1.1 违反 SwissKitJ UI 规范的具体问题

对照 [`01-design-system.md`](../../SwissKitJ/docs/ui-design/01-design-system.md) 与 [`03-component-library.md §S1`](../../SwissKitJ/docs/ui-design/03-component-library.md) 逐条核查,当前实现存在以下违规:

| # | 问题 | 规范要求 | 当前代码 |
|---|---|---|---|
| V1 | **导航选中态蓝色填充** —— `OpbStyle.navItem(selected=true)` 把选中项背景染成 `-sk-accent-soft`、文字染成 `-sk-accent` | S1:选中项应为**中性** `-sk-bg-selected` 填充 + **3px 左侧 `-sk-accent` 条**,文字升至 `-sk-text`。规范 §6 把"Blue-flood selection"列为反模式 | `OpbStyle.java:57-62` |
| V2 | **glassmorphism 命名残留** —— 类名/注释仍称 "glass card / glass token",`CommandShell` 注释写 "host glass tokens" | §6 反模式:glassmorphism 在 v3.2.0 已弃用,`.glass-*` 改名 `.sk-*`,应使用 flat `-sk-bg-elevated` 表面 | `OpbStyle.java` 全文、`CommandShell.java:30` |
| V3 | **硬编码 hex/rgba** —— 顶栏徽章等处混用硬编码颜色;`UiUtils.glassBtn`(宿主)主按钮写死 `#3574F0`、次按钮写死 `rgba(255,255,255,0.07)` | P3:任何颜色只能用 `-sk-*` 令牌或 `.sk-t*`/`.sk-surface*` 工具类,禁止 inline hex/rgba | `UiUtils.java:24-41`(宿主,但插件直接调用导致插件也被污染) |
| V4 | **图标填色硬编码** —— `PlatformMultiSelect`/`DepsPanel` 用 `OpbStyle.TEXT_PRIMARY` 作图标色参数,但 `MdiIconUtil.createIcon` 的图标颜色本应随主题;部分图标色用 `WHITE` 常量 | F1:文字/图标色应走 `.sk-t1/.sk-t2/.sk-t3` 或对应令牌,不得硬编码 | `DepsPanel.java:57`、`PlatformMultiSelect.java:38` |
| V5 | **圆角/字号越界** —— 个别地方出现 9px、10px 等非规范圆角;瓦片值字号 16px 超出 11/12/13/13.5/15 字号阶 | §3.4 圆角阶:6/8/10/999;§3.2 字号阶:11/12/13/13.5/15 | `BuildPanel.java:121`(16px)、`OpbStyle.countBadge`(9px radius) |
| V6 | **间距非 4 倍数** —— `topBar` padding `9 14`、nav padding `7 12`、`-fx-padding: 5 10` 等多处 | §3.3 间距网格:4 的倍数,默认 8 | `CommandShell.java:78,100,152` 等多处 |

### 1.2 信息架构问题

- **侧边栏过细**:13 个导航项中 8 个是 V2/V3 禁用占位(update/clean/pack/export/list/info/cache),实际可用仅 5 个(init/deps/build/verify/doctor)。占位项制造视觉噪声、违背 P1(functional-first)。
- **InitPanel 过空**:打开后只有标题 + 两行说明,空间利用率低。
- **build 与 verify 分两页跳转**:二者是紧邻工作流(构建→校验),分页增加跳转成本。

### 1.3 交互体验问题

- **无统一空状态**:未打开项目时各面板用零散文案,缺少一致的"请先打开项目"引导。
- **构建结果瓦片风格孤立**:`BuildPanel` 的结果瓦片是手写 VBox,与其它面板无复用。
- **日志级别筛选 pill** 用 `setUserData(Boolean)` + 重建可见集合的写法脆弱,且 pill 选中态颜色硬编码。

### 1.4 组件复用问题

- 内联样式散落各面板(`DepsPanel`/`VerifyPanel`/`DoctorPanel` 各自重复写徽章/分段/瓦片样式),缺少抽象。
- `CommandPanel` 基类只提供标题,缺少统一的"面板头(标题 + 右侧操作区)"抽象。

---

## 2. 设计目标(对应用户确认的四个方向)

1. **视觉一致性** —— 消除上述 V1–V6 全部违规;所有颜色走令牌/工具类;统一圆角、字号、间距阶。
2. **信息架构** —— 侧边栏从 13 项收缩到 **4 项**(项目 / 配置 / 构建校验 / 工具);build+verify 合并为同页上下分区。
3. **交互体验** —— 统一空状态、加载态、错误态;主从编辑反馈保留;日志抽屉化。
4. **组件复用** —— 抽取 `PanelHeader`、`StatTile`、`StatusBadge`、`EmptyState`、`LogDrawer` 等可复用组件,收敛到 `OpbStyle` + `ui/control/`。

---

## 3. 信息架构(导航合并)

### 3.1 新导航结构(4 项)

| 新导航项 | 中文标签 | 合并自 | 容纳面板 |
|---|---|---|---|
| `project` | 项目 | init | `ProjectPanel`(增强版 InitPanel) |
| `config` | 配置 | deps | `ConfigPanel`(= 现 DepsPanel,整改) |
| `build` | 构建校验 | build + verify | `BuildVerifyPanel`(上下两分区) |
| `doctor` | 工具 | doctor | `DoctorPanel`(整改) |

V2/V3 占位项(update/clean/pack/export/list/info/cache)**全部移除** —— 不再渲染禁用占位,未来加功能时再加回。`LABELS` 中 V2/V3 条目、`versionTag()`、`GROUPS` 双组结构全部删除。

### 3.2 工作流

```
项目(打开/新建)  →  配置(依赖表)  →  构建校验(构建 ↓ 校验)  →  [工具: 诊断]
```

四个导航项按工作流自上而下排列,符合从左到右的使用顺序。

---

## 4. 布局骨架

### 4.1 整体 BorderPane 分配

```
┌──────────────────────────────────────────────────────────┐
│ TopBar:  [ my-project ▾ ]              [ Python 3.12 ✓ ] │  top
├────────┬──────────────────────────────────┬──────────────┤
│        │                                  │              │
│ 项目   │                                  │  LogDrawer   │  right
│ 配置   │       Content(活动面板)           │  (可折叠)     │
│ 构建校验│                                  │              │
│ 工具   │                                  │              │
│        │                                  │              │
│ Nav    │                                  │              │
├────────┴──────────────────────────────────┴──────────────┤
│  (无 bottom —— 日志已移到右侧)                              │
└──────────────────────────────────────────────────────────┘
   left              center                       right
```

- **root**:`BorderPane`,`top=TopBar`,`left=Nav`,`center=Content`,`right=LogDrawer`。移除 `bottom`。
- 全部内联样式收敛进 `OpbStyle` 新增方法,面板内不再出现裸颜色字符串。

### 4.2 TopBar(重设计)

```
[ 📁 my-project ▾ ]                              [ Python 3.12 · pip 24.0  ✓ ]
```

- **左侧:项目切换器** —— 单个 `MenuButton`(或可点击 Label + 下拉),显示当前项目名 + `▾`。点击展开菜单含:**新建…** / **打开…** / (最近项目列表,可选 V2)。新建/打开从顶栏独立按钮收进此菜单,减少顶栏按钮数量。
  - 未打开时显示 `(未打开项目) ▾`。
- **右侧:Python 检测徽章** —— 复用 `OpbStyle.badge(ok)`(已合规:用 success/danger 令牌)。点击徽章跳转到 `doctor` 页(新增交互)。
- 样式:`OpbStyle.topBar()` 新增方法,统一 padding `8 16`(规范网格)、`-sk-bg` 背景、底部 1px `-sk-border` 分隔线。

### 4.3 Nav(整改为规范导航项)

严格按 S1 实现。每个 nav 项:

```
   ┌─────────────────────────────┐
   │ ▢  项目              [ 3 ]  │   icon + label(Hgrow) + 可选 badge
   └─────────────────────────────┘
   idle:  bg transparent,         文/图 -sk-text-secondary
   hover: bg -sk-bg-hover,        文/图 -sk-text
   active:bg -sk-bg-selected,     文/图 -sk-text,  + 左 3px -sk-accent border
```

- **图标**:每项配一个 MDI 图标(项目=`folder`,配置=`package-variant-closed`,构建校验=`hammer-wrench`,工具=`stethoscope`),走 `MdiIconUtil`,颜色随状态切换(用 `-sk-text-secondary`/`-sk-text`,**不再硬编码 WHITE**)。
- **active 左侧条**:通过 `-fx-border-color: transparent transparent transparent -sk-accent; -fx-border-width: 3 0 0 0;` 实现(或用 `Border` 的 `Background` 多层填充左侧色条)。
- **deps 计数徽章** 移到 `config` 项:复用 `OpbStyle.countBadge()`,但圆角改为规范的 6px(V5 修复),背景 `-sk-accent`、文字 white(徽章是少数允许 accent 填充的控件)。
- **分组标题**(原"仓库操作"/"查看与工具")删除 —— 4 项不需要分组。
- **宽度** `SIDEBAR_WIDTH=220` 保留(合规,>56px)。

### 4.4 LogDrawer(右侧固定窄抽屉,新组件)

```
┌──────────────┐
│ 日志    [◂]  │  标题 + 折叠按钮
├──────────────┤
│ DEBUG INFO   │  4 个级别 pill(筛选)
│ WARN  ERROR  │
├──────────────┤
│ [12:00:01]   │
│ 已打开项目…  │  TextArea,可滚动
│ ...          │
└──────────────┘
```

- **位置**:`root.setRight(logDrawer)`。固定宽度 `OpbStyle.LOG_DRAWER_WIDTH = 240`(4 倍数)。
- **折叠态**:点折叠按钮后宽度收缩为 `40px` 图标条(只留一个"日志"图标 + 展开箭头),`setManaged/setVisible` 控制 TextArea 显隐。复用现有 `LogConsole` 的 `setCollapsed` 机制。
- **级别 pill**:抽取为 `LogLevelPill` 控件,选中态用 `-sk-accent-soft` 背景 + `-sk-accent` 文字(V3 修复,不再硬编码),未选用 `-sk-bg-hover` + `-sk-text-secondary`。
- **样式**:`OpbStyle.logDrawerStyle()`、`OpbStyle.logPillStyle(boolean on)` 新增方法。
- 现有 `LogConsole`(TextArea + 过滤逻辑)保留为抽屉的 body 组件,顶部 bar 移入 `LogDrawer`。

---

## 5. 各面板设计

### 5.1 公共:`PanelHeader`(新组件,复用)

替换现有 `titleNode()`。统一面板头:

```
┌──────────────────────────────────────────────┐
│ 配置                              [🔍 搜索][+ 增加] │  标题(sk-t1,15px) + 右侧操作区
└──────────────────────────────────────────────┘
```

- `ui/control/PanelHeader.java`:`HBox`,左 `Label`(15px,`.sk-t1`,文本来自 `title()`),右 `HBox`(各面板注入操作按钮,可空)。
- `CommandPanel` 基类改为:`getChildren().add(header = new PanelHeader(title()))`,子类通过 `header.addActions(Node...)` 注入操作。

### 5.2 `ProjectPanel`(原 InitPanel 增强)

现状 InitPanel 仅标题 + 两行说明。增强为有意义的"项目"页:

```
┌─ 项目 ─────────────────────────────────────────┐
│                                                │
│  当前项目                                      │
│  ┌──────────────────────────────────────┐      │
│  │ 📁 /path/to/my-project               │ 打开 │  项目路径卡片(未打开则空状态)
│  └──────────────────────────────────────┘      │
│                                                │
│  初始化会生成:                                 │
│  • config.json   • requirements.txt  • README  │
│                                                │
│              [＋ 新建项目]  [📂 打开项目]       │
└────────────────────────────────────────────────┘
```

- 顶部显示当前项目路径卡片(若有),否则显示 `EmptyState`("未打开项目",引导点新建/打开)。
- 底部主操作:新建 / 打开(主按钮 primary)。这些按钮也可与顶栏项目切换器联动(顶栏更轻,这里更显眼)。
- 文案保留 i18n `opb.init.title`。

### 5.3 `ConfigPanel`(原 DepsPanel 整改)

功能不变(导入 / 表单 / 表格 / 选项 / 摘要),仅做规范整改 + 复用:

- 头部用 `PanelHeader`,右侧操作放"导入 requirements.txt""🔍 在线搜索""增加配置"。
- 表单区(包名/版本/平台)字段用 `UiUtils.fieldStyle()`(宿主,需评估是否换 `.sk-field` class —— 见 §8 风险)。
- 表格保持 `.sk-table`(合规),平台列图标色改用令牌(V4 修复,删 `WHITE` 常量)。
- 摘要栏用 `OpbStyle.card()`(改名后语义仍是 elevated surface)。
- 主从编辑(选中行→载入表单)逻辑保留。

### 5.4 `BuildVerifyPanel`(build + verify 合并,上下分区)

```
┌─ 构建校验 ─────────────────────────────────────┐
│ ┌─ 构建 ────────────────────────────────────┐  │
│ │ 📋 当前依赖:5 个 · 目标 win_amd64 · Py3.12 │  │  banner
│ │ [▶ 构建]  [✕ 取消]        ▓▓▓▓▓░░░░  60%  │  │  操作 + 进度
│ │ ┌──────┐┌──────┐┌──────┐┌──────┐          │  │
│ │ │已下载││耗时  ││大小  ││缓存  │          │  │  StatTile ×4
│ │ │  42  ││ 18s  ││120MB ││  8   │          │  │
│ │ └──────┘└──────┘└──────┘└──────┘          │  │
│ └───────────────────────────────────────────┘  │
│ ┌─ 校验 ────────────────────────────────────┐  │
│ │ [全量][仅完整性][仅SHA256]    [▶ 开始校验] │  │  分段 + 操作
│ │ [PASS] sha256 校验通过                    │  │  报告行
│ │ [WARN] 2 个 wheel 缺失                    │  │
│ │ ✓ / ⚠ / ✕  结论条                         │  │
│ └───────────────────────────────────────────┘  │
└────────────────────────────────────────────────┘
```

- 上下两个子区,各用 `OpbStyle.card()` 包裹(8px radius,V5 修复后统一)。
- **构建区**:banner + 操作行 + 进度条 + `StatTile` ×4。
- **校验区**:分段控件(`sk-seg` 风格,见下)+ 操作 + 报告 + 结论。
- 复用新组件:`StatTile`(瓦片)、`StatusBadge`(状态徽章)。
- `BuildPanel` 的 `isRunning()`/`cancel()` 能力保留,`CommandShell.hasRunningTasks()`/`onUnload()` 改指向 `BuildVerifyPanel`。

#### `StatTile`(新组件)

```
┌──────────┐
│ 已下载    │  label: .sk-t3, 11px
│   42     │  value: .sk-t1, 15px(规范上限,不再用 16px,V5 修复)
└──────────┘
```
- `ui/control/StatTile.java`:`VBox`,`OpbStyle.card()` + 8 padding + center。
- 替换 `BuildPanel.addTile` 手写 VBox。

#### `StatusBadge`(新组件)

- `ui/control/StatusBadge.java`:`Label`,接受 `Status`,输出 `[PASS]/[WARN]/[FAIL]`。
- 样式:`-sk-success-soft`+`-sk-success` 等(V1 类问题已合规,本组件只是集中)。
- 替换 `VerifyPanel` 手写 badge Label。

#### 分段控件(`sk-seg` 风格)

当前 `VerifyPanel` 用 `ToggleButton` + `ToggleGroup` 但样式是裸的。整改为容器 `-sk-bg-hover` 背景 + 8px radius,选中项 `-sk-bg-selected` + `-sk-text`,未选 `-sk-text-secondary`。可抽 `SegControl` 控件(可选,优先级低)。

### 5.5 `DoctorPanel`(整改)

- 头部用 `PanelHeader`,右侧放"▶ 运行诊断"。
- 结果网格整改:✓/✕ 用 `Status`(PASS/FAIL),颜色走令牌(已基本合规,清理内联)。
- 间距对齐网格。

---

## 6. `OpbStyle` 整改

`OpbStyleTest` 锁定了现有方法签名,**必须全部保留**(测试不改)。本次策略:**保留所有现有方法**(实现可修正违规用法)+ **新增**抽屉/顶栏/瓦片等方法。

### 6.1 修正项(改实现,签名不变)

- `navItem(selected, hover)`:**核心修正** —— 选中态改为 `-sk-bg-selected` 填充 + 左 3px `-sk-accent` border + 文字 `-sk-text`(不再是 accent-soft 蓝填充 + accent 文字)。hover 维持 `-sk-bg-hover`。**注**:`OpbStyleTest.navItemSelectedUsesAccent` 断言 `sel.contains(ACCENT_SOFT)` 将失败 —— 需同步更新该测试断言为 `BG_SELECTED` + `ACCENT`(border)。此为唯一需要改测试的地方,因为规范本身判定旧实现违规。
- 命名层面的 "glass" 注释清理:类/方法 Javadoc 把 "glass card / glass token" 改述为 "elevated surface / surface token",常量名 `GLASS_*` 保留(改名将连锁影响所有面板,成本高、收益低,仅改注释)。
- `countBadge()`:圆角 9px → 6px(V5)。

### 6.2 新增方法

- `topBar()` —— 顶栏容器样式(`-sk-bg` + 底 border + padding 8 16)。
- `projectSwitcher()` —— 项目切换器样式(MenuButton)。
- `navItemIcon(boolean active)` —— nav 图标色(返回令牌字符串)。
- `logDrawerStyle()` / `logDrawerCollapsedStyle()` —— 抽屉容器样式。
- `logPillStyle(boolean on)` —— 级别筛选 pill 样式。
- `statTile()` —— 瓦片样式(等价 card,语义方法)。
- `segStyle(boolean selected)` —— 分段控件项样式。
- `LOG_DRAWER_WIDTH = 240`、`LOG_DRAWER_COLLAPSED_WIDTH = 40` 常量。

---

## 7. 国际化

i18n key 调整:

| 现有 key | 处理 |
|---|---|
| `opb.init.title` | 保留(ProjectPanel 用) |
| `opb.deps.title` | 保留(ConfigPanel 用) |
| `opb.build.title` | 保留(BuildVerifyPanel 构建区用) |
| `opb.verify.title` | 保留(BuildVerifyPanel 校验区用) |
| `opb.doctor.title` | 保留 |

新增 key(中英):

| key | zh | en |
|---|---|---|
| `opb.nav.project` | 项目 | Project |
| `opb.nav.config` | 配置 | Config |
| `opb.nav.build` | 构建校验 | Build & Verify |
| `opb.nav.doctor` | 工具 | Tools |
| `opb.project.empty` | 未打开项目 | No project open |
| `opb.project.new` | ＋ 新建项目 | New Project |
| `opb.project.open` | 📂 打开项目 | Open Project |
| `opb.log.title` | 日志 | Logs |

(顶栏/抽屉的新文案随之补齐。)

---

## 8. 风险与取舍

1. **`UiUtils.glassBtn` 宿主硬编码** —— 宿主 `UiUtils.glassBtn` 主按钮硬编码 `#3574F0`、次按钮硬编码 `rgba(255,255,255,0.07)`(V3)。本次**不改宿主**(超出本插件范围),插件内尽量改用带 `.sk-btn-primary`/`.sk-btn-secondary` class 的按钮(若宿主提供);若宿主无 class 版本,则在插件内自建 `OpbStyle.primaryBtn()`/`secondaryBtn()` 方法(用令牌)并逐步替换 `glassBtn` 调用。**优先级**:导航/顶栏/面板头等显眼处先换;表格内删除按钮等次要先留。
2. **`OpbStyleTest` 需改一处断言** —— `navItemSelectedUsesAccent` 因规范要求改选中态实现,该测试断言要同步更新为 `BG_SELECTED` + 左侧 `ACCENT` border。这是规范驱动的必要改动,会在实现计划中明确。
3. **nav 左侧 3px 条的 JavaFX 实现** —— `-fx-border-width: 3 0 0 0` + 左 border 色,需验证在 Button 上的渲染(Button 默认 padding 可能吃掉 border)。备选:用 `StackPane` 底层放一个 3px 宽的 accent 色 `Region` 作左条。实现时验证。
4. **日志抽屉折叠宽度** —— JavaFX `BorderPane` right 区折叠需手动设 prefWidth + managed,验证折叠/展开动画(可选,规范 07 建议 220/180ms,可后加)。
5. **合并页 `BuildVerifyPanel` 的状态联动** —— build 的 `isRunning` 需透传到 `CommandShell.hasRunningTasks()`。保持现有接口,仅换持有对象。

---

## 9. 验收标准

- [ ] 导航 4 项,无 V2/V3 占位;选中项为中性填充 + 左 3px accent 条(肉眼可辨,非蓝填充)。
- [ ] 全插件 `grep` 无硬编码 `#` hex、无 `rgba(255`(宿主 `UiUtils` 调用除外,已记录为遗留)。
- [ ] 圆角全部落在 {6,8,10,999};字号全部落在 {11,12,13,13.5,15};间距(p) 全部为 4 倍数。
- [ ] 日志在右侧抽屉,可折叠;级别筛选 pill 选中态走令牌。
- [ ] build+verify 同页上下分区,工作流不跳转。
- [ ] `OpbStyleTest` 全绿(含更新的 nav 断言);`mvn test` 全绿。
- [ ] 深色/浅色主题切换,UI 无冻结颜色。
