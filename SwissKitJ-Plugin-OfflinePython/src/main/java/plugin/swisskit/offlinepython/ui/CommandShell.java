package plugin.swisskit.offlinepython.ui;

import fan.summer.api.i18n.I18n;
import fan.summer.api.theme.Themes;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import plugin.swisskit.offlinepython.infra.PythonDetector;
import plugin.swisskit.offlinepython.ui.panel.BuildPanel;
import plugin.swisskit.offlinepython.ui.panel.DepsPanel;
import plugin.swisskit.offlinepython.ui.panel.DoctorPanel;
import plugin.swisskit.offlinepython.ui.panel.InitPanel;
import plugin.swisskit.offlinepython.ui.panel.VerifyPanel;

import java.util.LinkedHashMap;
import java.util.Map;

public class CommandShell {
    private static final String[] NAV = {"init", "deps", "build", "verify", "doctor"};

    private final BorderPane root = new BorderPane();
    private final StackPane content = new StackPane();
    private final LogConsole logConsole = new LogConsole();
    private final Label pyBadge = new Label();
    private final Map<String, Button> navButtons = new LinkedHashMap<>();
    private String current = "init";
    private BuildPanel buildPanel;

    public CommandShell() {
        root.getStylesheets().add(Themes.commonStylesheetUrl());
        root.setStyle("-fx-background-color: transparent;");

        root.setLeft(buildNav());
        content.setStyle("-fx-background-color: transparent;");
        root.setCenter(content);

        BorderPane bottomBar = new BorderPane();
        bottomBar.setCenter(logConsole);
        root.setBottom(bottomBar);

        BorderPane top = new BorderPane();
        top.setRight(pyBadge);
        root.setTop(top);

        refreshPython();
        select("init");
    }

    private Node buildNav() {
        VBox nav = new VBox(4);
        nav.setPrefWidth(OpbStyle.SIDEBAR_WIDTH);
        nav.setMinWidth(Region.USE_PREF_SIZE);
        nav.setStyle("-fx-padding: 10;");
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("init",   I18n.get("opb.init.title"));
        labels.put("deps",   I18n.get("opb.deps.title"));
        labels.put("build",  I18n.get("opb.build.title"));
        labels.put("verify", I18n.get("opb.verify.title"));
        labels.put("doctor", I18n.get("opb.doctor.title"));
        for (String key : NAV) {
            Button b = new Button(labels.get(key));
            b.setMaxWidth(Double.MAX_VALUE);
            b.setAlignment(Pos.CENTER_LEFT);
            b.setMnemonicParsing(false);
            applyNavStyle(b, key.equals(current), false);
            b.setOnMouseEntered(e -> { if (!key.equals(current)) applyNavStyle(b, false, true); });
            b.setOnMouseExited(e ->  { if (!key.equals(current)) applyNavStyle(b, false, false); });
            b.setOnAction(e -> select(key));
            navButtons.put(key, b);
            nav.getChildren().add(b);
        }
        return nav;
    }

    private void applyNavStyle(Button b, boolean selected, boolean hover) {
        b.setStyle(OpbStyle.navItem(selected, hover) + " -fx-padding: 9 12 9 12;");
    }

    private void select(String key) {
        current = key;
        navButtons.forEach((k, b) -> applyNavStyle(b, k.equals(key), false));
        Node panel = switch (key) {
            case "init"   -> new InitPanel(logConsole);
            case "deps"   -> new DepsPanel(logConsole);
            case "build"  -> buildPanel != null ? buildPanel : (buildPanel = new BuildPanel(logConsole));
            case "verify" -> new VerifyPanel(logConsole);
            case "doctor" -> new DoctorPanel(logConsole);
            default -> new Label("—");
        };
        content.getChildren().setAll(panel);
    }

    public void refreshPython() {
        var d = PythonDetector.detect(null);
        boolean ok = d.ok();
        pyBadge.setText(ok
                ? I18n.get("opb.python.detected", d.pythonVersion(), d.pipVersion() == null ? "?" : d.pipVersion())
                : I18n.get("opb.python.missing"));
        pyBadge.setStyle(OpbStyle.badge(ok));
        if (!ok) content.getChildren().setAll(new PythonInstallGuide(this::refreshPython));
    }

    public Node getView() { return root; }
    public boolean hasRunningTasks() { return buildPanel != null && buildPanel.isRunning(); }
    public void onBackground() {}
    public void onForeground() { refreshPython(); }
    public void onUnload() { if (buildPanel != null) buildPanel.cancel(); }
}
