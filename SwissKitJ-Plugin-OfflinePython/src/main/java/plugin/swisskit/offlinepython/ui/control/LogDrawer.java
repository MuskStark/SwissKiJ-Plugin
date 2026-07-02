package plugin.swisskit.offlinepython.ui.control;

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

/**
 * 右侧日志抽屉:标题栏(折叠按钮)+ 级别 pill 行 + LogConsole。
 * 折叠时收缩为窄图标条。
 *
 * <p>Pill click wiring is owned by this drawer: each {@link LogLevelPill} exposes
 * {@code toggle()} / {@code isOn()} / {@code level()} without a self-bound handler,
 * so the drawer attaches {@code p.setOnMouseClicked(e -> { p.toggle(); refreshVisibleLevels(); })}
 * to keep the host shell in control of the interaction lifecycle.
 */
public class LogDrawer extends BorderPane {
    private final LogConsole console;
    private final HBox pillRow = new HBox(4);
    private boolean collapsed = false;

    public LogDrawer(LogConsole console) {
        this.console = console;
        setStyle(OpbStyle.logDrawerStyle());
        setPrefWidth(OpbStyle.LOG_DRAWER_WIDTH);
        setMinWidth(Region.USE_PREF_SIZE);
        buildHead();
        buildPills();
        // CENTER holds pill row + console so VGrow can push the console to fill.
        VBox center = new VBox();
        VBox.setVgrow(console, Priority.ALWAYS);
        center.getChildren().addAll(pillRow, console);
        setCenter(center);
        refreshVisibleLevels();
    }

    private void buildHead() {
        HBox head = new HBox(8);
        head.setAlignment(Pos.CENTER_LEFT);
        head.setStyle("-fx-padding: 8 12 8 12; -fx-border-color: transparent transparent "
                + OpbStyle.GLASS_BORDER + " transparent; -fx-border-width: 0 0 1 0;");
        Label title = new Label("日志");
        title.setStyle("-fx-text-fill: " + OpbStyle.TEXT_PRIMARY + "; -fx-font-size: 12px;");
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        Label caret = new Label("◂");
        caret.setStyle("-fx-text-fill: " + OpbStyle.TEXT_SECONDARY + "; -fx-cursor: hand;");
        caret.setOnMouseClicked(e -> toggleCollapse());
        head.getChildren().addAll(title, sp, caret);
        setTop(head);
    }

    private void buildPills() {
        pillRow.setStyle("-fx-padding: 8 12 8 12; -fx-border-color: transparent transparent "
                + OpbStyle.GLASS_BORDER + " transparent; -fx-border-width: 0 0 1 0;");
        for (LogLevel lv : new LogLevel[]{LogLevel.DEBUG, LogLevel.INFO, LogLevel.WARN, LogLevel.ERROR}) {
            LogLevelPill p = new LogLevelPill(lv, true);
            p.setOnMouseClicked(e -> { p.toggle(); refreshVisibleLevels(); });
            pillRow.getChildren().add(p);
        }
    }

    /** 切换折叠/展开。 */
    public void toggleCollapse() {
        collapsed = !collapsed;
        setPrefWidth(collapsed ? OpbStyle.LOG_DRAWER_COLLAPSED_WIDTH : OpbStyle.LOG_DRAWER_WIDTH);
        pillRow.setManaged(!collapsed);
        pillRow.setVisible(!collapsed);
        console.setManaged(!collapsed);
        console.setVisible(!collapsed);
    }

    /** 重新计算可见级别(从 pill 状态)。 */
    public void refreshVisibleLevels() {
        EnumSet<LogLevel> visible = EnumSet.noneOf(LogLevel.class);
        for (var n : pillRow.getChildren()) {
            if (n instanceof LogLevelPill p && p.isOn()) visible.add(p.level());
        }
        console.setVisibleLevels(visible);
    }

    public boolean isCollapsed() {
        return collapsed;
    }
}
