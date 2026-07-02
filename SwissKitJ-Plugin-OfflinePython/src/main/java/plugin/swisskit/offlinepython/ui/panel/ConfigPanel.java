package plugin.swisskit.offlinepython.ui.panel;

import fan.summer.api.MdiIconUtil;
import fan.summer.api.component.GlassNotification;
import fan.summer.api.component.UiUtils;
import fan.summer.api.i18n.I18n;
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
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import plugin.swisskit.offlinepython.domain.DependencySpec;
import plugin.swisskit.offlinepython.domain.PlatformCatalog;
import plugin.swisskit.offlinepython.domain.RequirementsFile;
import plugin.swisskit.offlinepython.ui.LogConsole;
import plugin.swisskit.offlinepython.ui.OpbStyle;
import plugin.swisskit.offlinepython.ui.ProjectContext;
import plugin.swisskit.offlinepython.ui.control.EmptyState;
import plugin.swisskit.offlinepython.ui.control.PanelHeader;
import plugin.swisskit.offlinepython.ui.control.PlatformMultiSelect;
import plugin.swisskit.offlinepython.ui.dialog.PyPISearchDialog;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ConfigPanel extends CommandPanel {

    /** Editable row backing the table: name/version/size + per-row target platforms. */
    public static class Row {
        public final javafx.beans.property.SimpleStringProperty name = new javafx.beans.property.SimpleStringProperty();
        public final javafx.beans.property.SimpleStringProperty version = new javafx.beans.property.SimpleStringProperty();
        public final javafx.beans.property.SimpleStringProperty size = new javafx.beans.property.SimpleStringProperty("—");
        public final List<String> platforms = new ArrayList<>(List.of("win_amd64"));
        public Row(String n, String v, List<String> plats) {
            name.set(n); version.set(v);
            if (plats != null && !plats.isEmpty()) { platforms.clear(); platforms.addAll(plats); }
        }
        public javafx.beans.property.SimpleStringProperty nameProperty() { return name; }
        public javafx.beans.property.SimpleStringProperty versionProperty() { return version; }
        public javafx.beans.property.SimpleStringProperty sizeProperty() { return size; }
        public String toRequirement() {
            return name.get() + (version.get() == null || version.get().isBlank() ? "" : version.get());
        }
    }

    private final TableView<Row> table = new TableView<>();
    private final CheckBox recursive = new CheckBox("递归");
    private final CheckBox wheelFirst = new CheckBox("wheel 优先");
    private final CheckBox upgradePip = new CheckBox("升级 pip");
    private final Label summary = new Label();

    // 行2 表单：当前正在新增/编辑的这条依赖
    private final TextField nField = new TextField();
    private final TextField vField = new TextField();
    private final PlatformMultiSelect platformSelect = new PlatformMultiSelect();
    private long pendingSize = 0L; // 在线搜索带回的 wheel 大小，提交时写入行
    private final Runnable onOpen;
    /** 表单+表格+选项+摘要的容器(有项目时显示,无项目时隐藏)。 */
    private final VBox contentBox = new VBox();
    /** 空状态容器(无项目时显示,有项目时隐藏)。 */
    private final VBox emptyHolder = new VBox();

    public ConfigPanel(LogConsole log, ProjectContext project, Runnable onOpen) {
        super(log, project);
        this.onOpen = onOpen;
        recursive.setSelected(true); wheelFirst.setSelected(true);
        buildUi();
        reload();
    }

    /** 重新加载:无项目显示空状态,有项目加载依赖表。供 CommandShell 打开项目后调用。 */
    public void reload() {
        boolean hasProject = project.getProjectDir() != null;
        contentBox.setManaged(hasProject); contentBox.setVisible(hasProject);
        emptyHolder.setManaged(!hasProject); emptyHolder.setVisible(!hasProject);
        if (hasProject) {
            loadFromProject();
        } else if (emptyHolder.getChildren().isEmpty()) {
            EmptyState empty = new EmptyState("folder-off-outline", I18n.get("opb.project.empty"));
            Button openBtn = UiUtils.glassBtn(I18n.get("opb.project.open"), true);
            openBtn.setOnAction(e -> { if (onOpen != null) onOpen.run(); });
            empty.setActions(new HBox(8, openBtn));
            emptyHolder.getChildren().setAll(empty);
        }
    }

    @SuppressWarnings("unchecked")
    private void buildUi() {
        PanelHeader header = new PanelHeader(I18n.get("opb.deps.title"));
        Button imp = UiUtils.glassBtn("导入 requirements.txt", false);
        imp.setTooltip(new Tooltip("选择本地 requirements.txt 并解析为依赖表"));
        imp.setOnAction(e -> doImport());
        Button search = UiUtils.glassBtn("🔍 在线搜索", false);
        search.setTooltip(new Tooltip("从 PyPI 在线搜索该包的 wheel,选中后回填包名/版本/平台"));
        search.setOnAction(e -> doSearch());
        Button addBtn = UiUtils.glassBtn("增加配置", true);
        addBtn.setTooltip(new Tooltip("将当前包名/版本/平台加入依赖表并保存"));
        addBtn.setOnAction(e -> doSave());
        header.addActions(imp, search, addBtn);
        getChildren().add(header);

        // --- 行2：包名 / 版本 / 目标平台（per-dep） ---
        nField.setStyle(UiUtils.fieldStyle()); nField.setPromptText("包名");
        vField.setStyle(UiUtils.fieldStyle()); vField.setPromptText("版本 (如 ==1.26.4)");
        HBox row2 = new HBox(8, labeled("包名", nField), labeled("版本", vField), platformBox());
        HBox.setHgrow(nField, Priority.ALWAYS);

        // --- 表格列 ---
        TableColumn<Row, String> cName = textCol("包名", 1.4, r -> r.name.get());
        TableColumn<Row, String> cVer = textCol("版本约束", 1.0, r -> r.version.get());
        TableColumn<Row, String> cPlat = perRowPlatformCol();
        TableColumn<Row, String> cSize = textCol("预估大小", 0.9, r -> r.size.get());
        TableColumn<Row, Row> cDel = new TableColumn<>("");
        cDel.setCellFactory(tc -> new TableCell<>() {
            private final Button del = UiUtils.glassBtn("✕", false);
            { del.setTooltip(new Tooltip("删除该行"));
              del.setOnAction(e -> { table.getItems().remove(getIndex()); refreshSummary(); }); }
            @Override protected void updateItem(Row r, boolean empty) {
                super.updateItem(r, empty); setGraphic(empty ? null : del);
            }
        });
        table.getColumns().addAll(cName, cVer, cPlat, cSize, cDel);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.setFixedCellSize(30);
        table.setMinHeight(150);
        table.getStyleClass().add("sk-table");
        // 主从编辑：选中行 → 载入表单；清空 → 重置为新增态
        table.getSelectionModel().selectedItemProperty().addListener((o, ov, nv) -> loadForm(nv));

        // --- 选项 ---
        HBox opts = new HBox(18, recursive, wheelFirst, upgradePip);
        opts.setStyle("-fx-text-fill: " + OpbStyle.TEXT_SECONDARY + ";");

        // --- 底栏：摘要 ---
        HBox summaryBar = new HBox(14, summary);
        summaryBar.setStyle(OpbStyle.card() + " -fx-padding: 10 14 10 14;");
        HBox.setHgrow(summaryBar, Priority.ALWAYS);

        VBox tableBox = new VBox(6, table);
        contentBox.setSpacing(8);
        contentBox.getChildren().addAll(row2, tableBox, opts, summaryBar);
        // header + contentBox(有项目) + emptyHolder(无项目);reload() 切换可见性
        getChildren().addAll(contentBox, emptyHolder);
    }

    private TableColumn<Row, String> textCol(String title, double widthFactor,
                                             java.util.function.Function<Row, String> value) {
        TableColumn<Row, String> c = new TableColumn<>(title);
        c.setCellValueFactory(cb -> new javafx.beans.property.SimpleStringProperty(value.apply(cb.getValue())));
        c.setPrefWidth(widthFactor * 100);
        c.setCellFactory(tc -> new TableCell<>() {
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                if (empty) { setText(null); return; }
                setText(v == null || v.isBlank() ? "—" : v);
            }
        });
        return c;
    }

    /** 目标平台列：只读，按平台 OS 图标区分（每平台一个 MDI 图标 + 文本汇总）。 */
    private TableColumn<Row, String> perRowPlatformCol() {
        TableColumn<Row, String> c = new TableColumn<>("目标平台");
        c.setPrefWidth(180);
        c.setCellFactory(tc -> new TableCell<>() {
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                if (empty) { setGraphic(null); setText(null); return; }
                int idx = getIndex();
                List<Row> items = getTableView().getItems();
                if (idx < 0 || idx >= items.size()) { setGraphic(null); setText(null); return; }
                List<String> plats = items.get(idx).platforms;
                HBox icons = new HBox(3);
                String firstArch = null; boolean mixedArch = false; boolean anyArch = false;
                for (String p : plats) {
                    var ic = MdiIconUtil.createIcon(PlatformCatalog.iconOf(p), 14, "-fx-fill: " + OpbStyle.TEXT_PRIMARY + ";");
                    Tooltip.install(ic, new Tooltip(PlatformCatalog.labelOf(p)));
                    icons.getChildren().add(ic);
                    String ab = PlatformCatalog.archBitsLabel(p);
                    if (!ab.isEmpty()) {
                        anyArch = true;
                        if (firstArch == null) firstArch = ab; else if (!firstArch.equals(ab)) mixedArch = true;
                    }
                }
                setGraphic(icons);
                setText(!anyArch ? "通用" : mixedArch ? "多种架构" : firstArch);
            }
        });
        return c;
    }

    private HBox labeled(String text, TextField f) {
        HBox h = new HBox(6, UiUtils.subLabel(text), f); HBox.setHgrow(f, Priority.ALWAYS); return h;
    }

    private HBox platformBox() {
        return new HBox(6, UiUtils.subLabel("目标平台"), platformSelect);
    }

    /** 载入行到表单（编辑态）；null → 重置为新增态。 */
    private void loadForm(Row r) {
        if (r == null) {
            nField.clear();
            vField.clear();
            platformSelect.setSelected(defaultPlatforms());
            pendingSize = 0L;
        } else {
            nField.setText(r.name.get());
            vField.setText(r.version.get());
            platformSelect.setSelected(r.platforms);
            pendingSize = 0L;
        }
    }

    private List<String> defaultPlatforms() {
        return (project.getConfig() != null && project.getConfig().getPython() != null
                && project.getConfig().getPython().getPlatforms() != null)
                ? project.getConfig().getPython().getPlatforms() : List.of("win_amd64");
    }

    private void loadFromProject() {
        Path dir = project.getProjectDir();
        if (dir == null) return;
        try {
            Path req = dir.resolve("requirements.txt");
            List<Row> rows = new ArrayList<>();
            if (Files.exists(req)) {
                Map<String, List<String>> dp = (project.getConfig() != null && project.getConfig().getPython() != null)
                        ? project.getConfig().getPython().getDepPlatforms() : new LinkedHashMap<>();
                List<String> defaults = defaultPlatforms();
                for (DependencySpec d : RequirementsFile.parse(Files.readString(req))) {
                    rows.add(new Row(d.name(), d.versionSpec(),
                            dp.getOrDefault(DependencySpec.normalizeName(d.name()), defaults)));
                }
            }
            table.getItems().setAll(rows);
            table.refresh();
            refreshSummary();
        } catch (Exception e) {
            log.log("加载 requirements 失败: " + e.getMessage());
        }
    }

    private void doImport() {
        FileChooser fc = new FileChooser();
        File f = fc.showOpenDialog(getScene().getWindow());
        if (f == null) return;
        try {
            Map<String, List<String>> dp = (project.getConfig() != null && project.getConfig().getPython() != null)
                    ? project.getConfig().getPython().getDepPlatforms() : new LinkedHashMap<>();
            List<String> defaults = defaultPlatforms();
            List<Row> rows = new ArrayList<>();
            for (DependencySpec d : RequirementsFile.parse(Files.readString(f.toPath()))) {
                rows.add(new Row(d.name(), d.versionSpec(),
                        dp.getOrDefault(DependencySpec.normalizeName(d.name()), defaults)));
            }
            table.getItems().setAll(rows);
            table.refresh();
            refreshSummary();
            GlassNotification.toast(this, GlassNotification.Type.SUCCESS, "已导入 requirements.txt");
        } catch (Exception e) {
            GlassNotification.toast(this, GlassNotification.Type.ERROR, "导入失败");
        }
    }

    private void doSearch() {
        PyPISearchDialog dlg = new PyPISearchDialog(getScene().getWindow());
        dlg.showAndWait().ifPresent(w -> {
            nField.setText(dlg.packageName());
            vField.setText("==" + w.version());
            platformSelect.setSelected(List.of(w.platformTag()));
            pendingSize = w.sizeBytes();
        });
    }

    /** 提交表单（更新选中行或新增）并持久化；thenBuild=true 再跳转构建。 */
    private void doSave() {
        Path dir = project.getProjectDir();
        if (dir == null) { GlassNotification.toast(this, GlassNotification.Type.WARNING, "先打开或新建项目"); return; }
        String name = nField.getText().trim();
        boolean committed = false;
        boolean updating = false;
        if (!name.isBlank()) {
            String ver = vField.getText().trim();
            List<String> plats = new ArrayList<>(platformSelect.getSelected());
            Row sel = table.getSelectionModel().getSelectedItem();
            updating = sel != null;
            if (updating) {
                sel.name.set(name); sel.version.set(ver);
                sel.platforms.clear(); sel.platforms.addAll(plats);
                sel.size.set(pendingSize > 0 ? humanSize(pendingSize) : "—");
            } else {
                Row nr = new Row(name, ver, plats);
                nr.size.set(pendingSize > 0 ? humanSize(pendingSize) : "—");
                table.getItems().add(nr);
            }
            pendingSize = 0L;
            table.refresh();
            committed = true;
        }
        try {
            persist(dir);
            GlassNotification.toast(this, GlassNotification.Type.SUCCESS,
                    committed ? (updating ? "已更新依赖" : "已添加依赖") : "已保存配置");
            log.log("已保存 " + table.getItems().size() + " 条依赖");
            if (committed) {
                // 重置表单为新增态：清空选中会触发 loadForm(null)，清掉包名/版本/平台，避免二次添加
                table.getSelectionModel().clearSelection();
            }
        } catch (Exception e) {
            log.log("保存失败: " + e.getMessage());
            GlassNotification.toast(this, GlassNotification.Type.ERROR, "保存失败");
        }
    }

    private void persist(Path dir) throws Exception {
        List<DependencySpec> specs = new ArrayList<>();
        for (Row r : table.getItems()) specs.add(new DependencySpec(r.name.get(), r.version.get(), null));
        Files.writeString(dir.resolve("requirements.txt"), RequirementsFile.write(specs));
        if (project.getConfig() != null) {
            project.getConfig().getDownload().setRecursive(recursive.isSelected());
            project.getConfig().getDownload().setOnlyBinary(wheelFirst.isSelected());
            project.getConfig().getDownload().setUpgradePip(upgradePip.isSelected());
            Map<String, List<String>> dp = project.getConfig().getPython().getDepPlatforms();
            dp.clear();
            for (Row r : table.getItems()) {
                dp.put(DependencySpec.normalizeName(r.name.get()), new ArrayList<>(r.platforms));
            }
            project.saveConfig();
        }
        refreshSummary();
    }

    private void refreshSummary() {
        summary.setText("直接 " + table.getItems().size() + " 个依赖（平台按各自目标）");
    }

    private static String humanSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }

    @Override public String title() { return I18n.get("opb.deps.title"); }
}
