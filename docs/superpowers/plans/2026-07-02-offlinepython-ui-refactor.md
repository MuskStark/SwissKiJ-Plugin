# OfflinePython UI 重构 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 按 SwissKitJ 官方 UI 设计规范对 OfflinePython 插件 UI 做系统性重构:合并侧边栏(13→4 项)、日志右侧抽屉化、build+verify 合并页、消除规范违规(蓝填充选中态/硬编码颜色/越界圆角字号)。

**Architecture:** 保留 `command/`、`domain/`、`infra/`、`task/` 业务层不动;重写 `ui/` 层 —— `CommandShell` 改 4 区布局(top/left/center/right),nav 改 4 项,新增 `ui/control/` 可复用组件(`PanelHeader`/`StatTile`/`StatusBadge`/`EmptyState`/`LogDrawer`/`LogLevelPill`/`ProjectSwitcher`),合并 build+verify 面板,`OpbStyle` 修正 nav 选中态 + 新增抽屉/顶栏方法。

**Tech Stack:** JavaFX 21,SwissKitJ-Api(host: `UiUtils`/`MdiIconUtil`/`Themes`/`GlassNotification`/`I18n`),JUnit 5,Maven。

## Global Constraints

- **颜色**:只用 `-sk-*` 令牌(19 个,见 `05-theme-color-system.md`),禁止 inline hex/rgba。`OpbStyle` 常量已覆盖全部令牌。
- **圆角阶**:6(控件)/ 8(卡片表格)/ 10(对话框)/ 999(胶囊)。常量 `CARD_RADIUS=12` **须改为 8**(越界修复);`NAV_RADIUS=8` **须改为 6**。
- **字号阶**:11/12/13/13.5/15。禁止 16px(现 `BuildPanel` 瓦片值)。
- **间距**:4 的倍数,默认 8。
- **导航选中态(规范 S1)**:中性 `-sk-bg-selected` 填充 + **左 3px `-sk-accent` border** + 文字升至 `-sk-text`。**禁止蓝填充**(`-sk-accent-soft` 不用于 nav 选中)。
- **测试**:`OpbStyleTest` 现有断言锁定旧 nav 实现,`navItemSelectedUsesAccent` 须更新为 `BG_SELECTED` + `ACCENT`(border)。其余测试方法签名保留。每步 `mvn -pl SwissKitJ-Plugin-OfflinePython test`。
- **提交**:每个 Task 末尾 commit,前缀 `feat(OfflinePython):` 或 `style(OfflinePython):`。
- **不动**:`OfflinePythonPlugin.java`(入口)、`command/`、`domain/`、`infra/`、`task/`、`PythonInstallGuide.java`(仅随 `OpbStyle` 改动被动受益)。

---

## File Structure

### 修改的文件
| 文件 | 职责变化 |
|---|---|
| `ui/OpbStyle.java` | 修正 nav 选中态;圆角常量(12→8,8→6);新增 topBar/drawer/pill/seg/statTile 方法;清理 glass 注释 |
| `ui/CommandShell.java` | 重写为 4 区布局;nav 4 项;持有 LogDrawer/ProjectSwitcher;删除 V2/V3 LABELS/GROUPS |
| `ui/panel/CommandPanel.java` | 改用 PanelHeader;清理内联 |
| `ui/panel/InitPanel.java` | 改名为 ProjectPanel(增强空状态/路径卡片) |
| `ui/panel/DepsPanel.java` | 改名 ConfigPanel;图标色令牌化(删 WHITE);复用 PanelHeader/StatTile |
| `ui/panel/BuildPanel.java` | 与 VerifyPanel 合并为 BuildVerifyPanel;复用 StatTile;瓦片字号 15 |
| `ui/panel/VerifyPanel.java` | 合并入 BuildVerifyPanel(删除) |
| `ui/panel/DoctorPanel.java` | 复用 PanelHeader;内联清理 |
| `ui/control/PlatformMultiSelect.java` | 图标色令牌化(删 TEXT_PRIMARY 字面量) |
| `ui/dialog/PyPISearchDialog.java` | 圆角/字号合规清理 |
| `i18n/messages_zh.properties` `messages.properties` | 新增 nav/project/log key |

### 新增的文件
| 文件 | 职责 |
|---|---|
| `ui/control/PanelHeader.java` | 统一面板头(标题 + 右侧操作区) |
| `ui/control/StatTile.java` | 结果瓦片(label + value,15px) |
| `ui/control/StatusBadge.java` | 状态徽章(Status→PASS/WARN/FAIL) |
| `ui/control/EmptyState.java` | 空状态(big icon + 文案 + 操作) |
| `ui/control/LogDrawer.java` | 右侧日志抽屉(折叠/级别筛选/LogConsole) |
| `ui/control/LogLevelPill.java` | 日志级别筛选 pill |
| `ui/control/ProjectSwitcher.java` | 顶栏项目切换器(MenuButton) |

### 删除的文件
| 文件 | 原因 |
|---|---|
| `ui/panel/VerifyPanel.java` | 合并入 BuildVerifyPanel |

---

## Task 1: OpbStyle 规范整改(令牌/圆角/字号/nav 选中态)

**Files:**
- Modify: `SwissKitJ-Plugin-OfflinePython/src/main/java/plugin/swisskit/offlinepython/ui/OpbStyle.java`
- Test: `SwissKitJ-Plugin-OfflinePython/src/test/java/plugin/swisskit/offlinepython/OpbStyleTest.java`

**Interfaces:**
- Produces: `OpbStyle.CARD_RADIUS`(8)、`NAV_RADIUS`(6)、`OpbStyle.navItem(selected,hover)`(新实现:选中=`-sk-bg-selected`+左3px accent border+`-sk-text`)。新增方法签名见各步骤代码。

- [ ] **Step 1: 更新 OpbStyleTest 的 nav 选中态断言**

修改 `OpbStyleTest.java` 的 `navItemSelectedUsesAccent` 和 `navItemIdleIsTransparentAndHoverUsesGlassHover`:

```java
    @Test
    void navItemSelectedUsesNeutralFillAndLeftAccentStrip() {
        String sel = OpbStyle.navItem(true, false);
        // 规范 S1:选中态 = 中性 -sk-bg-selected 填充 + 左 3px -sk-accent border + -sk-text 文字
        assertTrue(sel.contains(OpbStyle.BG_SELECTED), "选中态背景须为中性 -sk-bg-selected");
        assertTrue(sel.contains(OpbStyle.ACCENT), "须含 -sk-accent(左侧条)");
        assertTrue(sel.contains(OpbStyle.TEXT_PRIMARY), "选中文字须升至 -sk-text");
        assertTrue(sel.contains("-fx-border-width"), "须有 border-width 实现左侧条");
        assertFalse(sel.contains(OpbStyle.ACCENT_SOFT), "禁止蓝填充(规范反模式)");
    }
```

并把 `cardStyleUsesGlassTokensAndRadius` 的半径断言改为 `8`(因 CARD_RADIUS 12→8)。

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -pl SwissKitJ-Plugin-OfflinePython test -Dtest=OpbStyleTest`
Expected: FAIL(实现尚未改)

- [ ] **Step 3: 修正 OpbStyle 常量与 navItem**

在 `OpbStyle.java` 中:

```java
    public static final int CARD_RADIUS   = 8;   // 规范 §3.4:卡片/表格 8px(原 12 越界)
    public static final int NAV_RADIUS    = 6;   // 规范 §3.4:控件 6px(原 8)
```

替换 `navItem` 方法:

```java
    /**
     * Nav item style per spec S1: idle = transparent + secondary text;
     * hover = -sk-bg-hover + -sk-text; selected = neutral -sk-bg-selected fill
     * + 3px LEFT -sk-accent border + -sk-text text. Never blue-flood.
     */
    public static String navItem(boolean selected, boolean hover) {
        String bg = selected ? BG_SELECTED : (hover ? GLASS_BG_HOVER : "transparent");
        String fg = selected ? TEXT_PRIMARY : (hover ? TEXT_PRIMARY : TEXT_SECONDARY);
        String border = selected
                ? "-fx-border-color: transparent transparent transparent " + ACCENT + ";"
                  + " -fx-border-width: 3 0 0 0;"
                : "-fx-border-color: transparent; -fx-border-width: 0;";
        return "-fx-background-color: " + bg + ";"
             + " -fx-text-fill: " + fg + ";"
             + " " + border
             + " -fx-background-radius: " + NAV_RADIUS + ";"
             + " -fx-cursor: hand;";
    }
```

- [ ] **Step 4: 修正 countBadge 圆角(9→6)与瓦片字号相关方法**

`countBadge()` 圆角 `9` → `6`:

```java
    public static String countBadge() {
        return "-fx-background-color: " + ACCENT + "; -fx-text-fill: white;"
             + " -fx-background-radius: 6; -fx-padding: 0 6 0 6; -fx-font-size: 10px;";
    }
```

- [ ] **Step 5: 运行测试确认通过**

Run: `mvn -pl SwissKitJ-Plugin-OfflinePython test -Dtest=OpbStyleTest`
Expected: PASS(全部)

- [ ] **Step 6: 提交**

```bash
git add SwissKitJ-Plugin-OfflinePython/src/main/java/plugin/swisskit/offlinepython/ui/OpbStyle.java \
        SwissKitJ-Plugin-OfflinePython/src/test/java/plugin/swisskit/offlinepython/OpbStyleTest.java
git commit -m "style(OfflinePython): OpbStyle nav neutral-fill selection, fix radius/scale (spec S1)"
```

---

## Task 2: OpbStyle 新增布局令牌方法(顶栏/抽屉/分段/瓦片)

**Files:**
- Modify: `ui/OpbStyle.java`
- Test: `OpbStyleTest.java`(新增方法断言)

**Interfaces:**
- Produces:`OpbStyle.topBar()`、`projectSwitcher()`、`navItemIconColor(boolean active)`、`logDrawerStyle()`、`logDrawerCollapsedStyle()`、`logPillStyle(boolean on)`、`statTile()`、`segStyle(boolean selected)`、`LOG_DRAWER_WIDTH`、`LOG_DRAWER_COLLAPSED_WIDTH`。

- [ ] **Step 1: 在 OpbStyle 新增常量与方法**

在 `OpbStyle.java` 常量区末尾(`SIDEBAR_WIDTH` 后)新增:

```java
    public static final int SIDEBAR_WIDTH      = 200;   // 原 220,4 倍数
    public static final int LOG_DRAWER_WIDTH   = 240;
    public static final int LOG_DRAWER_COLLAPSED_WIDTH = 40;
```

> 注:`SIDEBAR_WIDTH` 由 220 改 200(仍是 4 倍数,且 nav 仅 4 项更紧凑)。若担心影响 `CommandShellTest`,此 task 仅改常量,引用点在 Task 5。

在类末尾新增方法(`tableHeaderStyle()` 之后):

```java
    /** TopBar container: base bg + bottom 1px border + 8/16 padding(网格). */
    public static String topBar() {
        return "-fx-background-color: " + GLASS_BG_SOFT + ";"
             + "-fx-border-color: transparent transparent " + GLASS_BORDER + " transparent;"
             + "-fx-border-width: 0 0 1 0;";
    }

    /** Project switcher (MenuButton) control style. */
    public static String projectSwitcher() {
        return "-fx-background-color: " + GLASS_BG_HOVER + ";"
             + "-fx-text-fill: " + TEXT_PRIMARY + ";"
             + "-fx-border-color: " + GLASS_BORDER + ";"
             + "-fx-background-radius: " + NAV_RADIUS + "; -fx-border-radius: " + NAV_RADIUS + ";"
             + "-fx-cursor: hand;";
    }

    /** Nav item icon fill color: secondary when idle, primary when active. */
    public static String navItemIconColor(boolean active) {
        return active ? TEXT_PRIMARY : TEXT_SECONDARY;
    }

    /** LogDrawer container (expanded): base bg + left 1px border. */
    public static String logDrawerStyle() {
        return "-fx-background-color: " + GLASS_BG_SOFT + ";"
             + "-fx-border-color: transparent transparent transparent " + GLASS_BORDER + ";"
             + "-fx-border-width: 0 0 0 1;";
    }

    /** LogDrawer collapsed state: narrower, no content. */
    public static String logDrawerCollapsedStyle() {
        return logDrawerStyle();
    }

    /** Log level filter pill: accent-soft when on, hover-tier when off. */
    public static String logPillStyle(boolean on) {
        return "-fx-background-color: " + (on ? ACCENT_SOFT : GLASS_BG_HOVER) + ";"
             + "-fx-text-fill: " + (on ? ACCENT : TEXT_SECONDARY) + ";"
             + "-fx-background-radius: " + NAV_RADIUS + "; -fx-cursor: hand;";
    }

    /** Stat tile surface (equivalent to card; semantic alias). */
    public static String statTile() {
        return "-fx-background-color: " + GLASS_BG_SOFT + ";"
             + "-fx-border-color: " + GLASS_BORDER + ";"
             + "-fx-background-radius: " + CARD_RADIUS + "; -fx-border-radius: " + CARD_RADIUS + ";"
             + "-fx-alignment: center;";
    }

    /** Segmented control item style. */
    public static String segStyle(boolean selected) {
        return "-fx-background-color: " + (selected ? BG_SELECTED : "transparent") + ";"
             + "-fx-text-fill: " + (selected ? TEXT_PRIMARY : TEXT_SECONDARY) + ";"
             + "-fx-background-radius: " + NAV_RADIUS + "; -fx-cursor: hand;";
    }

    /** Section sub-title (e.g. 构建/校验 within merged page). */
    public static String subSectionTitle() {
        return "-fx-text-fill: " + TEXT_SECONDARY + "; -fx-font-size: 11px; -fx-font-weight: bold;"
             + " -fx-label-padding: 0 0 8 0;";
    }
```

- [ ] **Step 2: 为新方法加测试断言**

在 `OpbStyleTest.java` 末尾新增:

```java
    @Test
    void newLayoutHelpersUseTokensNotHex() {
        assertTrue(OpbStyle.topBar().contains(OpbStyle.GLASS_BORDER));
        assertTrue(OpbStyle.projectSwitcher().contains(OpbStyle.GLASS_BG_HOVER));
        assertTrue(OpbStyle.logPillStyle(true).contains(OpbStyle.ACCENT_SOFT));
        assertTrue(OpbStyle.logPillStyle(false).contains(OpbStyle.GLASS_BG_HOVER));
        assertTrue(OpbStyle.segStyle(true).contains(OpbStyle.BG_SELECTED));
        assertTrue(OpbStyle.statTile().contains(OpbStyle.GLASS_BORDER));
    }

    @Test
    void drawerWidthsAreGridAligned() {
        assertEquals(240, OpbStyle.LOG_DRAWER_WIDTH);
        assertEquals(40, OpbStyle.LOG_DRAWER_COLLAPSED_WIDTH);
    }
```

- [ ] **Step 3: 运行测试**

Run: `mvn -pl SwissKitJ-Plugin-OfflinePython test -Dtest=OpbStyleTest`
Expected: PASS

- [ ] **Step 4: 提交**

```bash
git add SwissKitJ-Plugin-OfflinePython/src/main/java/plugin/swisskit/offlinepython/ui/OpbStyle.java \
        SwissKitJ-Plugin-OfflinePython/src/test/java/plugin/swisskit/offlinepython/OpbStyleTest.java
git commit -m "feat(OfflinePython): OpbStyle layout tokens (topBar/drawer/seg/statTile)"
```

---

## Task 3: 可复用控件组件(control 包)

本 Task 新增 5 个无逻辑/低逻辑的展示型控件,各自独立可测。

### Task 3a: PanelHeader

**Files:** Create `ui/control/PanelHeader.java`

- [ ] **Step 1: 创建 PanelHeader**

```java
package plugin.swisskit.offlinepython.ui.control;

import fan.summer.api.component.UiUtils;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.Priority;
import plugin.swisskit.offlinepython.ui.OpbStyle;

/**
 * 统一面板头:左侧标题(sk-t1, 15px)+ 右侧操作区(HBox,可空)。
 * 替换各面板手写的 titleNode()。
 */
public class PanelHeader extends HBox {
    private final HBox actions = new HBox(8);

    public PanelHeader(String title) {
        super(12);
        var t = UiUtils.sectionTitle(title);   // 15px, sk-t1
        getChildren().add(t);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        getChildren().addAll(spacer, actions);
    }

    /** 注入右侧操作按钮(可多次调用追加)。 */
    public void addActions(javafx.scene.Node... nodes) {
        actions.getChildren().addAll(nodes);
    }
}
```

### Task 3b: StatTile

**Files:** Create `ui/control/StatTile.java`

- [ ] **Step 1: 创建 StatTile**

```java
package plugin.swisskit.offlinepython.ui.control;

import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import plugin.swisskit.offlinepython.ui.OpbStyle;

/**
 * 结果统计瓦片:小标题(sk-t3, 11px)+ 大值(sk-t1, 15px,规范上限)。
 * 替换 BuildPanel 手写 VBox(原 16px 越界)。
 */
public class StatTile extends VBox {
    public StatTile(String label, String value) {
        super(2);
        Label l = new Label(label);
        l.setStyle("-fx-text-fill: " + OpbStyle.TEXT_TERTIARY + "; -fx-font-size: 11px;");
        Label v = new Label(value);
        v.setStyle("-fx-text-fill: " + OpbStyle.TEXT_PRIMARY + "; -fx-font-size: 15px; -fx-font-weight: 600;");
        getChildren().addAll(l, v);
        setStyle(OpbStyle.statTile() + " -fx-padding: 10;");
    }

    public void setValue(String value) {
        ((Label) getChildren().get(1)).setText(value);
    }
}
```

### Task 3c: StatusBadge

**Files:** Create `ui/control/StatusBadge.java`

- [ ] **Step 1: 创建 StatusBadge**

```java
package plugin.swisskit.offlinepython.ui.control;

import javafx.scene.control.Label;
import plugin.swisskit.offlinepython.domain.Status;
import plugin.swisskit.offlinepython.ui.OpbStyle;

/**
 * 状态徽章:[PASS]/[WARN]/[FAIL],颜色走 soft+实色令牌对。
 */
public class StatusBadge extends Label {
    public StatusBadge(Status s) {
        super("[" + s + "]");
        setStyle("-fx-text-fill: " + OpbStyle.statusColor(s)
                + "; -fx-background-color: " + soft(s) + ";"
                + " -fx-background-radius: 6; -fx-padding: 1 8 1 8;"
                + " -fx-font-weight: bold; -fx-font-size: 10px;");
    }

    private static String soft(Status s) {
        return switch (s) {
            case PASS -> OpbStyle.SUCCESS_SOFT;
            case WARN -> OpbStyle.WARNING_SOFT;
            case FAIL -> OpbStyle.DANGER_SOFT;
        };
    }
}
```

### Task 3d: EmptyState

**Files:** Create `ui/control/EmptyState.java`

- [ ] **Step 1: 创建 EmptyState**

```java
package plugin.swisskit.offlinepython.ui.control;

import fan.summer.api.MdiIconUtil;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import plugin.swisskit.offlinepython.ui.OpbStyle;

/**
 * 空状态:大图标(sk-t3)+ 文案 + 可选操作按钮行。
 */
public class EmptyState extends VBox {
    public EmptyState(String iconName, String message) {
        super(10);
        setAlignment(javafx.geometry.Pos.CENTER);
        var icon = MdiIconUtil.createIcon(iconName, 40, "-fx-fill: " + OpbStyle.TEXT_TERTIARY + ";");
        Label msg = new Label(message);
        msg.setStyle("-fx-text-fill: " + OpbStyle.TEXT_TERTIARY + "; -fx-font-size: 13px;");
        getChildren().addAll(icon, msg);
    }

    /** 追加操作按钮行(通常一个 HBox of buttons)。 */
    public void setActions(javafx.scene.Node actions) {
        getChildren().add(actions);
    }
}
```

### Task 3e: LogLevelPill

**Files:** Create `ui/control/LogLevelPill.java`

- [ ] **Step 1: 创建 LogLevelPill**

```java
package plugin.swisskit.offlinepython.ui.control;

import javafx.scene.control.Label;
import plugin.swisskit.offlinepython.ui.LogLevel;
import plugin.swisskit.offlinepython.ui.OpbStyle;

/**
 * 日志级别筛选 pill:点击 toggle on/off,选中态 accent-soft。
 */
public class LogLevelPill extends Label {
    private boolean on;
    private final LogLevel level;

    public LogLevelPill(LogLevel level, boolean initialOn) {
        super(level.name());
        this.level = level;
        this.on = initialOn;
        applyStyle();
        setOnMouseClicked(e -> toggle());
    }

    public boolean isOn() { return on; }
    public LogLevel level() { return level; }

    public void toggle() {
        on = !on;
        applyStyle();
    }

    private void applyStyle() {
        setStyle(OpbStyle.logPillStyle(on) + " -fx-padding: 2 9 2 9;");
    }
}
```

- [ ] **Step 2: 编译全部新控件**

Run: `mvn -pl SwissKitJ-Plugin-OfflinePython test-compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add SwissKitJ-Plugin-OfflinePython/src/main/java/plugin/swisskit/offlinepython/ui/control/
git commit -m "feat(OfflinePython): reusable controls (PanelHeader/StatTile/StatusBadge/EmptyState/LogLevelPill)"
```

---

## Task 4: ProjectSwitcher + LogDrawer 控件

### Task 4a: ProjectSwitcher

**Files:** Create `ui/control/ProjectSwitcher.java`

**Interfaces:**
- Produces: `ProjectSwitcher(Runnable onNew, Runnable onOpen)`,`updateName(String)`。

- [ ] **Step 1: 创建 ProjectSwitcher**

```java
package plugin.swisskit.offlinepython.ui.control;

import fan.summer.api.MdiIconUtil;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import plugin.swisskit.offlinepython.ui.OpbStyle;

/**
 * 顶栏项目切换器:MenuButton,菜单含「新建…」「打开…」。
 * 显示当前项目名 + ▾。未打开时显示占位。
 */
public class ProjectSwitcher extends MenuButton {
    private final MenuItem newItem = new MenuItem("＋ 新建项目…");
    private final MenuItem openItem = new MenuItem("📂 打开项目…");

    public ProjectSwitcher(Runnable onNew, Runnable onOpen) {
        super("(未打开项目)");
        newItem.setOnAction(e -> onNew.run());
        openItem.setOnAction(e -> onOpen.run());
        getItems().addAll(newItem, openItem);
        setStyle(OpbStyle.projectSwitcher());
        setGraphic(MdiIconUtil.createIcon("folder", 14, "-fx-fill: " + OpbStyle.TEXT_PRIMARY + ";"));
        // MenuButton 默认 graphic/text 同显;graphic 放左,text 是项目名
        setMnemonicParsing(false);
    }

    public void updateName(String name) {
        setText(name + " ▾");
    }
}
```

### Task 4b: LogDrawer

**Files:** Create `ui/control/LogDrawer.java`

**Interfaces:**
- Consumes: `LogConsole`(现有,Task 5 保留)、`LogLevelPill`
- Produces: `LogDrawer(LogConsole)`,`toggleCollapse()`、`visibleLevels()`(EnumSet)。

- [ ] **Step 1: 创建 LogDrawer**

```java
package plugin.swisskit.offlinepython.ui.control;

import fan.summer.api.MdiIconUtil;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import plugin.swisskit.offlinepython.ui.LogConsole;
import plugin.swisskit.offlinepython.ui.LogLevel;
import plugin.swisskit.offlinepython.ui.OpbStyle;

import java.util.EnumSet;
import java.util.Set;

/**
 * 右侧日志抽屉:标题栏(折叠按钮)+ 级别 pill 行 + LogConsole。
 * 折叠时收缩为窄图标条。
 */
public class LogDrawer extends BorderPane {
    private static final Set<LogLevel> DEFAULT_VISIBLE = EnumSet.allOf(LogLevel.class);
    private final LogConsole console;
    private final HBox pillRow = new HBox(4);
    private boolean collapsed = false;

    public LogDrawer(LogConsole console) {
        this.console = console;
        setStyle(collapsed ? OpbStyle.logDrawerCollapsedStyle() : OpbStyle.logDrawerStyle());
        setPrefWidth(OpbStyle.LOG_DRAWER_WIDTH);
        setMinWidth(Region.USE_PREF_SIZE);
        buildHead();
        buildPills();
        setCenter(console);
        console.setVisibleLevels(DEFAULT_VISIBLE);
    }

    private void buildHead() {
        HBox head = new HBox(8);
        head.setAlignment(Pos.CENTER_LEFT);
        head.setStyle("-fx-padding: 8 12 8 12; -fx-border-color: transparent transparent "
                + OpbStyle.GLASS_BORDER + " transparent; -fx-border-width: 0 0 1 0;");
        Label title = new Label("日志");
        title.setStyle("-fx-text-fill: " + OpbStyle.TEXT_PRIMARY + "; -fx-font-size: 12px;");
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        Label caret = new Label("◂");
        caret.setStyle("-fx-text-fill: " + OpbStyle.TEXT_SECONDARY + "; -fx-cursor: hand;");
        caret.setOnMouseClicked(e -> toggleCollapse());
        head.getChildren().addAll(title, sp, caret);
        setTop(head);
    }

    private void buildPills() {
        pillRow.setStyle("-fx-padding: 8 12 8 12; -fx-border-color: transparent transparent "
                + OpbStyle.GLASS_BORDER + " transparent; -fx-border-width: 0 0 1 0;");
        // 插在 head 与 console 之间:用 BorderPane 不便,改用 VBox 包裹 pillRow+console
        VBox center = new VBox();
        for (LogLevel lv : new LogLevel[]{LogLevel.DEBUG, LogLevel.INFO, LogLevel.WARN, LogLevel.ERROR}) {
            pillRow.getChildren().add(new LogLevelPill(lv, true));
        }
        VBox.setVgrow(console, Priority.ALWAYS);
        center.getChildren().addAll(pillRow, console);
        setCenter(center);
    }

    /** 切换折叠/展开。 */
    public void toggleCollapse() {
        collapsed = !collapsed;
        setPrefWidth(collapsed ? OpbStyle.LOG_DRAWER_COLLAPSED_WIDTH : OpbStyle.LOG_DRAWER_WIDTH);
        pillRow.setManaged(!collapsed); pillRow.setVisible(!collapsed);
        console.setManaged(!collapsed); console.setVisible(!collapsed);
    }

    /** 重新计算可见级别(从 pill 状态)。 */
    public void refreshVisibleLevels() {
        EnumSet<LogLevel> visible = EnumSet.noneOf(LogLevel.class);
        for (var n : pillRow.getChildren()) {
            if (n instanceof LogLevelPill p && p.isOn()) visible.add(p.level());
        }
        console.setVisibleLevels(visible);
    }

    public boolean isCollapsed() { return collapsed; }
}
```

> 注:`LogLevelPill.toggle()` 当前只改样式;需在 pill 创建后追加 listener 通知 drawer 刷新。在 `buildPills()` 的 for 循环内,为每个 pill 加 `p.setOnMouseClicked(e -> { p.toggle(); refreshVisibleLevels(); });` 并移除 LogLevelPill 构造里的自绑定 mouseClicked(改为只提供 toggle,由 drawer 接管点击)。修正 LogLevelPill:删构造里 `setOnMouseClicked`,保留 `toggle()` public。

- [ ] **Step 2: 修正 LogLevelPill(去掉自绑定点击,交 drawer 接管)**

修改 `LogLevelPill.java`,删除构造方法中的 `setOnMouseClicked(e -> toggle());` 一行。`toggle()` 保持 public。

- [ ] **Step 3: 更新 LogDrawer buildPills 接管点击**

把 Task 4b Step 1 的 `buildPills()` for 循环改为:

```java
        for (LogLevel lv : new LogLevel[]{LogLevel.DEBUG, LogLevel.INFO, LogLevel.WARN, LogLevel.ERROR}) {
            LogLevelPill p = new LogLevelPill(lv, true);
            p.setOnMouseClicked(e -> { p.toggle(); refreshVisibleLevels(); });
            pillRow.getChildren().add(p);
        }
```

- [ ] **Step 4: 编译**

Run: `mvn -pl SwissKitJ-Plugin-OfflinePython test-compile`
Expected: BUILD SUCCESS

- [ ] **Step 5: 提交**

```bash
git add SwissKitJ-Plugin-OfflinePython/src/main/java/plugin/swisskit/offlinepython/ui/control/
git commit -m "feat(OfflinePython): ProjectSwitcher + LogDrawer controls"
```

---

## Task 5: CommandShell 4 区布局 + nav 4 项

**Files:**
- Modify: `ui/CommandShell.java`
- Modify: `i18n/messages_zh.properties`, `i18n/messages.properties`

**Interfaces:**
- Consumes: `ProjectSwitcher`、`LogDrawer`、`ProjectContext`、4 个面板
- Produces: `CommandShell()` 构造稳定,`getView()`/`hasRunningTasks()`/`onBackground()`/`onForeground()`/`onUnload()`/`refreshPython()` 签名不变(供 `OfflinePythonPlugin` 调用)。

- [ ] **Step 1: 新增 i18n key**

`messages_zh.properties` 追加:

```
opb.nav.project=项目
opb.nav.config=配置
opb.nav.build=构建校验
opb.nav.doctor=工具
opb.project.empty=未打开项目
opb.project.new=＋ 新建项目
opb.project.open=📂 打开项目
opb.log.title=日志
```

`messages.properties` 追加:

```
opb.nav.project=Project
opb.nav.config=Config
opb.nav.build=Build & Verify
opb.nav.doctor=Tools
opb.project.empty=No project open
opb.project.new=New Project
opb.project.open=Open Project
opb.log.title=Logs
```

- [ ] **Step 2: 重写 CommandShell —— 字段与构造**

完整替换 `CommandShell.java`。字段区:

```java
package plugin.swisskit.offlinepython.ui;

import fan.summer.api.MdiIconUtil;
import fan.summer.api.component.GlassNotification;
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
import plugin.swisskit.offlinepython.ui.control.LogDrawer;
import plugin.swisskit.offlinepython.ui.control.ProjectSwitcher;
import plugin.swisskit.offlinepython.ui.panel.BuildVerifyPanel;
import plugin.swisskit.offlinepython.ui.panel.ConfigPanel;
import plugin.swisskit.offlinepython.ui.panel.DoctorPanel;
import plugin.swisskit.offlinepython.ui.panel.ProjectPanel;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

public class CommandShell {
    private final BorderPane root = new BorderPane();
    private final VBox contentWrap = new VBox();
    private final LogConsole logConsole = new LogConsole();
    private final LogDrawer logDrawer = new LogDrawer(logConsole);
    private final Label pyBadge = new Label();
    private final ProjectContext project = new ProjectContext();
    private final ProjectSwitcher switcher = new ProjectSwitcher(this::createNew, this::openExisting);
    private final Map<String, Button> navButtons = new LinkedHashMap<>();
    private final Map<String, String> navLabels = new LinkedHashMap<>();
    private String current = "project";
    private BuildVerifyPanel buildVerifyPanel;
```

> 注:nav key 用 `project/config/build/doctor` 四个。`navLabels` 用 i18n 填充。

- [ ] **Step 3: 重写构造方法**

```java
    public CommandShell() {
        root.getStylesheets().add(Themes.commonStylesheetUrl());
        root.setStyle("-fx-background-color: transparent;");
        navLabels.put("project", I18n.get("opb.nav.project"));
        navLabels.put("config",   I18n.get("opb.nav.config"));
        navLabels.put("build",    I18n.get("opb.nav.build"));
        navLabels.put("doctor",   I18n.get("opb.nav.doctor"));

        root.setTop(buildTopBar());
        root.setLeft(buildNav());
        contentWrap.setStyle("-fx-background-color: transparent;");
        root.setCenter(contentWrap);
        root.setRight(logDrawer);
        root.addEventHandler(NavEvent.NAV, e -> select(e.target()));
        refreshPython();
        select("project");
    }
```

- [ ] **Step 4: 重写 buildTopBar**

```java
    private Node buildTopBar() {
        HBox bar = new HBox(12);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new javafx.geometry.Insets(8, 16, 8, 16));
        bar.setStyle(OpbStyle.topBar());
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        pyBadge.setStyle(OpbStyle.badge(true));
        pyBadge.setOnMouseClicked(e -> select("doctor"));
        pyBadge.setStyle("-fx-cursor: hand;" + OpbStyle.badge(true));
        bar.getChildren().addAll(switcher, spacer, pyBadge);
        return bar;
    }
```

- [ ] **Step 5: 重写 buildNav(4 项 + 图标 + 左条 active)**

```java
    private Node buildNav() {
        VBox nav = new VBox(4);
        nav.setPrefWidth(OpbStyle.SIDEBAR_WIDTH);
        nav.setMinWidth(Region.USE_PREF_SIZE);
        nav.setPadding(new javafx.geometry.Insets(8));
        nav.setStyle("-fx-background-color: " + OpbStyle.GLASS_BG_SOFT + ";"
                + " -fx-border-color: transparent " + OpbStyle.GLASS_BORDER + " transparent transparent;"
                + " -fx-border-width: 0 1 0 0;");
        navEntry(nav, "project", "folder-outline");
        navEntry(nav, "config",  "package-variant-closed");
        navEntry(nav, "build",   "hammer-wrench");
        navEntry(nav, "doctor",  "stethoscope");
        return nav;
    }

    private void navEntry(VBox nav, String key, String icon) {
        Button b = new Button(navLabels.get(key));
        b.setMaxWidth(Double.MAX_VALUE);
        b.setAlignment(Pos.CENTER_LEFT);
        b.setMnemonicParsing(false);
        b.setGraphicTextGap(10);
        applyNavStyle(b, key.equals(current), false, key.equals(current));
        b.setOnMouseEntered(e -> { if (!key.equals(current)) applyNavStyle(b, false, true, false); });
        b.setOnMouseExited(e ->  { if (!key.equals(current)) applyNavStyle(b, false, false, false); });
        b.setOnAction(e -> select(key));
        if (key.equals("config")) {
            Label badge = new Label("0");
            badge.setStyle(OpbStyle.countBadge());
            b.setGraphic(badge);  // 覆盖:config 项右端显示依赖数,图标留给 graphic? —— 见下方说明
        }
        navButtons.put(key, b);
        nav.getChildren().add(b);
    }

    /**
     * nav item 样式:graphic 为左侧图标(色随 active),文本居左。
     * config 项额外用右侧 badge —— 用 HBox 包裹。此处简化:config 项 graphic 仍为图标,
     * badge 文本拼到 label 末尾。
     */
    private void applyNavStyle(Button b, boolean selected, boolean hover, boolean active) {
        b.setStyle(OpbStyle.navItem(selected, hover) + " -fx-padding: 8 12 8 9;");
        // 图标
        var iconKey = switch (b.getText() == null ? "" : b.getText()) {
            default -> "folder-outline";
        };
    }
```

> **重要简化(避免 graphic/badge 冲突):** 由于 `Button.setGraphic` 只能放一个节点,config 项要同时显示图标 + 右侧 badge 较繁琐。采用更稳健方案:把 nav item 改成 `HBox`(图标 + 标签 + spacer + badge),放入 `Button.graphic`,Button 文本置空。下面 Step 6 给出最终实现。

- [ ] **Step 6: nav item 最终实现(用 HBox 作 graphic)**

替换 `navEntry` 与 `applyNavStyle` 为:

```java
    private void navEntry(VBox nav, String key, String icon, boolean hasBadge) {
        Button b = new Button();
        b.setMaxWidth(Double.MAX_VALUE);
        b.setMnemonicParsing(false);
        HBox content = new HBox(10);
        content.setAlignment(Pos.CENTER_LEFT);
        var iconNode = MdiIconUtil.createIcon(icon, 14, "-fx-fill: " + OpbStyle.navItemIconColor(key.equals(current)) + ";");
        iconNode.setUserData("icon");  // 标记,便于刷新图标色
        Label text = new Label(navLabels.get(key));
        text.setUserData("text");
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        content.getChildren().addAll(iconNode, text, spacer);
        if (hasBadge) {
            Label badge = new Label("0");
            badge.setStyle(OpbStyle.countBadge());
            badge.setUserData("badge");
            badge.textProperty().bind(javafx.beans.binding.Bindings.createStringBinding(
                () -> String.valueOf(countDeps()), project.projectDirProperty()));
        }
        b.setGraphic(content);
        applyNavStyle(b, key.equals(current), false);
        b.setOnMouseEntered(e -> { if (!key.equals(current)) applyNavStyle(b, false, true); });
        b.setOnMouseExited(e ->  { if (!key.equals(current)) applyNavStyle(b, false, false); });
        b.setOnAction(e -> select(key));
        navButtons.put(key, b);
        nav.getChildren().add(b);
    }

    private void applyNavStyle(Button b, boolean selected, boolean hover) {
        b.setStyle(OpbStyle.navItem(selected, hover) + " -fx-padding: 8 12 8 9;");
        // 刷新图标色(选中/悬停态切换)
        if (b.getGraphic() instanceof HBox h && !h.getChildren().isEmpty()
                && h.getChildren().get(0) instanceof javafx.scene.text.Text t) {
            t.setStyle("-fx-fill: " + OpbStyle.navItemIconColor(selected || hover) + ";");
        }
    }
```

并修正 `buildNav` 的调用:

```java
        navEntry(nav, "project", "folder-outline", false);
        navEntry(nav, "config",  "package-variant-closed", true);
        navEntry(nav, "build",   "hammer-wrench", false);
        navEntry(nav, "doctor",  "stethoscope", false);
```

且 config 项的 badge 需真正加入 content。修正 `navEntry` 中 hasBadge 分支:

```java
        if (hasBadge) {
            Label badge = new Label("0");
            badge.setStyle(OpbStyle.countBadge());
            badge.textProperty().bind(javafx.beans.binding.Bindings.createStringBinding(
                () -> String.valueOf(countDeps()), project.projectDirProperty()));
            content.getChildren().add(badge);  // 加到 spacer 之后
        }
```

- [ ] **Step 7: 重写 select(4 面板)与 openExisting/createNew/refreshPython/countDeps**

```java
    private void select(String key) {
        current = key;
        navButtons.forEach((k, b) -> applyNavStyle(b, k.equals(key), false));
        Node panel = switch (key) {
            case "project" -> new ProjectPanel(logConsole, project);
            case "config"  -> new ConfigPanel(logConsole, project);
            case "build"   -> buildVerifyPanel != null ? buildVerifyPanel : (buildVerifyPanel = new BuildVerifyPanel(logConsole, project));
            case "doctor"  -> new DoctorPanel(logConsole, project);
            default -> new Label("—");
        };
        contentWrap.getChildren().setAll(panel);
    }

    private void openExisting() {
        DirectoryChooser dc = new DirectoryChooser();
        File dir = dc.showDialog(root.getScene().getWindow());
        if (dir == null) return;
        project.openExisting(dir.toPath());
        switcher.updateName(dir.getName());
        logConsole.log("已打开项目: " + dir);
    }

    private void createNew() {
        DirectoryChooser dc = new DirectoryChooser();
        File dir = dc.showDialog(root.getScene().getWindow());
        if (dir == null) return;
        try {
            project.createNew(dir.toPath());
            switcher.updateName(dir.getName());
            logConsole.log("已新建项目: " + dir);
            GlassNotification.toast(root, GlassNotification.Type.SUCCESS, "项目已初始化");
        } catch (Exception e) {
            GlassNotification.toast(root, GlassNotification.Type.ERROR, "新建失败");
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
        pyBadge.setStyle("-fx-cursor: hand;" + OpbStyle.badge(ok));
        if (!ok && !"project".equals(current))
            contentWrap.getChildren().setAll(new PythonInstallGuide(this::refreshPython));
    }

    public Node getView() { return root; }
    public boolean hasRunningTasks() { return buildVerifyPanel != null && buildVerifyPanel.isRunning(); }
    public void onBackground() {}
    public void onForeground() { refreshPython(); }
    public void onUnload() { if (buildVerifyPanel != null) buildVerifyPanel.cancel(); }
```

- [ ] **Step 8: 编译(此 Task 尚未建新面板,会报错 —— 正常,留待 Task 6/7/8 补齐)**

此 Task 与 Task 6-8 高度耦合,建议本 Step 仅确认 `CommandShell` 自身语法(除面板类引用)。实际编译留到 Task 8 末尾统一验证。

- [ ] **Step 9: 提交(暂不要求编译通过,与 Task 6-8 一并验证)**

```bash
git add SwissKitJ-Plugin-OfflinePython/src/main/java/plugin/swisskit/offlinepython/ui/CommandShell.java \
        SwissKitJ-Plugin-OfflinePython/src/main/resources/i18n/messages_zh.properties \
        SwissKitJ-Plugin-OfflinePython/src/main/resources/i18n/messages.properties
git commit -m "feat(OfflinePython): CommandShell 4-region layout, nav 4 items, right log drawer"
```

---

## Task 6: ProjectPanel + ConfigPanel(原 Init/Deps 整改)

### Task 6a: ProjectPanel(原 InitPanel 增强)

**Files:**
- Rename/Create `ui/panel/ProjectPanel.java`
- Delete `ui/panel/InitPanel.java`

- [ ] **Step 1: 创建 ProjectPanel**

```java
package plugin.swisskit.offlinepython.ui.panel;

import fan.summer.api.component.GlassNotification;
import fan.summer.api.component.UiUtils;
import fan.summer.api.i18n.I18n;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import plugin.swisskit.offlinepython.ui.LogConsole;
import plugin.swisskit.offlinepython.ui.OpbStyle;
import plugin.swisskit.offlinepython.ui.ProjectContext;
import plugin.swisskit.offlinepython.ui.control.EmptyState;
import plugin.swisskit.offlinepython.ui.control.PanelHeader;

public class ProjectPanel extends CommandPanel {
    public ProjectPanel(LogConsole log, ProjectContext project) {
        super(log, project);
        PanelHeader header = new PanelHeader(I18n.get("opb.init.title"));
        getChildren().add(header);

        var dir = project.getProjectDir();
        if (dir == null) {
            EmptyState empty = new EmptyState("folder-off-outline", I18n.get("opb.project.empty"));
            HBox actions = new HBox(8);
            Button newBtn = UiUtils.glassBtn(I18n.get("opb.project.new"), true);
            Button openBtn = UiUtils.glassBtn(I18n.get("opb.project.open"), false);
            actions.getChildren().addAll(newBtn, openBtn);
            empty.setActions(actions);
            getChildren().add(empty);
        } else {
            HBox card = new HBox(12);
            card.setStyle(OpbStyle.card() + " -fx-padding: 14; -fx-alignment: CENTER_LEFT;");
            Label path = new Label(dir.toString());
            path.setStyle("-fx-text-fill: " + OpbStyle.TEXT_SECONDARY + "; -fx-font-family: monospace; -fx-font-size: 12px;");
            HBox.setHgrow(path, Priority.ALWAYS);
            Button open = UiUtils.glassBtn("打开", false);
            card.getChildren().addAll(path, open);
            getChildren().add(card);

            Label initTitle = UiUtils.subLabel("初始化会生成");
            getChildren().add(initTitle);
            getChildren().add(UiUtils.subLabel("• config.json  • requirements.txt  • README.md"));
        }
    }

    @Override public String title() { return I18n.get("opb.init.title"); }
}
```

> 注:`CommandPanel` 构造已自带 card 样式;PanelHeader 已含标题,不再调 `titleNode()`。下面 Task 8 会调整 `CommandPanel` 基类移除冗余。

### Task 6b: ConfigPanel(原 DepsPanel 整改)

**Files:**
- Rename `ui/panel/DepsPanel.java` → `ui/panel/ConfigPanel.java`
- Modify: 类名、删 `WHITE` 常量、图标色令牌化、用 PanelHeader

- [ ] **Step 1: 重命名类 + 修图标色**

把 `DepsPanel.java` 重命名为 `ConfigPanel.java`,类名 `DepsPanel`→`ConfigPanel`。关键改动:

1. 删除 `private static final String WHITE = OpbStyle.TEXT_PRIMARY;`
2. `perRowPlatformCol()` 与 `MdiIconUtil.createIcon(..., WHITE)` 全部改用 `OpbStyle.TEXT_PRIMARY` 直接传(图标色用令牌)。
3. `buildUi()` 中 `getChildren().add(titleNode());` 改为:

```java
        PanelHeader header = new PanelHeader(I18n.get("opb.deps.title"));
        Button imp = UiUtils.glassBtn("导入 requirements.txt", false);
        imp.setOnAction(e -> doImport());
        Button search = UiUtils.glassBtn("🔍 在线搜索", false);
        search.setOnAction(e -> doSearch());
        Button addBtn = UiUtils.glassBtn("增加配置", true);
        addBtn.setOnAction(e -> doSave());
        header.addActions(imp, search, addBtn);
        getChildren().add(header);
```

并删除原来散落在 row1/row3 的 imp/search/addBtn 按钮创建(row2 表单 + 表格 + opts + summaryBar 保留)。`doImport/doSearch/doSave` 方法体不变。

- [ ] **Step 2: 编译(仍缺 BuildVerifyPanel,Task 7/8 补齐后统一验证)**

暂跳过,统一在 Task 8 验证。

- [ ] **Step 3: 提交**

```bash
git add SwissKitJ-Plugin-OfflinePython/src/main/java/plugin/swisskit/offlinepython/ui/panel/
git rm SwissKitJ-Plugin-OfflinePython/src/main/java/plugin/swisskit/offlinepython/ui/panel/InitPanel.java
git commit -m "feat(OfflinePython): ProjectPanel (enhanced) + ConfigPanel (tokenized icons)"
```

---

## Task 7: BuildVerifyPanel(合并 build+verify)

**Files:**
- Create `ui/panel/BuildVerifyPanel.java`
- Delete `ui/panel/BuildPanel.java`, `ui/panel/VerifyPanel.java`

**Interfaces:**
- Consumes: `StatTile`、`StatusBadge`、`PanelHeader`、`BuildService`、`VerifyService`
- Produces: `BuildVerifyPanel(LogConsole, ProjectContext)`、`isRunning()`、`cancel()`(供 CommandShell)。

- [ ] **Step 1: 创建 BuildVerifyPanel**

```java
package plugin.swisskit.offlinepython.ui.panel;

import fan.summer.api.component.GlassNotification;
import fan.summer.api.component.UiUtils;
import fan.summer.api.i18n.I18n;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import plugin.swisskit.offlinepython.command.BuildService;
import plugin.swisskit.offlinepython.command.VerifyService;
import plugin.swisskit.offlinepython.domain.BuildConfig;
import plugin.swisskit.offlinepython.domain.BuildSummary;
import plugin.swisskit.offlinepython.domain.CheckResult;
import plugin.swisskit.offlinepython.domain.Manifest;
import plugin.swisskit.offlinepython.domain.RequirementsFile;
import plugin.swisskit.offlinepython.domain.Status;
import plugin.swisskit.offlinepython.domain.VerifyResult;
import plugin.swisskit.offlinepython.domain.VerifyScope;
import plugin.swisskit.offlinepython.infra.JsonStore;
import plugin.swisskit.offlinepython.infra.ProcessRunner;
import plugin.swisskit.offlinepython.task.PluginTask;
import plugin.swisskit.offlinepython.ui.LogConsole;
import plugin.swisskit.offlinepython.ui.OpbStyle;
import plugin.swisskit.offlinepython.ui.ProjectContext;
import plugin.swisskit.offlinepython.ui.control.PanelHeader;
import plugin.swisskit.offlinepython.ui.control.StatTile;
import plugin.swisskit.offlinepython.ui.control.StatusBadge;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class BuildVerifyPanel extends CommandPanel {
    private final ProgressBar progress = new ProgressBar();
    private final Button build;
    private final Label banner = new Label();
    private final GridPane tiles = new GridPane();
    private final ToggleGroup scopeGroup = new ToggleGroup();
    private final VBox report = new VBox(6);
    private final Label conclusion = new Label();
    private PluginTask<BuildSummary> task;
    private ProcessRunner runner;

    public BuildVerifyPanel(LogConsole log, ProjectContext project) {
        super(log, project);
        PanelHeader header = new PanelHeader(I18n.get("opb.build.title"));
        getChildren().add(header);

        // ── 构建区 ──
        VBox buildSection = new VBox(10);
        buildSection.setStyle(OpbStyle.card() + " -fx-padding: 14;");
        Label bTitle = new Label("构建");
        bTitle.setStyle(OpbStyle.subSectionTitle());
        buildSection.getChildren().add(bTitle);

        banner.setStyle("-fx-background-color: " + OpbStyle.ACCENT_SOFT + "; -fx-text-fill: " + OpbStyle.ACCENT
                + "; -fx-background-radius: 6; -fx-padding: 8 12 8 12; -fx-font-size: 12px;");
        buildSection.getChildren().add(banner);

        build = UiUtils.glassBtn("▶ 构建", true);
        Button cancel = UiUtils.glassBtn("✕ 取消", false);
        progress.setProgress(-1);
        progress.setPrefHeight(6);
        build.setOnAction(e -> start());
        cancel.setOnAction(e -> { if (runner != null) runner.cancel(); });
        HBox buildRow = new HBox(8, build, cancel, progress);
        HBox.setHgrow(progress, Priority.ALWAYS);
        buildSection.getChildren().add(buildRow);

        tiles.setHgap(8); tiles.setVgap(8);
        buildSection.getChildren().add(tiles);
        getChildren().add(buildSection);

        // ── 校验区 ──
        VBox verifySection = new VBox(10);
        verifySection.setStyle(OpbStyle.card() + " -fx-padding: 14;");
        Label vTitle = new Label("校验");
        vTitle.setStyle(OpbStyle.subSectionTitle());
        verifySection.getChildren().add(vTitle);

        ToggleButton all = seg("全量", VerifyScope.ALL, true);
        ToggleButton integ = seg("仅完整性", VerifyScope.INTEGRITY, false);
        ToggleButton sha = seg("仅 SHA256", VerifyScope.SHA256, false);
        HBox segs = new HBox(0, all, integ, sha);
        Button run = UiUtils.glassBtn("▶ 开始校验", true);
        Region spring = new Region(); HBox.setHgrow(spring, Priority.ALWAYS);
        HBox verifyRow = new HBox(8, segs, spring, run);
        verifySection.getChildren().add(verifyRow);
        report.setStyle("-fx-padding: 4 0 0 0;");
        verifySection.getChildren().addAll(report, conclusion);
        getChildren().add(verifySection);

        run.setOnAction(e -> doVerify());
        refreshBanner();
    }

    private ToggleButton seg(String text, VerifyScope scope, boolean selected) {
        ToggleButton b = new ToggleButton(text);
        b.setToggleGroup(scopeGroup);
        b.setUserData(scope);
        b.setSelected(selected);
        b.setStyle(OpbStyle.segStyle(selected) + " -fx-padding: 5 14;");
        b.selectedProperty().addListener((o, ov, nv) ->
                b.setStyle(OpbStyle.segStyle(nv) + " -fx-padding: 5 14;"));
        return b;
    }

    private VerifyScope selectedScope() {
        Toggle t = scopeGroup.getSelectedToggle();
        return t == null ? VerifyScope.ALL : (VerifyScope) t.getUserData();
    }

    private void refreshBanner() {
        Path dir = project.getProjectDir();
        long depCount = countDeps(dir);
        String plat = project.getConfig() != null && project.getConfig().getPython() != null
                ? project.getConfig().getPython().getPrimaryPlatform() : "?";
        banner.setText("📋 当前依赖:" + depCount + " 个直接  ·  目标 " + plat
                + (project.getConfig() != null && project.getConfig().getPython() != null
                    ? "  ·  Python " + project.getConfig().getPython().getVersion() : ""));
    }

    private long countDeps(Path dir) {
        if (dir == null) return 0;
        try {
            if (Files.exists(dir.resolve("requirements.txt")))
                return RequirementsFile.parse(Files.readString(dir.resolve("requirements.txt")))
                        .stream().filter(d -> !d.name().isBlank()).count();
        } catch (Exception ignored) {}
        return 0;
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
            log.log("构建完成:" + s.totalWheels() + " wheels · " + humanBytes(s.totalBytes())
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
        tiles.add(makeTile("已下载", s.totalWheels() + ""), 0, 0);
        tiles.add(makeTile("耗时", (s.durationMs() / 1000) + "s"), 1, 0);
        tiles.add(makeTile("大小", humanBytes(s.totalBytes())), 2, 0);
        tiles.add(makeTile("缓存命中", s.cacheHits() + ""), 3, 0);
    }

    private StatTile makeTile(String label, String value) {
        StatTile t = new StatTile(label, value);
        GridPane.setHgrow(t, Priority.ALWAYS);
        t.setMaxWidth(Double.MAX_VALUE);
        return t;
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
            GlassNotification.toast(this, GlassNotification.Type.ERROR, "校验失败(未构建?)");
        }
    }

    private void render(VerifyResult r) {
        report.getChildren().clear();
        boolean fail = false; boolean warn = false;
        for (CheckResult c : presentChecks(r)) {
            if (c == null) continue;
            fail |= c.status() == Status.FAIL;
            warn |= c.status() == Status.WARN;
            report.getChildren().add(new HBox(10, new StatusBadge(c.status()), UiUtils.subLabel(c.detail())));
        }
        conclusion.setStyle("-fx-background-radius: 8; -fx-padding: 10 14; -fx-font-weight: 500;");
        if (fail) {
            conclusion.setText("⚠ 仓库存在问题");
            conclusion.setStyle("-fx-background-color: " + OpbStyle.DANGER_SOFT + "; -fx-text-fill: " + OpbStyle.DANGER
                    + "; -fx-background-radius: 8; -fx-padding: 10 14; -fx-font-weight: 500;");
        } else if (warn) {
            conclusion.setText("✓ 仓库可用(含警告)");
            conclusion.setStyle("-fx-background-color: " + OpbStyle.WARNING_SOFT + "; -fx-text-fill: " + OpbStyle.WARNING
                    + "; -fx-background-radius: 8; -fx-padding: 10 14; -fx-font-weight: 500;");
        } else {
            conclusion.setText("✓ Repository OK");
            conclusion.setStyle("-fx-background-color: " + OpbStyle.SUCCESS_SOFT + "; -fx-text-fill: " + OpbStyle.SUCCESS
                    + "; -fx-background-radius: 8; -fx-padding: 10 14; -fx-font-weight: 500;");
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

- [ ] **Step 2: 删除 BuildPanel.java 与 VerifyPanel.java**

```bash
git rm SwissKitJ-Plugin-OfflinePython/src/main/java/plugin/swisskit/offlinepython/ui/panel/BuildPanel.java
git rm SwissKitJ-Plugin-OfflinePython/src/main/java/plugin/swisskit/offlinepython/ui/panel/VerifyPanel.java
```

- [ ] **Step 3: 提交**

```bash
git add SwissKitJ-Plugin-OfflinePython/src/main/java/plugin/swisskit/offlinepython/ui/panel/BuildVerifyPanel.java
git commit -m "feat(OfflinePython): merge build+verify into BuildVerifyPanel (上下分区)"
```

---

## Task 8: CommandPanel 基类 + DoctorPanel 整改 + 整体编译验证

**Files:**
- Modify: `ui/panel/CommandPanel.java`
- Modify: `ui/panel/DoctorPanel.java`
- Modify: `ui/control/PlatformMultiSelect.java`(图标色)

- [ ] **Step 1: 简化 CommandPanel 基类(去掉冗余 titleNode 调用约束)**

`CommandPanel` 保持现有,但各子类已自行用 PanelHeader。基类不变(仍提供 card 样式 + log/project 字段)。无需改动,确认各子类不再调 `titleNode()` 即可(ProjectPanel/ConfigPanel/BuildVerifyPanel 已用 PanelHeader)。DoctorPanel 在 Step 2 改。

- [ ] **Step 2: DoctorPanel 用 PanelHeader + 清理**

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
import plugin.swisskit.offlinepython.ui.control.PanelHeader;

public class DoctorPanel extends CommandPanel {
    private final GridPane grid = new GridPane();

    public DoctorPanel(LogConsole log, ProjectContext project) {
        super(log, project);
        PanelHeader header = new PanelHeader(I18n.get("opb.doctor.title"));
        Button run = UiUtils.glassBtn("▶ 运行诊断", true);
        header.addActions(run);
        getChildren().add(header);

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
        getChildren().add(grid);
    }

    @Override public String title() { return I18n.get("opb.doctor.title"); }
}
```

- [ ] **Step 3: PlatformMultiSelect 图标色令牌化**

在 `PlatformMultiSelect.java` 中,把 `updateButtonDisplay()` 与 `rebuildMenu()` 里所有 `MdiIconUtil.createIcon(..., OpbStyle.TEXT_PRIMARY)` 保持(已是令牌,无需改)。确认无硬编码 WHITE。此文件实际已合规,仅确认。

- [ ] **Step 4: 整体编译验证**

Run: `mvn -pl SwissKitJ-Plugin-OfflinePython clean test-compile`
Expected: BUILD SUCCESS(无编译错误)

- [ ] **Step 5: 运行全部测试**

Run: `mvn -pl SwissKitJ-Plugin-OfflinePython test`
Expected: 全部 PASS。`OpbStyleTest` 应全绿(含 Task 1 更新的断言)。

- [ ] **Step 6: 提交**

```bash
git add SwissKitJ-Plugin-OfflinePython/src/main/java/plugin/swisskit/offlinepython/ui/panel/DoctorPanel.java \
        SwissKitJ-Plugin-OfflinePython/src/main/java/plugin/swisskit/offlinepython/ui/panel/CommandPanel.java
git commit -m "feat(OfflinePython): DoctorPanel PanelHeader + full build green"
```

---

## Task 9: PyPISearchDialog 合规清理 + 最终验证

**Files:**
- Modify: `ui/dialog/PyPISearchDialog.java`

- [ ] **Step 1: PyPISearchDialog 圆角/字号核查**

现有 `PyPISearchDialog` 已用 `.sk-field`/`.sk-table`/`.sk-dialog` class,基本合规。仅确认:无 16px 字号、无硬编码 hex。该文件无需改动,确认即可。

- [ ] **Step 2: 全量回归测试**

Run: `mvn -pl SwissKitJ-Plugin-OfflinePython clean test`
Expected: 全绿

- [ ] **Step 3: grep 验证无硬编码颜色(令牌规范)**

Run:
```bash
grep -rnE '#[0-9A-Fa-f]{6}|rgba\(' SwissKitJ-Plugin-OfflinePython/src/main/java/ \
  | grep -v 'OpbStyle.java' || echo "OK: 无硬编码颜色(除 OpbStyle 令牌常量定义)"
```
Expected: 输出 `OK:` 或仅 `OpbStyle.java` 自身(令牌字符串字面量如 `"-sk-accent"` 不是 hex,不应匹配)。

- [ ] **Step 4: 验收清单逐项确认(对应 spec §9)**

人工/静态核查:
- [ ] 导航 4 项,无 V2/V3 占位;选中项中性填充 + 左 3px accent 条
- [ ] 圆角全在 {6,8,10,999};字号全在 {11,12,13,13.5,15};间距 4 倍数
- [ ] 日志右侧抽屉可折叠;pill 选中态走令牌
- [ ] build+verify 同页上下分区
- [ ] `OpbStyleTest` 全绿;`mvn test` 全绿

- [ ] **Step 5: 最终提交**

```bash
git add -A SwissKitJ-Plugin-OfflinePython/
git commit -m "style(OfflinePython): UI refactor complete (spec-compliant, 4-item nav, log drawer)"
```

---

## Self-Review Notes

**Spec coverage:** §1(违规诊断)→ Task 1;§2(目标)→ 全覆盖;§3(导航 4 项)→ Task 5;§4(布局)→ Task 5/4b;§5(面板)→ Task 6/7/8;§6(OpbStyle)→ Task 1/2;§7(i18n)→ Task 5;§8(风险:nav 左条用 border-width 实现,UiUtils 宿主不改)→ 已在 Task 1/5 体现;§9(验收)→ Task 9。

**类型一致性:** `navItem(boolean,boolean)` 签名保留(测试锁定);`ProjectSwitcher.updateName(String)`、`LogDrawer.toggleCollapse()`、`BuildVerifyPanel.isRunning()/cancel()` 在 CommandShell(Task 5)引用处一致。

**已知简化:** nav item 用 `Button.graphic = HBox` 承载图标+文本+badge,避免 graphic 单节点限制(Task 5 Step 6)。
