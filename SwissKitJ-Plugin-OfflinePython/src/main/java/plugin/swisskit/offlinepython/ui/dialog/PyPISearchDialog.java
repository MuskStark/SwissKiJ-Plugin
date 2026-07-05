package plugin.swisskit.offlinepython.ui.dialog;

import fan.summer.api.component.SkNotification;
import fan.summer.api.component.UiUtils;
import fan.summer.api.host.PluginHost;
import fan.summer.api.theme.Themes;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import plugin.swisskit.offlinepython.command.DepsService;
import plugin.swisskit.offlinepython.command.WheelInfo;

import java.util.List;
import java.util.Optional;

/** Modal PyPI wheel search: type a package name, list its wheels, pick one to return. */
public class PyPISearchDialog {

    private final DepsService deps = new DepsService();
    private final PluginHost host;
    private final Stage stage = new Stage();
    private final TextField query = new TextField();
    private final TableView<WheelInfo> table = new TableView<>();
    private final Button search = UiUtils.glassBtn("搜索", false);
    private WheelInfo chosen;

    public PyPISearchDialog(Window owner, PluginHost host) {
        this.host = host;
        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("PyPI 在线搜索");
        buildUi();
    }

    private void buildUi() {
        query.getStyleClass().add("sk-field");
        query.setPromptText("输入包名，如 numpy");
        search.setOnAction(e -> doSearch());
        query.setOnAction(e -> doSearch());
        HBox bar = new HBox(8, query, search);
        HBox.setHgrow(query, javafx.scene.layout.Priority.ALWAYS);

        TableColumn<WheelInfo, String> cVer = col("版本", WheelInfo::version);
        TableColumn<WheelInfo, String> cPlat = col("平台", WheelInfo::platformTag);
        TableColumn<WheelInfo, String> cSize = col("大小", w -> human(w.sizeBytes()));
        TableColumn<WheelInfo, String> cFn = col("文件名", WheelInfo::filename);
        table.getColumns().addAll(cVer, cPlat, cSize, cFn);
        table.getStyleClass().add("sk-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.setPrefHeight(320);
        table.setPlaceholder(new Label("输入包名并搜索"));

        Button ok = UiUtils.glassBtn("确定", true);
        ok.setDisable(true);
        ok.setOnAction(e -> { chosen = table.getSelectionModel().getSelectedItem(); stage.close(); });
        Button cancel = UiUtils.glassBtn("取消", false);
        cancel.setOnAction(e -> stage.close());
        table.getSelectionModel().selectedItemProperty().addListener((o, ov, nv) -> ok.setDisable(nv == null));

        HBox actions = new HBox(8, cancel, ok);
        VBox root = new VBox(10, bar, table, actions);
        root.setPadding(new Insets(16));
        root.getStyleClass().add("sk-dialog");
        Scene scene = new Scene(root, 600, 460);
        scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        Themes.applyTo(scene);
        stage.setScene(scene);
    }

    private static TableColumn<WheelInfo, String> col(String title, java.util.function.Function<WheelInfo, String> get) {
        TableColumn<WheelInfo, String> c = new TableColumn<>(title);
        c.setCellValueFactory(cb -> new SimpleStringProperty(get.apply(cb.getValue())));
        return c;
    }

    private void doSearch() {
        String pkg = query.getText().trim();
        if (pkg.isBlank()) {
            host.notifications().toast(table, SkNotification.Type.WARNING, "请输入包名");
            return;
        }
        search.setDisable(true);   // 期间按钮置灰，避免并发搜索（spec §4.D）
        query.setDisable(true);
        table.setPlaceholder(new Label("查询中…"));
        table.getItems().clear();
        host.tasks().submit("opb-pypi-search",
            () -> deps.searchWheels(pkg),
            result -> {  // FX thread
                search.setDisable(false);
                query.setDisable(false);
                if (result.isEmpty()) {
                    table.setPlaceholder(new Label("未找到 wheel（包名不存在或无 wheel）"));
                    host.notifications().toast(table, SkNotification.Type.WARNING, "未找到 wheel");
                } else {
                    table.getItems().setAll(result);
                }
            },
            error -> {  // FX thread
                search.setDisable(false);
                query.setDisable(false);
                host.notifications().toast(table, SkNotification.Type.ERROR, "搜索失败: " + error.getMessage());
            });
    }

    /** The package name the user searched for (trimmed query). */
    public String packageName() { return query.getText().trim(); }

    /** Show modal; return the chosen wheel (empty if cancelled). */
    public Optional<WheelInfo> showAndWait() {
        stage.showAndWait();
        return Optional.ofNullable(chosen);
    }

    private static String human(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }
}
