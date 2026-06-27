package plugin.swisskit.offlinepython.ui;

import fan.summer.api.i18n.I18n;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import plugin.swisskit.offlinepython.infra.PythonDetector;
import plugin.swisskit.offlinepython.ui.panel.*;

public class CommandShell {
    private final BorderPane root = new BorderPane();
    private final StackPane content = new StackPane();
    private final LogConsole logConsole = new LogConsole();
    private final Label pyBadge = new Label();
    private BuildPanel buildPanel;

    public CommandShell() {
        ListView<String> nav = new ListView<>();
        nav.getItems().addAll("init", "deps", "build", "verify", "doctor");
        nav.getSelectionModel().selectedItemProperty().addListener((o, ov, name) -> switchTo(name));
        root.setLeft(nav);
        content.setStyle("-fx-background-color: transparent");
        root.setCenter(content);
        BorderPane bottomBar = new BorderPane();
        bottomBar.setCenter(logConsole);
        root.setBottom(bottomBar);
        BorderPane top = new BorderPane();
        top.setRight(pyBadge);
        root.setTop(top);
        refreshPython();
        nav.getSelectionModel().selectFirst();
    }

    private void switchTo(String name) {
        Node panel = switch (name) {
            case "init" -> new InitPanel(logConsole);
            case "deps" -> new DepsPanel(logConsole);
            case "build" -> buildPanel != null ? buildPanel : (buildPanel = new BuildPanel(logConsole));
            case "verify" -> new VerifyPanel(logConsole);
            case "doctor" -> new DoctorPanel(logConsole);
            default -> new Label("—");
        };
        content.getChildren().setAll(panel);
    }

    public void refreshPython() {
        var d = PythonDetector.detect(null);
        pyBadge.setText(d.ok()
                ? I18n.get("opb.python.detected", d.pythonVersion(), d.pipVersion() == null ? "?" : d.pipVersion())
                : I18n.get("opb.python.missing"));
        if (!d.ok()) content.getChildren().setAll(new PythonInstallGuide(this::refreshPython));
    }

    public Node getView() { return root; }
    public boolean hasRunningTasks() { return buildPanel != null && buildPanel.isRunning(); }
    public void onBackground() {}
    public void onForeground() { refreshPython(); }
    public void onUnload() { if (buildPanel != null) buildPanel.cancel(); }
}
