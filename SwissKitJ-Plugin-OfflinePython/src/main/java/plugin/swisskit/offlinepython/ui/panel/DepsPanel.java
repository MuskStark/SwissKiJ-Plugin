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
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import plugin.swisskit.offlinepython.command.DepsService;
import plugin.swisskit.offlinepython.domain.DependencySpec;
import plugin.swisskit.offlinepython.domain.RequirementsFile;
import plugin.swisskit.offlinepython.ui.LogConsole;
import plugin.swisskit.offlinepython.ui.ProjectContext;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class DepsPanel extends CommandPanel {

    /** Editable row backing the table. */
    public static class Row {
        public final javafx.beans.property.SimpleStringProperty name = new javafx.beans.property.SimpleStringProperty();
        public final javafx.beans.property.SimpleStringProperty version = new javafx.beans.property.SimpleStringProperty();
        public final javafx.beans.property.SimpleStringProperty platform = new javafx.beans.property.SimpleStringProperty();
        public final javafx.beans.property.SimpleStringProperty size = new javafx.beans.property.SimpleStringProperty("—");
        public Row(String n, String v, String p) { name.set(n); version.set(v); platform.set(p); }
        // PropertyValueFactory needs xxxProperty() accessors or table cells render empty.
        public javafx.beans.property.SimpleStringProperty nameProperty() { return name; }
        public javafx.beans.property.SimpleStringProperty versionProperty() { return version; }
        public javafx.beans.property.SimpleStringProperty platformProperty() { return platform; }
        public javafx.beans.property.SimpleStringProperty sizeProperty() { return size; }
        public String toRequirement() {
            String s = name.get() + (version.get() == null || version.get().isBlank() ? "" : version.get());
            return s;
        }
    }

    private final DepsService deps = new DepsService();
    private final TableView<Row> table = new TableView<>();
    private final CheckBox recursive = new CheckBox("递归");
    private final CheckBox wheelFirst = new CheckBox("wheel 优先");
    private final CheckBox upgradePip = new CheckBox("升级 pip");
    private final Label summary = new Label();

    public DepsPanel(LogConsole log, ProjectContext project) {
        super(log, project);
        recursive.setSelected(true); wheelFirst.setSelected(true);
        buildUi();
        loadFromProject();
    }

    @SuppressWarnings("unchecked")
    private void buildUi() {
        getChildren().add(titleNode());

        TableColumn<Row, String> cName = col("包名", "name", 1.4);
        TableColumn<Row, String> cVer = col("版本约束", "version", 1.0);
        TableColumn<Row, String> cPlat = col("目标平台", "platform", 1.1);
        TableColumn<Row, String> cSize = col("预估大小", "size", 0.8);
        TableColumn<Row, Row> cDel = new TableColumn<>("");
        cDel.setCellFactory(tc -> new TableCell<>() {
            private final Button del = UiUtils.glassBtn("✕", false);
            { del.setOnAction(e -> table.getItems().remove(getIndex())); }
            @Override protected void updateItem(Row r, boolean empty) {
                super.updateItem(r, empty); setGraphic(empty || r == null ? null : del);
            }
        });
        table.getColumns().addAll(cName, cVer, cPlat, cSize, cDel);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.setMinHeight(150);
        table.setStyle("-fx-background-color: transparent;");

        // toolbar
        TextField search = new TextField(); search.setStyle(UiUtils.fieldStyle()); search.setPromptText("搜索包…");
        search.textProperty().addListener((o, ov, nv) -> filterTable(nv));
        Button imp = UiUtils.glassBtn("导入 requirements.txt", false);
        imp.setOnAction(e -> doImport());
        Button pypiAdd = UiUtils.glassBtn("PyPI 查询版本", false);
        pypiAdd.setOnAction(e -> doPyPIFetch());
        Button save = UiUtils.glassBtn("保存", true);
        save.setOnAction(e -> doSave(false));
        HBox toolbar = new HBox(8, search, imp, pypiAdd);
        HBox.setHgrow(search, Priority.ALWAYS);
        toolbar.getChildren().addAll(new javafx.scene.layout.Region(), save);

        // add row
        TextField nField = new TextField(); nField.setStyle(UiUtils.fieldStyle()); nField.setPromptText("包名");
        TextField vField = new TextField(); vField.setStyle(UiUtils.fieldStyle()); vField.setPromptText("版本 (如 ==1.26.4)");
        TextField pField = new TextField(); pField.setStyle(UiUtils.fieldStyle()); pField.setPromptText("平台 (如 win_amd64)");
        Button add = UiUtils.glassBtn("＋", true);
        add.setOnAction(e -> {
            if (nField.getText().isBlank()) return;
            Row r = new Row(nField.getText().trim(), vField.getText().trim(),
                    pField.getText().isBlank() ? currentPlatform() : pField.getText().trim());
            table.getItems().add(r);
            nField.clear(); vField.clear(); pField.clear();
            refreshSummary();
        });
        HBox addRow = new HBox(8, labeled("包名", nField), labeled("版本", vField), labeled("平台", pField), add);
        HBox.setHgrow(nField, Priority.ALWAYS);

        // options
        HBox opts = new HBox(18, recursive, wheelFirst, upgradePip);
        opts.setStyle("-fx-text-fill: " + plugin.swisskit.offlinepython.ui.OpbStyle.TEXT_SECONDARY + ";");

        // summary
        Button saveBuild = UiUtils.glassBtn("保存并去构建 →", true);
        saveBuild.setOnAction(e -> doSave(true));
        HBox summaryBar = new HBox(14, summary, spacer(), platformPill(), saveBuild);
        summaryBar.setStyle(plugin.swisskit.offlinepython.ui.OpbStyle.card() + " -fx-padding: 10 14 10 14;");
        HBox.setHgrow(summaryBar, Priority.ALWAYS);

        VBox tableBox = new VBox(6, table);
        getChildren().addAll(toolbar, tableBox, addRow, opts, summaryBar);
    }

    private Label spacer() { Label s = new Label(); HBox.setHgrow(s, Priority.ALWAYS); return s; }

    private HBox labeled(String text, TextField f) {
        HBox h = new HBox(6, UiUtils.subLabel(text), f); HBox.setHgrow(f, Priority.ALWAYS); return h;
    }

    private TableColumn<Row, String> col(String title, String prop, double width) {
        TableColumn<Row, String> c = new TableColumn<>(title);
        c.setCellValueFactory(new PropertyValueFactory<>(prop));
        c.setPrefWidth(width * 100);
        c.setStyle("-fx-text-fill: rgba(255,255,255,0.85);");
        return c;
    }

    private Label platformPill() {
        Label p = new Label(currentPlatform());
        p.setStyle("-fx-background-color: " + plugin.swisskit.offlinepython.ui.OpbStyle.ACCENT_SOFT
                + "; -fx-text-fill: #9cc0ff; -fx-background-radius: 8; -fx-padding: 2 10 2 10;");
        return p;
    }

    private String currentPlatform() {
        return project.getConfig() != null && project.getConfig().getPython() != null
                ? project.getConfig().getPython().getPrimaryPlatform() : "win_amd64";
    }

    private void filterTable(String q) {
        // Simple: TableView shows all; filter is best-effort via re-load not needed for V1.
        // (Search filtering omitted to keep V1 bounded; field present per spec for future.)
    }

    private void loadFromProject() {
        Path dir = project.getProjectDir();
        if (dir == null) return;
        Path req = dir.resolve("requirements.txt");
        try {
            if (Files.exists(req)) {
                table.getItems().setAll(toRows(RequirementsFile.parse(Files.readString(req))));
                refreshSummary();
            }
        } catch (Exception e) {
            log.log("加载 requirements 失败: " + e.getMessage());
        }
    }

    private List<Row> toRows(List<DependencySpec> specs) {
        List<Row> rows = new ArrayList<>();
        for (DependencySpec d : specs) rows.add(new Row(d.name(), d.versionSpec(), "win_amd64"));
        return rows;
    }

    private void doImport() {
        FileChooser fc = new FileChooser();
        File f = fc.showOpenDialog(getScene().getWindow());
        if (f == null) return;
        try {
            table.getItems().setAll(toRows(RequirementsFile.parse(Files.readString(f.toPath()))));
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
            long size = deps.fetchSizeBytes(sel.name.get(), sel.version.get(), sel.platform.get());
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
            // persist options into config.download
            if (project.getConfig() != null) {
                project.getConfig().getDownload().setRecursive(recursive.isSelected());
                project.getConfig().getDownload().setOnlyBinary(wheelFirst.isSelected());
                project.getConfig().getDownload().setUpgradePip(upgradePip.isSelected());
                project.saveConfig();
            }
            GlassNotification.toast(this, GlassNotification.Type.SUCCESS, "已保存依赖");
            log.log("已保存 " + specs.size() + " 条依赖");
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
        summary.setText("直接 " + n + " 个依赖");
    }

    private static String humanSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }

    @Override public String title() { return I18n.get("opb.deps.title"); }
}
