package plugin.swisskit.hpl.ui;

import fan.summer.api.i18n.I18n;
import fan.summer.api.log.LoggerFactory;
import fan.summer.api.log.PluginLogger;
import fan.summer.api.component.GlassNotification;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
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

/**
 * HappyLearning control panel.
 *
 * <p>Themed exclusively with SwissKitJ {@code -sk-*} design tokens and {@code .sk-*}
 * foundation classes so it reads as a native host surface in both dark and light
 * themes. No inline hex, no custom font family (host's global font stack applies).
 */
public class HappyLearningUi {

    private static final PluginLogger log = LoggerFactory.getLogger(HappyLearningUi.class);

    /** SwissKitJ design tokens (looked-up colors — resolve per-theme on the scene root). */
    private static final String BG       = "-sk-bg";
    private static final String CARD_BG  = "-sk-bg-elevated";
    private static final String BORDER   = "-sk-border";
    private static final String TEXT     = "-sk-text";
    private static final String TEXT_SEC = "-sk-text-secondary";
    private static final String TEXT_DIM = "-sk-text-disabled";
    private static final String ACCENT   = "-sk-accent";
    private static final String SUCCESS  = "-sk-success";
    private static final String WARNING  = "-sk-warning";
    private static final String DANGER   = "-sk-danger";

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
        header.getStyleClass().add("sk-accent-text");
        header.setStyle("-fx-font-size: 15px; -fx-font-weight: bold;");
        header.setMaxWidth(Double.MAX_VALUE);
        header.setAlignment(Pos.CENTER);
        header.setPadding(new Insets(0, 0, 12, 0));

        // ── Config Section ──
        configFilePathField.setEditable(false);
        configFilePathField.getStyleClass().add("sk-field");
        uploadButton.getStyleClass().add("sk-btn-secondary");
        I18n.bind(uploadButton.textProperty(), p + "uploadConfig");

        BoardRow configRow = new BoardRow("CONFIG");
        configRow.setValueNode(configFilePathField, uploadButton);

        // ── PassKey Section ──
        passKeyField.getStyleClass().add("sk-field");
        setPassKeyButton.getStyleClass().add("sk-btn-secondary");
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
        dotIndicator.setAlignment(Pos.CENTER_LEFT);
        dotIndicator.setPadding(new Insets(4, 0, 0, 0));

        // ── Controls ──
        onlyMajorCheckBox.getStyleClass().add("sk-checkbox");
        onlyElectiveCheckBox.getStyleClass().add("sk-checkbox");
        I18n.bind(onlyMajorCheckBox.textProperty(), p + "onlyMajorSubject");
        I18n.bind(onlyElectiveCheckBox.textProperty(), p + "onlyElectiveSubject");

        onlyMajorCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) onlyElectiveCheckBox.setSelected(false);
        });
        onlyElectiveCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) onlyMajorCheckBox.setSelected(false);
        });

        startButton.getStyleClass().add("sk-btn-primary");   // the single primary action
        stopButton.getStyleClass().add("sk-btn-secondary");
        skipButton.getStyleClass().add("sk-btn-secondary");
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
        board.setStyle("-fx-background-color: " + CARD_BG + "; -fx-background-radius: 8;"
                + "-fx-border-color: " + BORDER + "; -fx-border-width: 1; -fx-border-radius: 8;");
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
        keyLabel.getStyleClass().add("sk-t3");
        keyLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: bold;");
        keyLabel.setMinWidth(85);

        Label sep = new Label(" │ ");
        sep.setStyle("-fx-text-fill: " + BORDER + "; -fx-font-size: 11px;");

        bar.setPrefHeight(6);
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.setStyle("-fx-accent: " + ACCENT + ";");
        HBox.setHgrow(bar, Priority.ALWAYS);

        percentLabel.getStyleClass().add("sk-t2");
        percentLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: bold;");
        percentLabel.setMinWidth(40);
        percentLabel.setAlignment(Pos.CENTER_RIGHT);

        detailLabel.getStyleClass().add("sk-t2");
        detailLabel.setStyle("-fx-font-size: 11px;");
        detailLabel.setMinWidth(65);

        HBox barRow = new HBox(8, bar, percentLabel);
        HBox.setHgrow(barRow, Priority.ALWAYS);

        row.getChildren().addAll(keyLabel, sep, barRow, new Label(" "), detailLabel);
        return row;
    }

    private HBox courseRow(String key, Label value) {
        HBox row = new HBox();
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(3, 0, 3, 0));

        Label keyLabel = new Label(key);
        keyLabel.getStyleClass().add("sk-t3");
        keyLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: bold;");
        keyLabel.setMinWidth(85);

        Label sep = new Label(" │ ");
        sep.setStyle("-fx-text-fill: " + BORDER + "; -fx-font-size: 11px;");

        value.getStyleClass().add("sk-t1");
        value.setStyle("-fx-font-size: 12px;");
        HBox.setHgrow(value, Priority.ALWAYS);

        row.getChildren().addAll(keyLabel, sep, value);
        return row;
    }

    private Node divider() {
        Region d = new Region();
        d.setStyle("-fx-background-color: " + BORDER + ";");
        d.setPrefHeight(1);
        VBox.setMargin(d, new Insets(4, 0, 4, 0));
        return d;
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
        statusLabel.getStyleClass().setAll(service.isRunning() ? "sk-success-text" : "sk-t2");
        statusLabel.setStyle("-fx-font-size: 11px;");
    }

    private void refreshDotIndicator() {
        dotIndicator.getChildren().clear();
        if (!service.isRunning()) return;

        Label dot = new Label("●");
        dot.setStyle("-fx-text-fill: " + SUCCESS + "; -fx-font-size: 10px;");
        Label label = new Label(" IN FLIGHT");
        label.getStyleClass().add("sk-success-text");
        label.setStyle("-fx-font-size: 10px;");
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
            GlassNotification.toast(root, GlassNotification.Type.SUCCESS,
                    I18n.get("plugin.hpl.configInstallSuccess") + selected.getName());
        } catch (IOException ex) {
            log.error("Failed to install config: {}", selected.getAbsolutePath(), ex);
            GlassNotification.toast(root, GlassNotification.Type.ERROR,
                    I18n.get("plugin.hpl.configInstallError") + ex.getMessage());
        }
    }

    private void handleSetPassKey() {
        String text = passKeyField.getText();
        if (text.isEmpty()) {
            log.warn("[UI] Passkey is empty");
            showError(I18n.get("plugin.hpl.passkeyEmpty"));
            return;
        }
        service.setKey(text);
        showSuccess(I18n.get("plugin.hpl.passkeySuccess"));
        log.info("[UI] Passkey set successfully");
    }

    private void handleStart() {
        if (service.isRunning()) {
            log.warn("[UI] Start clicked but learning is already running");
            showInfo(I18n.get("plugin.hpl.learningAlreadyRunning"));
            return;
        }

        String key = service.getKey();
        if (key == null || key.isEmpty()) {
            log.warn("[UI] Start clicked but passkey is not set");
            showWarning(I18n.get("plugin.hpl.pleaseSetPasskey"));
            return;
        }

        try {
            ConfigLoader.loadConfig();
        } catch (Exception e) {
            log.error("Failed to load config", e);
            showError(I18n.get("plugin.hpl.failedLoadConfig") + ": " + e.getMessage());
            return;
        }

        String token = WebUtil.getValueFromCookie(key, "m0biletoken");
        if (token == null || token.isEmpty()) {
            log.error("[UI] Token is null or empty");
            showError(I18n.get("plugin.hpl.tokenEmpty"));
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
                showError(I18n.get("plugin.hpl.failedGetPersonInfo"));
                return;
            }
            PeriodDataRU period = resp.getData().getPeriodDataRU();
            service.setGoals(period);
            updateProgressDisplay(period);
        } catch (Exception e) {
            log.error("Failed to initialize progress", e);
            showError(I18n.get("plugin.hpl.failedInitProgress") + ": " + e.getMessage());
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
        boolean on = dot.getStyle().contains(SUCCESS);
        dot.setStyle("-fx-text-fill: " + (on ? CARD_BG : SUCCESS) + "; -fx-font-size: 10px;");
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

    /**
     * Progress-bar fill color, driven by host semantic tokens so it tracks the theme.
     * complete → success, mid → accent, low → text-secondary.
     */
    private String progressColor(double pct) {
        if (pct >= 1.0) return SUCCESS;
        if (pct >= 0.7) return SUCCESS;
        if (pct >= 0.3) return ACCENT;
        return TEXT_SEC;
    }

    // ==================== Helpers ====================

    private void showSuccess(String message) {
        GlassNotification.toast(root, GlassNotification.Type.SUCCESS, message);
    }

    private void showError(String message) {
        GlassNotification.toast(root, GlassNotification.Type.ERROR, message);
    }

    private void showWarning(String message) {
        GlassNotification.toast(root, GlassNotification.Type.WARNING, message);
    }

    private void showInfo(String message) {
        GlassNotification.toast(root, GlassNotification.Type.INFO, message);
    }

    private static class BoardRow extends HBox {
        BoardRow(String key) {
            setAlignment(Pos.CENTER_LEFT);
            setPadding(new Insets(4, 0, 4, 0));
            setSpacing(6);

            Label keyLabel = new Label(key);
            keyLabel.getStyleClass().add("sk-t3");
            keyLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: bold;");
            keyLabel.setMinWidth(85);

            Label sep = new Label(" │ ");
            sep.setStyle("-fx-text-fill: " + BORDER + "; -fx-font-size: 11px;");

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
