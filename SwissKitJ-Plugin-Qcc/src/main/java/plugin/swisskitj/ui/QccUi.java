package plugin.swisskitj.ui;

import fan.summer.api.i18n.I18n;
import fan.summer.api.theme.Themes;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import plugin.swisskitj.QccWorker;

import java.io.File;
import java.nio.file.Path;

public class QccUi {

    private static final String BG = "#1a1a2e";
    private static final String CARD_BG = "#16213e";
    private static final String ACCENT = "#e2b714";
    private static final String ACCENT_GREEN = "#00e676";
    private static final String ACCENT_RED = "#ff5252";
    private static final String TEXT_DIM = "#8892b0";
    private static final String TEXT_BRIGHT = "#ccd6f6";
    private static final String DIVIDER = "#233554";

    private final VBox root = new VBox();
    private final TextField sourceField = new TextField();
    private final TextField outputField = new TextField();
    private final Button selectSourceBtn = new Button();
    private final Button selectOutputBtn = new Button();
    private final Button convertBtn = new Button();
    private final ProgressBar progressBar = new ProgressBar(0);
    private final Label statusLabel = new Label();
    private final Label progressPercent = new Label("0%");

    public QccUi() {
        initComponents();
    }

    public Node getView() {
        return root;
    }

    private void initComponents() {
        root.setStyle("-fx-background-color: " + BG + "; -fx-background-radius: 8;");
        root.setPadding(new Insets(20));
        root.setSpacing(0);
        root.setFillWidth(true);

        String p = "plugin.qcc.";

        // ── Header ──
        Label header = new Label("✈  QCC DATA BOARD");
        header.setStyle("-fx-text-fill: " + ACCENT + "; -fx-font-size: 14px;"
                + " -fx-font-weight: bold; -fx-font-family: 'Menlo', 'Consolas', monospace;");
        header.setMaxWidth(Double.MAX_VALUE);
        header.setAlignment(Pos.CENTER);
        header.setPadding(new Insets(0, 0, 10, 0));

        // ── Source File Row ──
        sourceField.setEditable(false);
        styleTextField(sourceField);
        styleSmallButton(selectSourceBtn, ACCENT);
        I18n.bind(selectSourceBtn.textProperty(), p + "choiceFile");

        HBox sourceRow = buildRow("SOURCE ", sourceField, selectSourceBtn);

        // ── Output Dir Row ──
        outputField.setEditable(false);
        styleTextField(outputField);
        styleSmallButton(selectOutputBtn, ACCENT);
        I18n.bind(selectOutputBtn.textProperty(), p + "choiceOutPutPath");

        HBox outputRow = buildRow("OUTPUT ", outputField, selectOutputBtn);

        // ── Progress Section ──
        HBox progressRow = new HBox();
        progressRow.setAlignment(Pos.CENTER_LEFT);
        progressRow.setPadding(new Insets(4, 0, 4, 0));

        Label progressKey = buildKeyLabel("PROGRESS");
        Label sep1 = buildSep();

        progressBar.setPrefHeight(14);
        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressBar.setStyle("-fx-accent: " + ACCENT_GREEN + ";");
        HBox.setHgrow(progressBar, Priority.ALWAYS);

        progressPercent.setStyle("-fx-font-family: 'Menlo', 'Consolas', monospace;"
                + " -fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: " + ACCENT + ";");
        progressPercent.setMinWidth(40);
        progressPercent.setAlignment(Pos.CENTER_RIGHT);

        StackPane barOverlay = new StackPane(progressBar, progressPercent);
        progressPercent.setStyle("-fx-font-size: 10px; -fx-font-weight: bold;"
                + " -fx-text-fill: white; -fx-effect: dropshadow(gaussian, black, 1, 0.8, 0, 0);");
        HBox.setHgrow(barOverlay, Priority.ALWAYS);

        progressRow.getChildren().addAll(progressKey, sep1, barOverlay);

        // ── Status ──
        statusLabel.setText("—");
        statusLabel.setStyle("-fx-font-family: 'Menlo', 'Consolas', monospace; -fx-font-size: 11px;"
                + " -fx-text-fill: " + TEXT_DIM + ";");
        statusLabel.setPadding(new Insets(6, 0, 2, 0));

        // ── Board Card ──
        VBox board = new VBox();
        board.setStyle("-fx-background-color: " + CARD_BG + "; -fx-background-radius: 6;");
        board.setPadding(new Insets(14, 16, 14, 16));
        board.setSpacing(2);
        board.getChildren().addAll(
                sourceRow, divider(),
                outputRow, divider(),
                progressRow, divider(),
                statusLabel
        );

        // ── Convert Button ──
        styleButton(convertBtn, ACCENT, BG);
        I18n.bind(convertBtn.textProperty(), p + "qccToExcel");

        HBox btnRow = new HBox(convertBtn);
        btnRow.setAlignment(Pos.CENTER);
        btnRow.setPadding(new Insets(14, 0, 0, 0));

        root.getChildren().addAll(header, board, btnRow);

        // ── Actions ──
        selectSourceBtn.setOnAction(e -> handleSelectSource());
        selectOutputBtn.setOnAction(e -> handleSelectOutput());
        convertBtn.setOnAction(e -> handleConvert());
    }

    // ==================== Row Builders ====================

    private HBox buildRow(String key, TextField field, Button btn) {
        HBox row = new HBox();
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(4, 0, 4, 0));
        row.setSpacing(6);

        Label keyLabel = buildKeyLabel(key);
        Label sep = buildSep();
        HBox.setHgrow(field, Priority.ALWAYS);

        row.getChildren().addAll(keyLabel, sep, field, btn);
        return row;
    }

    private Label buildKeyLabel(String key) {
        Label label = new Label(key);
        label.setStyle("-fx-text-fill: " + TEXT_DIM + ";"
                + " -fx-font-family: 'Menlo', 'Consolas', monospace;"
                + " -fx-font-size: 11px; -fx-font-weight: bold;");
        label.setMinWidth(85);
        return label;
    }

    private Label buildSep() {
        Label sep = new Label(" │ ");
        sep.setStyle("-fx-text-fill: " + DIVIDER + "; -fx-font-family: monospace; -fx-font-size: 11px;");
        return sep;
    }

    private Node divider() {
        Region d = new Region();
        d.setStyle("-fx-background-color: " + DIVIDER + ";");
        d.setPrefHeight(1);
        VBox.setMargin(d, new Insets(5, 0, 5, 0));
        return d;
    }

    // ==================== Style Helpers ====================

    private void styleTextField(TextField field) {
        field.setStyle("-fx-background-color: #0f3460; -fx-text-fill: " + TEXT_BRIGHT + ";"
                + " -fx-font-family: 'Menlo', 'Consolas', monospace; -fx-font-size: 11px;"
                + " -fx-background-radius: 3; -fx-padding: 4 8 4 8;"
                + " -fx-border-color: " + DIVIDER + "; -fx-border-radius: 3;");
    }

    private void styleSmallButton(Button btn, String color) {
        btn.setStyle("-fx-background-color: " + color + "; -fx-text-fill: " + BG + ";"
                + " -fx-font-family: 'Menlo', 'Consolas', monospace; -fx-font-size: 10px;"
                + " -fx-font-weight: bold; -fx-background-radius: 3; -fx-padding: 4 12 4 12;");
        btn.setCursor(Cursor.HAND);
    }

    private void styleButton(Button btn, String bgColor, String textColor) {
        btn.setStyle("-fx-background-color: " + bgColor + ";"
                + " -fx-text-fill: " + textColor + ";"
                + " -fx-font-family: 'Menlo', 'Consolas', monospace;"
                + " -fx-font-size: 11px; -fx-font-weight: bold;"
                + " -fx-background-radius: 4; -fx-padding: 7 24 7 24;");
        btn.setCursor(Cursor.HAND);
    }

    // ==================== Event Handlers ====================

    private void handleSelectSource() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18n.get("plugin.qcc.choiceFile"));
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("CSV Files (*.csv)", "*.csv"));
        File selected = chooser.showOpenDialog(root.getScene().getWindow());
        if (selected != null) {
            sourceField.setText(selected.getAbsolutePath());
        }
    }

    private void handleSelectOutput() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(I18n.get("plugin.qcc.choiceOutPutPath"));
        File selected = chooser.showDialog(root.getScene().getWindow());
        if (selected != null) {
            outputField.setText(selected.getAbsolutePath());
        }
    }

    private void handleConvert() {
        String source = sourceField.getText();
        String outputDir = outputField.getText();

        if (source.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Please select a source CSV file.");
            return;
        }
        if (outputDir.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Please select an output directory.");
            return;
        }

        String outputPath = Path.of(outputDir, "result.xlsx").toString();

        convertBtn.setDisable(true);
        progressBar.setProgress(0);
        progressPercent.setText("0%");
        statusLabel.setText("Processing...");
        statusLabel.setStyle("-fx-font-family: 'Menlo', 'Consolas', monospace; -fx-font-size: 11px;"
                + " -fx-text-fill: " + ACCENT + ";");

        QccWorker worker = new QccWorker(source, outputPath);

        worker.progressProperty().addListener((obs, oldVal, newVal) -> {
            double pct = newVal.doubleValue();
            progressBar.setProgress(pct);
            progressPercent.setText((int) (pct * 100) + "%");
        });

        worker.messageProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) statusLabel.setText(newVal);
        });

        worker.setOnSucceeded(e -> {
            convertBtn.setDisable(false);
            progressBar.setProgress(1.0);
            progressPercent.setText("100%");
            statusLabel.setText("Done — " + outputPath);
            statusLabel.setStyle("-fx-font-family: 'Menlo', 'Consolas', monospace; -fx-font-size: 11px;"
                    + " -fx-text-fill: " + ACCENT_GREEN + ";");
            progressBar.setStyle("-fx-accent: #4caf50;");
        });

        worker.setOnFailed(e -> {
            convertBtn.setDisable(false);
            progressBar.setProgress(0);
            progressPercent.setText("ERR");
            Throwable ex = worker.getException();
            statusLabel.setText("Error: " + (ex != null ? ex.getMessage() : "Unknown"));
            statusLabel.setStyle("-fx-font-family: 'Menlo', 'Consolas', monospace; -fx-font-size: 11px;"
                    + " -fx-text-fill: " + ACCENT_RED + ";");
            showAlert(Alert.AlertType.ERROR, "Conversion failed: " + (ex != null ? ex.getMessage() : "Unknown error"));
        });

        Thread t = new Thread(worker, "Qcc-Worker");
        t.setDaemon(true);
        t.start();
    }

    private void showAlert(Alert.AlertType type, String message) {
        Alert alert = new Alert(type);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.getDialogPane().sceneProperty().addListener((obs, old, scene) -> {
            if (scene != null) Themes.applyTo(scene);
        });
        alert.showAndWait();
    }
}
