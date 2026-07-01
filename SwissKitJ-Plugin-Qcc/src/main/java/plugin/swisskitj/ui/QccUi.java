package plugin.swisskitj.ui;

import fan.summer.api.component.GlassNotification;
import fan.summer.api.i18n.I18n;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import plugin.swisskitj.QccWorker;

import java.io.File;
import java.nio.file.Path;

/**
 * Qichacha CSV → styled Excel converter UI.
 *
 * <p>Themed exclusively with SwissKitJ {@code -sk-*} design tokens and {@code .sk-*}
 * foundation classes so it reads as a native host surface in both dark and light
 * themes. No inline hex, no custom font family (host's global font stack applies).
 */
public class QccUi {

    /** SwissKitJ design tokens (looked-up colors — resolve per-theme on the scene root). */
    private static final String BG       = "-sk-bg";
    private static final String CARD_BG  = "-sk-bg-elevated";
    private static final String BORDER   = "-sk-border";
    private static final String TEXT     = "-sk-text";
    private static final String TEXT_SEC = "-sk-text-secondary";
    private static final String TEXT_DIM = "-sk-text-disabled";
    private static final String ACCENT   = "-sk-accent";
    private static final String SUCCESS  = "-sk-success";
    private static final String DANGER   = "-sk-danger";

    private final VBox root = new VBox();
    private final TextField sourceField = new TextField();
    private final TextField outputField = new TextField();
    private final Button selectSourceBtn = new Button();
    private final Button selectOutputBtn = new Button();
    private final Button convertBtn = new Button();
    private final ProgressBar progressBar = new ProgressBar(0);
    private final Label statusLabel = new Label();
    private final Label progressPercent = new Label("0%");
    private QccWorker activeWorker;

    public QccUi() {
        initComponents();
    }

    public Node getView() {
        return root;
    }

    public boolean isRunning() {
        return activeWorker != null && activeWorker.isRunning();
    }

    public void cancel() {
        if (activeWorker != null && activeWorker.isRunning()) {
            activeWorker.cancel(true);
        }
    }

    private void initComponents() {
        root.setStyle("-fx-background-color: " + BG + "; -fx-background-radius: 8;");
        root.setPadding(new Insets(20));
        root.setSpacing(0);
        root.setFillWidth(true);

        String p = "plugin.qcc.";

        // ── Header ──
        Label header = new Label("✈  QCC DATA BOARD");
        header.getStyleClass().add("sk-accent-text");
        header.setStyle("-fx-font-size: 15px; -fx-font-weight: bold;");
        header.setMaxWidth(Double.MAX_VALUE);
        header.setAlignment(Pos.CENTER);
        header.setPadding(new Insets(0, 0, 12, 0));

        // ── Source File Row ──
        sourceField.setEditable(false);
        sourceField.getStyleClass().add("sk-field");
        selectSourceBtn.getStyleClass().add("sk-btn-secondary");
        I18n.bind(selectSourceBtn.textProperty(), p + "choiceFile");

        HBox sourceRow = buildRow("SOURCE ", sourceField, selectSourceBtn);

        // ── Output Dir Row ──
        outputField.setEditable(false);
        outputField.getStyleClass().add("sk-field");
        selectOutputBtn.getStyleClass().add("sk-btn-secondary");
        I18n.bind(selectOutputBtn.textProperty(), p + "choiceOutPutPath");

        HBox outputRow = buildRow("OUTPUT ", outputField, selectOutputBtn);

        // ── Progress Section ──
        HBox progressRow = new HBox();
        progressRow.setAlignment(Pos.CENTER_LEFT);
        progressRow.setPadding(new Insets(4, 0, 4, 0));

        Label progressKey = buildKeyLabel("PROGRESS");
        Label sep1 = buildSep();

        progressBar.setPrefHeight(6);
        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressBar.setStyle("-fx-accent: " + ACCENT + ";");
        HBox.setHgrow(progressBar, Priority.ALWAYS);

        progressPercent.getStyleClass().add("sk-t2");
        progressPercent.setStyle("-fx-font-size: 11px; -fx-font-weight: bold;");
        progressPercent.setMinWidth(40);
        progressPercent.setAlignment(Pos.CENTER_RIGHT);

        HBox barRow = new HBox(8, progressBar, progressPercent);
        HBox.setHgrow(barRow, Priority.ALWAYS);

        progressRow.getChildren().addAll(progressKey, sep1, barRow);

        // ── Status ──
        statusLabel.setText("—");
        statusLabel.getStyleClass().add("sk-t2");
        statusLabel.setStyle("-fx-font-size: 11px;");
        statusLabel.setPadding(new Insets(6, 0, 2, 0));

        // ── Board Card ──
        VBox board = new VBox();
        board.setStyle("-fx-background-color: " + CARD_BG + "; -fx-background-radius: 8;"
                + "-fx-border-color: " + BORDER + "; -fx-border-width: 1; -fx-border-radius: 8;");
        board.setPadding(new Insets(14, 16, 14, 16));
        board.setSpacing(2);
        board.getChildren().addAll(
                sourceRow, divider(),
                outputRow, divider(),
                progressRow, divider(),
                statusLabel
        );

        // ── Convert Button (the single primary action on this screen) ──
        convertBtn.getStyleClass().add("sk-btn-primary");
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
        label.getStyleClass().add("sk-t3");
        label.setStyle("-fx-font-size: 11px; -fx-font-weight: bold;");
        label.setMinWidth(85);
        return label;
    }

    private Label buildSep() {
        Label sep = new Label(" │ ");
        sep.setStyle("-fx-text-fill: " + BORDER + "; -fx-font-size: 11px;");
        return sep;
    }

    private Node divider() {
        Region d = new Region();
        d.setStyle("-fx-background-color: " + BORDER + ";");
        d.setPrefHeight(1);
        VBox.setMargin(d, new Insets(5, 0, 5, 0));
        return d;
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
            GlassNotification.toast(root, GlassNotification.Type.WARNING,
                    I18n.get("plugin.qcc.selectSourceWarning"));
            return;
        }
        if (outputDir.isEmpty()) {
            GlassNotification.toast(root, GlassNotification.Type.WARNING,
                    I18n.get("plugin.qcc.selectOutputWarning"));
            return;
        }

        String outputPath = Path.of(outputDir, "result.xlsx").toString();

        convertBtn.setDisable(true);
        progressBar.setProgress(0);
        progressPercent.setText("0%");
        statusLabel.setText(I18n.get("plugin.qcc.processing"));
        statusLabel.getStyleClass().setAll("sk-accent-text");
        statusLabel.setStyle("-fx-font-size: 11px;");
        progressBar.setStyle("-fx-accent: " + ACCENT + ";");

        activeWorker = new QccWorker(source, outputPath);

        activeWorker.progressProperty().addListener((obs, oldVal, newVal) -> {
            double pct = newVal.doubleValue();
            progressBar.setProgress(pct);
            progressPercent.setText((int) (pct * 100) + "%");
        });

        activeWorker.messageProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) statusLabel.setText(newVal);
        });

        activeWorker.setOnSucceeded(e -> {
            activeWorker = null;
            convertBtn.setDisable(false);
            progressBar.setProgress(1.0);
            progressPercent.setText("100%");
            statusLabel.setText(I18n.get("plugin.qcc.done") + " — " + outputPath);
            statusLabel.getStyleClass().setAll("sk-success-text");
            statusLabel.setStyle("-fx-font-size: 11px;");
            progressBar.setStyle("-fx-accent: " + SUCCESS + ";");
        });

        activeWorker.setOnFailed(e -> {
            Throwable ex = activeWorker.getException();
            activeWorker = null;
            convertBtn.setDisable(false);
            progressBar.setProgress(0);
            progressPercent.setText("ERR");
            String errorMsg = ex != null ? ex.getMessage() : I18n.get("plugin.qcc.error");
            statusLabel.setText(I18n.get("plugin.qcc.error") + ": " + errorMsg);
            statusLabel.getStyleClass().setAll("sk-danger-text");
            statusLabel.setStyle("-fx-font-size: 11px;");
            GlassNotification.toast(root, GlassNotification.Type.ERROR,
                    I18n.get("plugin.qcc.conversionFailed") + ": " + errorMsg);
        });

        Thread t = new Thread(activeWorker, "Qcc-Worker");
        t.setDaemon(true);
        t.start();
    }

}
