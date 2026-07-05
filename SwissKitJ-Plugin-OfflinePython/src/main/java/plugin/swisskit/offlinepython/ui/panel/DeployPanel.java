package plugin.swisskit.offlinepython.ui.panel;

import fan.summer.api.component.SkNotification;
import fan.summer.api.component.UiUtils;
import fan.summer.api.host.PluginHost;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import plugin.swisskit.offlinepython.command.DeployService;
import plugin.swisskit.offlinepython.domain.BundleReader;
import plugin.swisskit.offlinepython.domain.DeployResult;
import plugin.swisskit.offlinepython.domain.DeployTarget;
import plugin.swisskit.offlinepython.domain.PlatformMatcher;
import plugin.swisskit.offlinepython.domain.WheelEntry;
import plugin.swisskit.offlinepython.infra.OpbLogger;
import plugin.swisskit.offlinepython.infra.PythonDetector;
import plugin.swisskit.offlinepython.ui.OpbStyle;
import plugin.swisskit.offlinepython.ui.control.PanelHeader;

import java.nio.file.Path;
import java.util.List;

/**
 * 部署页:离线机上加载 bundle ZIP → 检测本机平台 → 筛选 wheel → 选目标 → 安装。
 * 不依赖 ProjectContext(随时可用)。
 */
public class DeployPanel extends CommandPanel {

    private final TableView<WheelEntry> matchTable = new TableView<>();
    private final Label envLabel = new Label();
    private final Label summary = new Label();
    private final RadioButton rbGlobal = new RadioButton(host.i18n().get("opb.deploy.targetGlobal"));
    private final RadioButton rbVenv = new RadioButton(host.i18n().get("opb.deploy.targetVenv"));
    private final TextField venvPath = new TextField();
    private final Button installBtn = UiUtils.glassBtn(host.i18n().get("opb.deploy.install"), true);
    private final ProgressBar progress = new ProgressBar(0);
    private final TextArea logArea = new TextArea();
    private final VBox previewBox = new VBox(6);
    private final VBox targetBox = new VBox(6);

    private Path selectedZip;
    private PythonDetector.Detection detection;
    private List<WheelEntry> matchedWheels = List.of();

    public DeployPanel(OpbLogger log, PluginHost host) {
        super(log, null, host);  // no project context needed
        buildUi();
        detectEnv();
    }

    private void buildUi() {
        PanelHeader header = new PanelHeader(host.i18n().get("opb.deploy.title"));

        // ① 选包
        Button choose = UiUtils.glassBtn(host.i18n().get("opb.deploy.selectZip"), false);
        choose.setOnAction(e -> chooseZip());
        envLabel.setStyle("-fx-text-fill: " + OpbStyle.TEXT_SECONDARY + "; -fx-padding: 4 0;");
        VBox selectBox = new VBox(8, choose, envLabel);
        selectBox.setStyle(OpbStyle.card() + " -fx-padding: 14;");

        // ② 预览
        summary.setStyle("-fx-text-fill: " + OpbStyle.TEXT_PRIMARY + "; -fx-font-weight: 500;");
        TableColumn<WheelEntry, String> cName = new TableColumn<>("包名");
        cName.setCellValueFactory(cb -> new javafx.beans.property.SimpleStringProperty(cb.getValue().getName()));
        TableColumn<WheelEntry, String> cVer = new TableColumn<>("版本");
        cVer.setCellValueFactory(cb -> new javafx.beans.property.SimpleStringProperty(cb.getValue().getVersion()));
        TableColumn<WheelEntry, String> cFile = new TableColumn<>("wheel");
        cFile.setCellValueFactory(cb -> {
            String f = cb.getValue().getFile();
            int slash = f.lastIndexOf('/');
            return new javafx.beans.property.SimpleStringProperty(slash < 0 ? f : f.substring(slash + 1));
        });
        cFile.setCellFactory(tc -> new TableCell<>() {
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                setText(empty || v == null ? null : v);
                setStyle("-fx-font-size: 11px; -fx-text-fill: " + OpbStyle.TEXT_SECONDARY + ";");
            }
        });
        matchTable.getColumns().addAll(cName, cVer, cFile);
        matchTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        matchTable.setFixedCellSize(28);
        matchTable.setMinHeight(150);
        matchTable.setMaxHeight(260);
        matchTable.getStyleClass().add("sk-table");
        previewBox.getChildren().addAll(summary, matchTable);
        previewBox.setStyle(OpbStyle.card() + " -fx-padding: 14;");
        previewBox.setVisible(false);
        previewBox.setManaged(false);

        // ③ 目标环境
        ToggleGroup tg = new ToggleGroup();
        rbGlobal.setToggleGroup(tg);
        rbVenv.setToggleGroup(tg);
        rbGlobal.setSelected(true);
        venvPath.setPromptText("虚拟环境路径");
        venvPath.setStyle(UiUtils.fieldStyle());
        Button browse = UiUtils.glassBtn("浏览", false);
        browse.setOnAction(e -> {
            DirectoryChooser dc = new DirectoryChooser();
            var d = dc.showDialog(getScene().getWindow());
            if (d != null) venvPath.setText(d.getAbsolutePath());
        });
        HBox venvRow = new HBox(8, venvPath, browse);
        HBox.setHgrow(venvPath, Priority.ALWAYS);
        targetBox.getChildren().addAll(rbGlobal, new HBox(6, rbVenv), venvRow, installBtn);
        targetBox.setStyle(OpbStyle.card() + " -fx-padding: 14;");
        targetBox.setVisible(false);
        targetBox.setManaged(false);

        // ④ 日志
        progress.setPrefHeight(6);
        progress.setVisible(false);
        logArea.setEditable(false);
        logArea.setStyle("-fx-font-family: monospace; -fx-font-size: 11px;");
        logArea.setVisible(false);
        logArea.setManaged(false);

        installBtn.setOnAction(e -> startInstall());

        VBox content = new VBox(10, selectBox, previewBox, targetBox, progress, logArea);
        content.setFillWidth(true);
        getChildren().addAll(header, content);
    }

    private void detectEnv() {
        detection = PythonDetector.detect(null);
        if (detection.ok()) {
            PlatformMatcher.HostTags host = PlatformMatcher.detectHost(detection.pythonVersion());
            envLabel.setText("检测到本机: " + host.os() + " · " + host.arch()
                    + " · Python " + detection.pythonVersion()
                    + "  @ " + detection.executable());
        } else {
            envLabel.setText(host.i18n().get("opb.deploy.noPython"));
            installBtn.setDisable(true);
        }
    }

    private void chooseZip() {
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("ZIP", "*.zip"));
        var f = fc.showOpenDialog(getScene().getWindow());
        if (f == null) return;
        try {
            selectedZip = f.toPath();
            BundleReader.Bundle b = BundleReader.read(selectedZip);
            PythonDetector.Detection det = PythonDetector.detect(null);
            if (!det.ok()) { host.notifications().toast(this, SkNotification.Type.WARNING, host.i18n().get("opb.deploy.noPython")); return; }
            PlatformMatcher.HostTags host = PlatformMatcher.detectHost(det.pythonVersion());
            matchedWheels = PlatformMatcher.match(host, b.wheels());
            matchTable.getItems().setAll(matchedWheels);
            int incompat = b.wheels().size() - matchedWheels.size();
            summary.setText("将安装 " + matchedWheels.size() + " 个包(适配本机)" +
                    (incompat > 0 ? " · " + incompat + " 个不兼容(已隐藏)" : ""));
            previewBox.setVisible(true); previewBox.setManaged(true);
            targetBox.setVisible(true); targetBox.setManaged(true);
        } catch (Exception ex) {
            host.notifications().toast(this, SkNotification.Type.ERROR, host.i18n().get("opb.deploy.invalidZip") + ": " + ex.getMessage());
        }
    }

    private void startInstall() {
        if (selectedZip == null || detection == null || !detection.ok()) return;
        DeployTarget target;
        if (rbVenv.isSelected()) {
            String p = venvPath.getText().trim();
            if (p.isBlank()) { host.notifications().toast(this, SkNotification.Type.WARNING, "请填写虚拟环境路径"); return; }
            target = new DeployTarget.Venv(Path.of(detection.executable()), Path.of(p));
        } else {
            target = new DeployTarget.Global(Path.of(detection.executable()));
        }
        installBtn.setDisable(true);
        progress.setProgress(0);
        progress.setVisible(true);
        logArea.clear();
        logArea.setVisible(true);
        logArea.setManaged(true);

        host.tasks().submit("opb-deploy",
            () -> new DeployService().install(selectedZip, target, line -> {
                log.log(line);
                Platform.runLater(() -> logArea.appendText(line + "\n"));
            }),
            r -> {  // FX thread
                progress.setProgress(1);
                installBtn.setDisable(false);
                if (r.ok()) {
                    host.notifications().toast(this, SkNotification.Type.SUCCESS,
                            java.text.MessageFormat.format(host.i18n().get("opb.deploy.done"), r.installed()));
                } else {
                    host.notifications().toast(this, SkNotification.Type.WARNING,
                            java.text.MessageFormat.format(host.i18n().get("opb.deploy.partial"), r.installed(), r.failed()));
                }
            },
            error -> {  // FX thread
                log.log("ERROR: " + error.getMessage());
                logArea.appendText("ERROR: " + error.getMessage() + "\n");
                host.notifications().toast(this, SkNotification.Type.ERROR, host.i18n().get("opb.deploy.failed"));
                installBtn.setDisable(false);
            });
    }

    @Override public String title() { return host.i18n().get("opb.deploy.title"); }
}
