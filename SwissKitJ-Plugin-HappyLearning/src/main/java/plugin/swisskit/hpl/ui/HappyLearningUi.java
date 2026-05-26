package plugin.swisskit.hpl.ui;

import fan.summer.api.i18n.I18n;
import fan.summer.api.log.LoggerFactory;
import fan.summer.api.log.PluginLogger;
import fan.summer.api.theme.Themes;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import plugin.swisskit.hpl.dto.PeriodDataRU;
import plugin.swisskit.hpl.dto.UserSearchResp;
import plugin.swisskit.hpl.service.HappyLearningService;
import plugin.swisskit.hpl.util.ConfigLoader;
import plugin.swisskit.hpl.util.WebUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class HappyLearningUi {

    private static final PluginLogger log = LoggerFactory.getLogger(HappyLearningUi.class);

    private static final String BG = "#1a1a2e";
    private static final String CARD_BG = "#16213e";
    private static final String ACCENT = "#e2b714";
    private static final String ACCENT_GREEN = "#00e676";
    private static final String ACCENT_RED = "#ff5252";
    private static final String ACCENT_BLUE = "#42a5f5";
    private static final String TEXT_DIM = "#8892b0";
    private static final String TEXT_BRIGHT = "#ccd6f6";
    private static final String DIVIDER = "#233554";

    private final HappyLearningService service = HappyLearningService.getInstance();
    private final VBox root = new VBox();

    // Config
    private final TextField configFilePathField = new TextField();
    private final Button uploadButton = new Button();

    // PassKey
    private final TextField passKeyField = new TextField();
    private final Button setPassKeyButton = new Button();

    // Progress
    private final ProgressBar majorProgressBar = new ProgressBar(0);
    private final Label majorPercentLabel = new Label("0%");
    private final Label majorDetailLabel = new Label("0/0 h");
    private final ProgressBar electiveProgressBar = new ProgressBar(0);
    private final Label electivePercentLabel = new Label("0%");
    private final Label electiveDetailLabel = new Label("0/0 h");

    // Current course
    private final Label courseNameValue = new Label("—");
    private final Label courseIdValue = new Label("—");
    private final Label courseHoursValue = new Label("—");

    // Controls
    private final Button startButton = new Button();
    private final Button stopButton = new Button();
    private final Button skipButton = new Button();
    private final CheckBox onlyMajorCheckBox = new CheckBox();
    private final CheckBox onlyElectiveCheckBox = new CheckBox();

    // Status
    private final Label statusLabel = new Label();
    private final HBox dotIndicator = new HBox(4);

    // Timers
    private Timeline progressTimeline;
    private Timeline blinkTimer;

    public HappyLearningUi() {
        initComponents();
    }

    public Node getView() {
        return root;
    }

    public void suspendUi() {
        if (progressTimeline != null) { progressTimeline.stop(); progressTimeline = null; }
        if (blinkTimer != null) { blinkTimer.stop(); blinkTimer = null; }
    }

    public void resumeUi() {
        syncUiState();
        if (service.isRunning()) {
            startProgressPolling();
            startBlink();
        }
    }

    private void initComponents() {
        root.setStyle("-fx-background-color: " + BG + "; -fx-background-radius: 8;");
        root.setPadding(new Insets(18));
        root.setSpacing(0);
        root.setFillWidth(true);

        String p = "plugin.hpl.";

        // ── Header ──
        Label header = new Label("✈  HAPPY LEARNING BOARD");
        header.setStyle("-fx-text-fill: " + ACCENT + "; -fx-font-size: 14px;"
                + " -fx-font-weight: bold; -fx-font-family: 'Menlo', 'Consolas', monospace;");
        header.setMaxWidth(Double.MAX_VALUE);
        header.setAlignment(Pos.CENTER);
        header.setPadding(new Insets(0, 0, 10, 0));

        // ── Config Section ──
        configFilePathField.setEditable(false);
        styleTextField(configFilePathField);
        styleSmallButton(uploadButton, ACCENT);
        I18n.bind(uploadButton.textProperty(), p + "uploadConfig");

        BoardRow configRow = new BoardRow("CONFIG");
        configRow.setValueNode(configFilePathField, uploadButton);

        // ── PassKey Section ──
        styleTextField(passKeyField);
        styleSmallButton(setPassKeyButton, ACCENT);
        I18n.bind(setPassKeyButton.textProperty(), p + "setPassKey");

        BoardRow passKeyRow = new BoardRow("PASSKEY");
        passKeyRow.setValueNode(passKeyField, setPassKeyButton);

        // ── Progress Section ──
        VBox progressBox = new VBox(4);
        progressBox.setPadding(new Insets(4, 0, 4, 0));

        progressBox.getChildren().addAll(
                buildProgressRow("MAJOR  ", majorProgressBar, majorPercentLabel, majorDetailLabel),
                divider(),
                buildProgressRow("ELECTIVE", electiveProgressBar, electivePercentLabel, electiveDetailLabel)
        );

        // ── Current Course Section ──
        VBox courseBox = new VBox(2);
        courseBox.setPadding(new Insets(2, 0, 2, 0));
        courseBox.getChildren().addAll(
                courseRow("COURSE ", courseNameValue),
                courseRow("ID     ", courseIdValue),
                courseRow("HOURS  ", courseHoursValue)
        );

        // ── Status + Dot Indicator ──
        statusLabel.setStyle("-fx-font-family: 'Menlo', 'Consolas', monospace; -fx-font-size: 11px;");
        dotIndicator.setAlignment(Pos.CENTER_LEFT);
        dotIndicator.setPadding(new Insets(4, 0, 0, 0));

        // ── Controls ──
        styleCheckBox(onlyMajorCheckBox);
        styleCheckBox(onlyElectiveCheckBox);
        I18n.bind(onlyMajorCheckBox.textProperty(), p + "onlyMajorSubject");
        I18n.bind(onlyElectiveCheckBox.textProperty(), p + "onlyElectiveSubject");

        onlyMajorCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) onlyElectiveCheckBox.setSelected(false);
        });
        onlyElectiveCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) onlyMajorCheckBox.setSelected(false);
        });

        styleButton(startButton, ACCENT, BG);
        styleButton(stopButton, ACCENT_RED, "#fff");
        styleButton(skipButton, TEXT_DIM, BG);
        I18n.bind(startButton.textProperty(), p + "startHappy");
        I18n.bind(stopButton.textProperty(), p + "unHappy");
        I18n.bind(skipButton.textProperty(), p + "skipClass");

        HBox checkboxRow = new HBox(14, onlyMajorCheckBox, onlyElectiveCheckBox);
        checkboxRow.setAlignment(Pos.CENTER);

        HBox buttonRow = new HBox(8, startButton, stopButton, skipButton);
        buttonRow.setAlignment(Pos.CENTER);

        VBox controlBox = new VBox(6, checkboxRow, buttonRow);
        controlBox.setAlignment(Pos.CENTER);
        controlBox.setPadding(new Insets(8, 0, 0, 0));

        // ── Assemble board card ──
        VBox board = new VBox();
        board.setStyle("-fx-background-color: " + CARD_BG + "; -fx-background-radius: 6;");
        board.setPadding(new Insets(12, 14, 12, 14));
        board.setSpacing(2);
        board.getChildren().addAll(
                configRow, divider(),
                passKeyRow, divider(),
                progressBox, divider(),
                courseBox, divider(),
                statusLabel, dotIndicator
        );

        root.getChildren().addAll(header, board, controlBox);

        // ── Button actions ──
        uploadButton.setOnAction(e -> handleUpload());
        setPassKeyButton.setOnAction(e -> handleSetPassKey());
        startButton.setOnAction(e -> handleStart());
        stopButton.setOnAction(e -> handleStop());
        skipButton.setOnAction(e -> handleSkip());

        // ── Check existing config ──
        Path configFile = Path.of(ConfigLoader.CONFIG_DIR, "netschool-headers.json");
        if (configFile.toFile().exists()) {
            configFilePathField.setText(configFile.toAbsolutePath().toString());
        }

        // ── Sync with singleton state ──
        syncUiState();
        I18n.addListener(this::refreshStatusLabel);
        refreshStatusLabel();
    }

    // ==================== UI Builders ====================

    private HBox buildProgressRow(String key, ProgressBar bar, Label percentLabel, Label detailLabel) {
        HBox row = new HBox();
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(4, 0, 4, 0));

        Label keyLabel = new Label(key);
        keyLabel.setStyle("-fx-text-fill: " + TEXT_DIM + ";"
                + " -fx-font-family: 'Menlo', 'Consolas', monospace;"
                + " -fx-font-size: 11px; -fx-font-weight: bold;");
        keyLabel.setMinWidth(85);

        Label sep = new Label(" │ ");
        sep.setStyle("-fx-text-fill: " + DIVIDER + "; -fx-font-family: monospace; -fx-font-size: 11px;");

        bar.setPrefHeight(12);
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.setStyle("-fx-accent: " + ACCENT_BLUE + ";");
        HBox.setHgrow(bar, Priority.ALWAYS);

        percentLabel.setStyle("-fx-font-family: 'Menlo', 'Consolas', monospace;"
                + " -fx-font-size: 10px; -fx-font-weight: bold;"
                + " -fx-text-fill: " + ACCENT + ";");
        percentLabel.setMinWidth(36);
        percentLabel.setAlignment(Pos.CENTER_RIGHT);

        detailLabel.setStyle("-fx-font-family: 'Menlo', 'Consolas', monospace;"
                + " -fx-font-size: 10px; -fx-text-fill: " + TEXT_DIM + ";");
        detailLabel.setMinWidth(65);

        StackPane barOverlay = new StackPane(bar, percentLabel);
        percentLabel.setStyle("-fx-font-size: 10px; -fx-font-weight: bold;"
                + " -fx-text-fill: white; -fx-effect: dropshadow(gaussian, black, 1, 0.8, 0, 0);");
        HBox.setHgrow(barOverlay, Priority.ALWAYS);

        row.getChildren().addAll(keyLabel, sep, barOverlay, new Label(" "), detailLabel);
        return row;
    }

    private HBox courseRow(String key, Label value) {
        HBox row = new HBox();
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(3, 0, 3, 0));

        Label keyLabel = new Label(key);
        keyLabel.setStyle("-fx-text-fill: " + TEXT_DIM + ";"
                + " -fx-font-family: 'Menlo', 'Consolas', monospace;"
                + " -fx-font-size: 11px; -fx-font-weight: bold;");
        keyLabel.setMinWidth(85);

        Label sep = new Label(" │ ");
        sep.setStyle("-fx-text-fill: " + DIVIDER + "; -fx-font-family: monospace; -fx-font-size: 11px;");

        value.setStyle("-fx-text-fill: " + TEXT_BRIGHT + ";"
                + " -fx-font-family: 'Menlo', 'Consolas', monospace; -fx-font-size: 12px;");
        HBox.setHgrow(value, Priority.ALWAYS);

        row.getChildren().addAll(keyLabel, sep, value);
        return row;
    }

    private Node divider() {
        Region d = new Region();
        d.setStyle("-fx-background-color: " + DIVIDER + ";");
        d.setPrefHeight(1);
        VBox.setMargin(d, new Insets(4, 0, 4, 0));
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
                + " -fx-background-radius: 4; -fx-padding: 6 20 6 20;");
        btn.setCursor(Cursor.HAND);
    }

    private void styleCheckBox(CheckBox cb) {
        cb.setStyle("-fx-text-fill: " + TEXT_DIM + ";"
                + " -fx-font-family: 'Menlo', 'Consolas', monospace; -fx-font-size: 10px;");
    }

    // ==================== State Sync ====================

    private void syncUiState() {
        if (service.isRunning()) {
            startButton.setDisable(true);
            stopButton.setDisable(false);
            skipButton.setDisable(false);
        } else {
            startButton.setDisable(false);
            stopButton.setDisable(true);
            skipButton.setDisable(true);
        }
        refreshStatusLabel();
        refreshDotIndicator();

        // Restore key if set
        if (service.getKey() != null) {
            passKeyField.setText(service.getKey());
        }
    }

    private void refreshStatusLabel() {
        String statusText = I18n.get("plugin.hpl.learningStatus") + ": " + I18n.get(service.getCurrentStatusKey());
        statusLabel.setText(statusText);
        statusLabel.setStyle("-fx-font-family: 'Menlo', 'Consolas', monospace; -fx-font-size: 11px;"
                + " -fx-text-fill: " + (service.isRunning() ? ACCENT_GREEN : TEXT_DIM) + ";");
    }

    private void refreshDotIndicator() {
        dotIndicator.getChildren().clear();
        if (!service.isRunning()) return;

        Label dot = new Label("●");
        dot.setStyle("-fx-text-fill: " + ACCENT_GREEN + "; -fx-font-size: 10px;");
        Label label = new Label(" IN FLIGHT");
        label.setStyle("-fx-text-fill: " + ACCENT_GREEN + "; -fx-font-family: 'Menlo', 'Consolas', monospace; -fx-font-size: 10px;");
        dotIndicator.getChildren().addAll(dot, label);
    }

    // ==================== Event Handlers ====================

    private void handleUpload() {
        File configDir = Path.of(ConfigLoader.CONFIG_DIR).toFile();
        configDir.mkdirs();

        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Config Files (*.json)", "*.json"));
        fileChooser.setTitle(I18n.get("plugin.hpl.selectConfigFile"));

        File selected = fileChooser.showOpenDialog(root.getScene().getWindow());
        if (selected == null) return;

        configFilePathField.setText(selected.getAbsolutePath());

        try {
            Path source = selected.toPath();
            Path target = configDir.toPath().resolve(source.toFile().getName());
            log.debug("Installing config from {} to {}", source, target);
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            log.info("Successfully installed config: {}", source.toFile().getName());
            showAlert(Alert.AlertType.INFORMATION,
                    I18n.get("plugin.hpl.configInstallSuccess") + selected.getName());
        } catch (IOException ex) {
            log.error("Failed to install config: {}", selected.getAbsolutePath(), ex);
            showAlert(Alert.AlertType.ERROR,
                    I18n.get("plugin.hpl.configInstallError") + ex.getMessage());
        }
    }

    private void handleSetPassKey() {
        String text = passKeyField.getText();
        if (text.isEmpty()) {
            log.warn("[UI] Passkey is empty");
            showAlert(Alert.AlertType.ERROR, I18n.get("plugin.hpl.passkeyEmpty"));
            return;
        }
        service.setKey(text);
        showAlert(Alert.AlertType.INFORMATION, I18n.get("plugin.hpl.passkeySuccess"));
        log.info("[UI] Passkey set successfully");
    }

    private void handleStart() {
        if (service.isRunning()) {
            log.warn("[UI] Start clicked but learning is already running");
            showAlert(Alert.AlertType.INFORMATION, I18n.get("plugin.hpl.learningAlreadyRunning"));
            return;
        }

        String key = service.getKey();
        if (key == null || key.isEmpty()) {
            log.warn("[UI] Start clicked but passkey is not set");
            showAlert(Alert.AlertType.WARNING, I18n.get("plugin.hpl.pleaseSetPasskey"));
            return;
        }

        try {
            ConfigLoader.loadConfig();
        } catch (Exception e) {
            log.error("Failed to load config", e);
            showAlert(Alert.AlertType.ERROR, I18n.get("plugin.hpl.failedLoadConfig") + ": " + e.getMessage());
            return;
        }

        String token = WebUtil.getValueFromCookie(key, "m0biletoken");
        if (token == null || token.isEmpty()) {
            log.error("[UI] Token is null or empty");
            showAlert(Alert.AlertType.ERROR, I18n.get("plugin.hpl.tokenEmpty"));
            return;
        }

        String lessonType;
        if (onlyMajorCheckBox.isSelected()) {
            lessonType = "MajorSubject";
        } else if (onlyElectiveCheckBox.isSelected()) {
            lessonType = "ElectiveSubject";
        } else {
            lessonType = null;
        }

        try {
            UserSearchResp resp = service.getPersonInfo(key, token);
            if (resp == null || resp.getData() == null || resp.getData().getPeriodDataRU() == null) {
                showAlert(Alert.AlertType.ERROR, I18n.get("plugin.hpl.failedGetPersonInfo"));
                return;
            }
            PeriodDataRU period = resp.getData().getPeriodDataRU();
            service.setGoals(period);
            updateProgressDisplay(period);
        } catch (Exception e) {
            log.error("Failed to initialize progress", e);
            showAlert(Alert.AlertType.ERROR, I18n.get("plugin.hpl.failedInitProgress") + ": " + e.getMessage());
            return;
        }

        service.startLearning(lessonType, token);

        startButton.setDisable(true);
        stopButton.setDisable(false);
        skipButton.setDisable(false);

        startProgressPolling();
        startBlink();
        refreshStatusLabel();
        refreshDotIndicator();

        log.info("[UI] Learning task started, lessonType: {}", lessonType);
    }

    private void handleStop() {
        service.stopLearning();
    }

    private void handleSkip() {
        if (service.isRunning()) {
            service.setSkipSignal(true);
        }
    }

    // ==================== Progress Polling ====================

    private void startProgressPolling() {
        stopProgressPolling();
        progressTimeline = new Timeline(new KeyFrame(Duration.seconds(10), e -> {
            pollProgress();
            // Check if task finished while we were away
            if (!service.isRunning()) {
                stopProgressPolling();
                stopBlink();
                syncUiState();
            }
        }));
        progressTimeline.setCycleCount(Timeline.INDEFINITE);
        progressTimeline.play();
    }

    private void stopProgressPolling() {
        if (progressTimeline != null) { progressTimeline.stop(); progressTimeline = null; }
    }

    private void startBlink() {
        stopBlink();
        blinkTimer = new Timeline(new KeyFrame(Duration.seconds(1), e -> toggleDot()));
        blinkTimer.setCycleCount(Timeline.INDEFINITE);
        blinkTimer.play();
    }

    private void stopBlink() {
        if (blinkTimer != null) { blinkTimer.stop(); blinkTimer = null; }
    }

    private void toggleDot() {
        if (dotIndicator.getChildren().isEmpty()) return;
        Label dot = (Label) dotIndicator.getChildren().getFirst();
        boolean on = dot.getStyle().contains(ACCENT_GREEN);
        dot.setStyle("-fx-text-fill: " + (on ? CARD_BG : ACCENT_GREEN) + "; -fx-font-size: 10px;");
    }

    private void pollProgress() {
        String token = service.getToken();
        if (token == null) return;

        Task<Void> pollTask = new Task<>() {
            @Override
            protected Void call() {
                try {
                    UserSearchResp resp = service.getPersonInfo(service.getKey(), token);
                    if (resp == null || resp.getData() == null) return null;

                    PeriodDataRU period = resp.getData().getPeriodDataRU();
                    Long lessonId = service.getCurrentLessonId();
                    String lessonName = service.getCurrentLessonName();
                    Float classHours = service.getClassHours();

                    Platform.runLater(() -> {
                        updateProgressDisplay(period);
                        if (lessonId != null) courseIdValue.setText(String.valueOf(lessonId));
                        if (lessonName != null) courseNameValue.setText(lessonName);
                        if (classHours != null) courseHoursValue.setText(String.valueOf(classHours));
                    });
                } catch (Exception e) {
                    log.error("[UI] Failed to poll progress: {}", e.getMessage());
                }
                return null;
            }
        };
        Thread t = new Thread(pollTask, "HappyLearning-Poll");
        t.setDaemon(true);
        t.start();
    }

    private void updateProgressDisplay(PeriodDataRU period) {
        int majorCurrent = period.getGroupLearningTotal().intValue();
        int electiveCurrent = period.getSelfLearningTotal().intValue();
        int majorGoal = service.getMajorGoal();
        int electiveGoal = service.getElectiveGoal();

        double majorPct = majorGoal > 0 ? (double) majorCurrent / majorGoal : 0;
        majorProgressBar.setProgress(Math.min(majorPct, 1.0));
        majorPercentLabel.setText((int) (majorPct * 100) + "%");
        majorDetailLabel.setText(majorCurrent + "/" + majorGoal + " h");
        majorProgressBar.setStyle("-fx-accent: " + progressColor(majorPct) + ";");

        double electivePct = electiveGoal > 0 ? (double) electiveCurrent / electiveGoal : 0;
        electiveProgressBar.setProgress(Math.min(electivePct, 1.0));
        electivePercentLabel.setText((int) (electivePct * 100) + "%");
        electiveDetailLabel.setText(electiveCurrent + "/" + electiveGoal + " h");
        electiveProgressBar.setStyle("-fx-accent: " + progressColor(electivePct) + ";");
    }

    private String progressColor(double pct) {
        if (pct >= 1.0) return "#4caf50";
        if (pct >= 0.7) return "#66bb6a";
        if (pct >= 0.3) return ACCENT_BLUE;
        return "#5c6bc0";
    }

    // ==================== Helpers ====================

    private void showAlert(Alert.AlertType type, String message) {
        Alert alert = new Alert(type);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.getDialogPane().sceneProperty().addListener((obs, old, scene) -> {
            if (scene != null) Themes.applyTo(scene);
        });
        alert.showAndWait();
    }

    private static class BoardRow extends HBox {
        BoardRow(String key) {
            setAlignment(Pos.CENTER_LEFT);
            setPadding(new Insets(4, 0, 4, 0));
            setSpacing(6);

            Label keyLabel = new Label(key);
            keyLabel.setStyle("-fx-text-fill: " + TEXT_DIM + ";"
                    + " -fx-font-family: 'Menlo', 'Consolas', monospace;"
                    + " -fx-font-size: 11px; -fx-font-weight: bold;");
            keyLabel.setMinWidth(85);

            Label sep = new Label(" │ ");
            sep.setStyle("-fx-text-fill: " + DIVIDER + "; -fx-font-family: monospace; -fx-font-size: 11px;");

            getChildren().addAll(keyLabel, sep);
        }

        void setValueNode(Node... nodes) {
            for (Node n : nodes) {
                if (n instanceof TextField tf) HBox.setHgrow(tf, Priority.ALWAYS);
                getChildren().add(n);
            }
        }
    }
}
