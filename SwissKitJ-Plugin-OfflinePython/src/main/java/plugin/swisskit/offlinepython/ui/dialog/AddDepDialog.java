package plugin.swisskit.offlinepython.ui.dialog;

import fan.summer.api.component.UiUtils;
import fan.summer.api.theme.Themes;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import plugin.swisskit.offlinepython.ui.OpbStyle;
import plugin.swisskit.offlinepython.ui.control.PlatformMultiSelect;

import java.util.List;
import java.util.Optional;

/**
 * 模态对话框:填写单条依赖(包名 / 版本 / 目标平台),确认后返回。
 * 由 ConfigPanel 的「增加依赖」按钮唤起。
 */
public class AddDepDialog {

    private final Stage stage = new Stage();
    private final javafx.scene.control.TextField nameField = new TextField();
    private final javafx.scene.control.TextField versionField = new TextField();
    private final PlatformMultiSelect platformSelect = new PlatformMultiSelect();
    private Result result;

    /** 对话框返回值(包名/版本/平台列表)。 */
    public record Result(String name, String version, List<String> platforms) {}

    public AddDepDialog(Window owner) {
        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("增加依赖");
        buildUi();
    }

    private void buildUi() {
        nameField.setStyle(UiUtils.fieldStyle());
        nameField.setPromptText("包名,如 numpy");
        versionField.setStyle(UiUtils.fieldStyle());
        versionField.setPromptText("版本,如 ==1.26.4");

        Label nLabel = UiUtils.subLabel("包名");
        Label vLabel = UiUtils.subLabel("版本");
        Label pLabel = UiUtils.subLabel("目标平台");

        // 在线搜索:从 PyPI 搜索 wheel,回填版本+平台
        Button search = UiUtils.glassBtn("🔍 在线搜索", false);
        search.setOnAction(e -> {
            PyPISearchDialog pyDlg = new PyPISearchDialog(stage);
            pyDlg.showAndWait().ifPresent(w -> {
                nameField.setText(pyDlg.packageName());
                versionField.setText("==" + w.version());
                platformSelect.setSelected(List.of(w.platformTag()));
            });
        });

        VBox form = new VBox(8);
        form.getChildren().addAll(nLabel, nameField, vLabel, versionField, search, pLabel, platformSelect);

        Button ok = UiUtils.glassBtn("确定", true);
        ok.setOnAction(e -> {
            String n = nameField.getText().trim();
            if (n.isBlank()) {
                fan.summer.api.component.GlassNotification.toast(stage.getScene().getRoot(),
                        fan.summer.api.component.GlassNotification.Type.WARNING, "请输入包名");
                return;
            }
            result = new Result(n, versionField.getText().trim(), platformSelect.getSelected());
            stage.close();
        });
        Button cancel = UiUtils.glassBtn("取消", false);
        cancel.setOnAction(e -> stage.close());

        HBox actions = new HBox(8, cancel, ok);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.setPadding(new Insets(8, 0, 0, 0));

        VBox root = new VBox(14, form, actions);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: " + OpbStyle.GLASS_BG + ";"
                + " -fx-background-radius: 10; -fx-border-color: " + OpbStyle.GLASS_BORDER + ";"
                + " -fx-border-radius: 10;");
        Scene scene = new Scene(root, 420, 320);
        scene.setFill(Color.TRANSPARENT);
        Themes.applyTo(scene);
        stage.setScene(scene);
    }

    /** 显示对话框,返回用户输入(取消则 empty)。 */
    public Optional<Result> showAndWait() {
        stage.showAndWait();
        return Optional.ofNullable(result);
    }
}
