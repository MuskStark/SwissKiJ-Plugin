package plugin.swisskit.offlinepython.ui.panel;

import fan.summer.api.component.GlassNotification;
import fan.summer.api.component.UiUtils;
import fan.summer.api.i18n.I18n;
import javafx.application.Platform;
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
import plugin.swisskit.offlinepython.domain.RequirementsFile;
import plugin.swisskit.offlinepython.ui.LogConsole;
import plugin.swisskit.offlinepython.ui.OpbStyle;
import plugin.swisskit.offlinepython.ui.ProjectContext;
import plugin.swisskit.offlinepython.ui.control.PlatformMultiSelect;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class DepsPanel extends CommandPanel {

    /** Editable row backing the table (name/version/size only — platform is global). */
    public static class Row {
        public final javafx.beans.property.SimpleStringProperty name = new javafx.beans.property.SimpleStringProperty();
        public final javafx.beans.property.SimpleStringProperty version = new javafx.beans.property.SimpleStringProperty();
        public final javafx.beans.property.SimpleStringProperty size = new javafx.beans.property.SimpleStringProperty("—");
        public Row(String n, String v) { name.set(n); version.set(v); }
        // PropertyValueFactory-style accessors (kept for any external binding).
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
    private final PlatformMultiSelect platformSelect = new PlatformMultiSelect();

    public DepsPanel(LogConsole log, ProjectContext project) {
        super(log, project);
        recursive.setSelected(true); wheelFirst.setSelected(true);
        buildUi();
        loadFromProject();
    }

    @SuppressWarnings("unchecked")
    private void buildUi() {
        getChildren().add(titleNode());

        // --- columns ---
        TableColumn<Row, String> cName = textCol("包名", 1.4, r -> r.name.get(),
                OpbStyle.tableCellStyle(OpbStyle.TEXT_PRIMARY, true, false));
        TableColumn<Row, String> cVer = textCol("版本约束", 1.0, r -> r.version.get(),
                OpbStyle.tableCellStyle(OpbStyle.TEXT_SECONDARY, false, true));
        TableColumn<Row, String> cPlat = mirrorPlatformCol();
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
        // zebra rows + selection highlight
        table.setRowFactory(tv -> new javafx.scene.control.TableRow<>() {
            @Override protected void updateItem(Row r, boolean empty) {
                super.updateItem(r, empty);
                setStyle(empty || r == null ? "" : OpbStyle.tableRowStyle(getIndex() % 2 == 1, isSelected()));
            }
        });
        // refresh the mirror platform column whenever the global selection changes
        platformSelect.setOnChange(sel -> table.refresh());

        // --- toolbar (all secondary; platform dropdown + add on the right) ---
        TextField search = new TextField(); search.setStyle(UiUtils.fieldStyle()); search.setPromptText("搜索包…");
        search.textProperty().addListener((o, ov, nv) -> filterTable(nv));
        Button imp = UiUtils.glassBtn("导入 requirements.txt", false);
        imp.setTooltip(new Tooltip("选择本地 requirements.txt 并解析为依赖表"));
        imp.setOnAction(e -> doImport());
        Button pypiAdd = UiUtils.glassBtn("PyPI 查询版本", false);
        pypiAdd.setTooltip(new Tooltip("为选中行从 PyPI 查询最新版本与 wheel 大小"));
        pypiAdd.setOnAction(e -> doPyPIFetch());
        Button add = UiUtils.glassBtn("+ 添加依赖", false);
        add.setTooltip(new Tooltip("添加一行依赖，平台跟随全局目标"));
        HBox toolbar = new HBox(8, search, imp, pypiAdd);
        HBox.setHgrow(search, Priority.ALWAYS);
        Region spring = new Region(); HBox.setHgrow(spring, Priority.ALWAYS);
        toolbar.getChildren().addAll(spring, platformSelect, add);

        // --- add-row form (platform is global, no per-row field) ---
        TextField nField = new TextField(); nField.setStyle(UiUtils.fieldStyle()); nField.setPromptText("包名");
        TextField vField = new TextField(); vField.setStyle(UiUtils.fieldStyle()); vField.setPromptText("版本 (如 ==1.26.4)");
        add.setOnAction(e -> {
            if (nField.getText().isBlank()) return;
            table.getItems().add(new Row(nField.getText().trim(), vField.getText().trim()));
            nField.clear(); vField.clear();
            table.refresh();
            refreshSummary();
        });
        HBox addRow = new HBox(8, labeled("包名", nField), labeled("版本", vField));
        HBox.setHgrow(nField, Priority.ALWAYS);

        // --- options ---
        HBox opts = new HBox(18, recursive, wheelFirst, upgradePip);
        opts.setStyle("-fx-text-fill: " + OpbStyle.TEXT_SECONDARY + ";");

        // --- summary bar (secondary save + single primary CTA) ---
        Button save = UiUtils.glassBtn("保存 requirements.txt", false);
        save.setTooltip(new Tooltip("仅保存依赖与配置，不构建"));
        save.setOnAction(e -> doSave(false));
        Button saveBuild = UiUtils.glassBtn("保存并去构建 →", true);
        saveBuild.setTooltip(new Tooltip("保存后跳转构建面板"));
        saveBuild.setOnAction(e -> doSave(true));
        HBox summaryBar = new HBox(14, summary, spacer(), save, saveBuild);
        summaryBar.setStyle(OpbStyle.card() + " -fx-padding: 10 14 10 14;");
        HBox.setHgrow(summaryBar, Priority.ALWAYS);

        VBox tableBox = new VBox(6, table);
        getChildren().addAll(toolbar, tableBox, addRow, opts, summaryBar);
    }

    /** Styled text column backed by a per-row value supplier. */
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

    /** Target-platform column: ignores row data, mirrors the global PlatformMultiSelect (white text, high contrast). */
    private TableColumn<Row, String> mirrorPlatformCol() {
        TableColumn<Row, String> c = new TableColumn<>("目标平台");
        c.setPrefWidth(150);
        c.setStyle(OpbStyle.tableHeaderStyle());
        c.setCellFactory(tc -> new TableCell<>() {
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                if (empty) { setText(null); setStyle(""); return; }
                setText(platformSelect.summary());
                setStyle(OpbStyle.tableCellStyle(WHITE, false, false));
            }
        });
        return c;
    }

    private Label spacer() { Label s = new Label(); HBox.setHgrow(s, Priority.ALWAYS); return s; }

    private HBox labeled(String text, TextField f) {
        HBox h = new HBox(6, UiUtils.subLabel(text), f); HBox.setHgrow(f, Priority.ALWAYS); return h;
    }

    private void filterTable(String q) {
        // Search filtering omitted to keep V1 bounded; field present per spec for future.
    }

    private void loadFromProject() {
        Path dir = project.getProjectDir();
        if (dir == null) return;
        try {
            Path req = dir.resolve("requirements.txt");
            if (Files.exists(req)) {
                table.getItems().setAll(toRows(RequirementsFile.parse(Files.readString(req))));
            }
            if (project.getConfig() != null && project.getConfig().getPython() != null
                    && project.getConfig().getPython().getPlatforms() != null) {
                platformSelect.setSelected(project.getConfig().getPython().getPlatforms());
            }
            table.refresh();
            refreshSummary();
        } catch (Exception e) {
            log.log("加载 requirements 失败: " + e.getMessage());
        }
    }

    private List<Row> toRows(List<DependencySpec> specs) {
        List<Row> rows = new ArrayList<>();
        for (DependencySpec d : specs) rows.add(new Row(d.name(), d.versionSpec()));
        return rows;
    }

    private void doImport() {
        FileChooser fc = new FileChooser();
        File f = fc.showOpenDialog(getScene().getWindow());
        if (f == null) return;
        try {
            table.getItems().setAll(toRows(RequirementsFile.parse(Files.readString(f.toPath()))));
            table.refresh();
            refreshSummary();
            GlassNotification.toast(this, GlassNotification.Type.SUCCESS, "已导入 requirements.txt");
        } catch (Exception e) {
            GlassNotification.toast(this, GlassNotification.Type.ERROR, "导入失败");
        }
    }

    private void doPyPIFetch() {
        Row sel = table.getSelectionModel().getSelectedItem();
        if (sel == null) { GlassNotification.toast(this, GlassNotification.Type.WARNING, "先选中一行"); return; }
        new Thread(() -> {
            String exe = project.getConfig() != null ? project.getConfig().getPython().getExecutable() : null;
            var v = deps.latestVersion(sel.name.get(), exe);
            long size = deps.fetchSizeBytes(sel.name.get(), sel.version.get(), platformSelect.primaryPlatform());
            Platform.runLater(() -> {
                v.ifPresent(sel.version::set);
                sel.size.set(size > 0 ? humanSize(size) : "—");
                table.refresh();
                refreshSummary();
            });
        }, "opb-deps-pypi").start();
    }

    private void doSave(boolean thenBuild) {
        Path dir = project.getProjectDir();
        if (dir == null) { GlassNotification.toast(this, GlassNotification.Type.WARNING, "先打开或新建项目"); return; }
        try {
            List<DependencySpec> specs = new ArrayList<>();
            for (Row r : table.getItems()) specs.add(new DependencySpec(r.name.get(), r.version.get(), null));
            Files.writeString(dir.resolve("requirements.txt"), RequirementsFile.write(specs));
            if (project.getConfig() != null) {
                project.getConfig().getDownload().setRecursive(recursive.isSelected());
                project.getConfig().getDownload().setOnlyBinary(wheelFirst.isSelected());
                project.getConfig().getDownload().setUpgradePip(upgradePip.isSelected());
                project.getConfig().getPython().setPlatforms(new ArrayList<>(platformSelect.getSelected()));
                project.saveConfig();
            }
            GlassNotification.toast(this, GlassNotification.Type.SUCCESS, "已保存依赖");
            log.log("已保存 " + specs.size() + " 条依赖 · 目标 " + platformSelect.getSelected().size() + " 个平台");
            if (thenBuild) fireEventBuildNav();
        } catch (Exception e) {
            log.log("保存失败: " + e.getMessage());
            GlassNotification.toast(this, GlassNotification.Type.ERROR, "保存失败");
        }
    }

    /** Ask the shell to switch to the build panel. Implemented via a custom event. */
    private void fireEventBuildNav() {
        if (getScene() != null) getScene().getRoot().fireEvent(
                new plugin.swisskit.offlinepython.ui.NavEvent("build"));
    }

    private void refreshSummary() {
        int n = table.getItems().size();
        int p = platformSelect.getSelected().size();
        summary.setText("直接 " + n + " 个依赖 · 目标 " + p + " 个平台（预估大小按主平台）");
    }

    private static String humanSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }

    @Override public String title() { return I18n.get("opb.deps.title"); }
}
