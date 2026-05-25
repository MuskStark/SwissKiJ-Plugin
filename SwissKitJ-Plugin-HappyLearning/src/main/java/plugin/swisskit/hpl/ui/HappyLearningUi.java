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

    private final VBox root;
    private final HappyLearningService service = new HappyLearningService();

    // Config section
    private final Label configFileLabel = new Label();
    private final TextField configFilePathField = new TextField();
    private final Button uploadButton = new Button();

    // PassKey section
    private final Label passKeyLabel = new Label();
    private final TextField passKeyField = new TextField();
    private final Button setPassKeyButton = new Button();

    // Progress section
    private final Label majorProgressTitle = new Label();
    private final ProgressBar majorProgressBar = new ProgressBar(0);
    private final Label majorProgressPercent = new Label("0%");
    private final Label majorProgressDetail = new Label("0/0 h");

    private final Label electiveProgressTitle = new Label();
    private final ProgressBar electiveProgressBar = new ProgressBar(0);
    private final Label electiveProgressPercent = new Label("0%");
    private final Label electiveProgressDetail = new Label("0/0 h");

    // Current course card
    private final Label currentCourseTitle = new Label();
    private final Label courseNameValue = new Label("-");
    private final Label courseIdValue = new Label("-");
    private final Label courseHoursValue = new Label("-");
    private final VBox currentCourseCard = new VBox(6);

    // Control section
    private final Button startButton = new Button();
    private final Button stopButton = new Button();
    private final Button skipButton = new Button();
    private final CheckBox onlyMajorCheckBox = new CheckBox();
    private final CheckBox onlyElectiveCheckBox = new CheckBox();

    // Status
    private final Label statusLabel = new Label();

    // State
    private String key;
    private Task<Void> currentTask;
    private Timeline progressTimeline;
    private int majorGoal;
    private int electiveGoal;
    private String currentStatusKey;

    public HappyLearningUi() {
        this.root = new VBox(12);
        initComponents();

        configFilePathField.setEditable(false);
        stopButton.setDisable(true);
        skipButton.setDisable(true);

        Path configFile = Path.of(ConfigLoader.CONFIG_DIR, "netschool-headers.json");
        if (configFile.toFile().exists()) {
            configFilePathField.setText(configFile.toAbsolutePath().toString());
        }

        onlyMajorCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) onlyElectiveCheckBox.setSelected(false);
        });
        onlyElectiveCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) onlyMajorCheckBox.setSelected(false);
        });
    }

    public Node getView() {
        return root;
    }

    private void initComponents() {
        root.setPadding(new Insets(16));
        root.setFillWidth(true);

        String p = "plugin.hpl.";

        // === Config Section ===
        VBox configSection = createSection(
                new Label("⚙"), configFileLabel,
                createInputRow(configFilePathField, uploadButton)
        );

        // === PassKey Section ===
        VBox passKeySection = createSection(
                new Label("🔑"), passKeyLabel,
                createInputRow(passKeyField, setPassKeyButton)
        );

        // === Progress Section ===
        VBox progressSection = new VBox(10);

        Label progressHeader = new Label();
        I18n.bind(progressHeader.textProperty(), p + "progressTitle");
        progressHeader.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        progressSection.getChildren().add(progressHeader);

        // Major progress card
        I18n.bind(majorProgressTitle.textProperty(), p + "majorSubject");
        majorProgressTitle.setStyle("-fx-font-weight: bold;");
        VBox majorCard = createProgressCard(majorProgressTitle, majorProgressBar,
                majorProgressPercent, majorProgressDetail);
        progressSection.getChildren().add(majorCard);

        // Elective progress card
        I18n.bind(electiveProgressTitle.textProperty(), p + "electiveSubject");
        electiveProgressTitle.setStyle("-fx-font-weight: bold;");
        VBox electiveCard = createProgressCard(electiveProgressTitle, electiveProgressBar,
                electiveProgressPercent, electiveProgressDetail);
        progressSection.getChildren().add(electiveCard);

        // === Current Course Card ===
        VBox courseSection = new VBox(8);
        I18n.bind(currentCourseTitle.textProperty(), p + "currentCourse");
        currentCourseTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        courseSection.getChildren().add(currentCourseTitle);

        currentCourseCard.setPadding(new Insets(10, 14, 10, 14));
        currentCourseCard.setStyle("-fx-background-color: derive(-fx-background, -5%);"
                + "-fx-background-radius: 6; -fx-border-color: derive(-fx-background, -15%);"
                + "-fx-border-radius: 6; -fx-border-width: 1;");

        Label nameLabel = new Label();
        I18n.bind(nameLabel.textProperty(), p + "subjectName");
        nameLabel.setStyle("-fx-text-fill: derive(-fx-text-background-color, -30%);");
        courseNameValue.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");

        Label idLabel = new Label();
        I18n.bind(idLabel.textProperty(), p + "subjectId");
        idLabel.setStyle("-fx-text-fill: derive(-fx-text-background-color, -30%);");
        courseIdValue.setStyle("-fx-font-size: 13px;");

        Label hoursLabel = new Label();
        I18n.bind(hoursLabel.textProperty(), p + "classHours");
        hoursLabel.setStyle("-fx-text-fill: derive(-fx-text-background-color, -30%);");
        courseHoursValue.setStyle("-fx-font-size: 13px;");

        currentCourseCard.getChildren().addAll(
                nameRow(nameLabel, courseNameValue),
                nameRow(idLabel, courseIdValue),
                nameRow(hoursLabel, courseHoursValue)
        );
        courseSection.getChildren().add(currentCourseCard);

        // === Control Section ===
        VBox controlSection = new VBox(8);
        controlSection.setAlignment(Pos.CENTER);

        HBox checkboxRow = new HBox(16, onlyMajorCheckBox, onlyElectiveCheckBox);
        checkboxRow.setAlignment(Pos.CENTER);
        controlSection.getChildren().add(checkboxRow);

        HBox buttonRow = new HBox(10, startButton, stopButton, skipButton);
        buttonRow.setAlignment(Pos.CENTER);
        controlSection.getChildren().add(buttonRow);

        // === Status ===
        statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: derive(-fx-text-background-color, -30%);");
        statusLabel.setAlignment(Pos.CENTER);
        statusLabel.setMaxWidth(Double.MAX_VALUE);

        // Assemble
        root.getChildren().addAll(
                configSection,
                new Separator(),
                passKeySection,
                new Separator(),
                progressSection,
                new Separator(),
                courseSection,
                new Separator(),
                controlSection,
                statusLabel
        );

        // Button actions
        uploadButton.setOnAction(e -> handleUpload());
        setPassKeyButton.setOnAction(e -> handleSetPassKey());
        startButton.setOnAction(e -> handleStart());
        stopButton.setOnAction(e -> handleStop());
        skipButton.setOnAction(e -> handleSkip());

        // Bind i18n
        I18n.bind(configFileLabel.textProperty(), p + "configFile");
        I18n.bind(uploadButton.textProperty(), p + "uploadConfig");
        I18n.bind(passKeyLabel.textProperty(), p + "passKey");
        I18n.bind(setPassKeyButton.textProperty(), p + "setPassKey");
        I18n.bind(onlyMajorCheckBox.textProperty(), p + "onlyMajorSubject");
        I18n.bind(onlyElectiveCheckBox.textProperty(), p + "onlyElectiveSubject");
        I18n.bind(startButton.textProperty(), p + "startHappy");
        I18n.bind(stopButton.textProperty(), p + "unHappy");
        I18n.bind(skipButton.textProperty(), p + "skipClass");

        currentStatusKey = p + "idle";
        I18n.addListener(this::refreshStatusLabel);
        refreshStatusLabel();
    }

    private HBox createInputRow(TextField field, Button button) {
        HBox row = new HBox(8, field, button);
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(field, Priority.ALWAYS);
        return row;
    }

    private VBox createSection(Node icon, Label titleLabel, HBox contentRow) {
        VBox section = new VBox(4);
        HBox header = new HBox(6, icon, titleLabel);
        header.setAlignment(Pos.CENTER_LEFT);
        titleLabel.setStyle("-fx-font-weight: bold;");
        section.getChildren().addAll(header, contentRow);
        return section;
    }

    private VBox createProgressCard(Label title, ProgressBar bar,
                                    Label percentLabel, Label detailLabel) {
        VBox card = new VBox(4);
        card.setPadding(new Insets(8, 12, 8, 12));
        card.setStyle("-fx-background-color: derive(-fx-background, -3%);"
                + "-fx-background-radius: 5;");

        HBox titleRow = new HBox(8, title, detailLabel);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(title, Priority.ALWAYS);
        detailLabel.setStyle("-fx-text-fill: derive(-fx-text-background-color, -25%); -fx-font-size: 11px;");

        bar.setMaxWidth(Double.MAX_VALUE);
        bar.setPrefHeight(14);

        HBox barRow = new HBox(bar);
        HBox.setHgrow(bar, Priority.ALWAYS);

        card.getChildren().addAll(titleRow, barRow);
        StackPane percentOverlay = new StackPane(bar, percentLabel);
        percentLabel.setStyle("-fx-font-size: 10px; -fx-font-weight: bold;"
                + "-fx-text-fill: white; -fx-effect: dropshadow(gaussian, black, 1, 0.8, 0, 0);");
        HBox.setHgrow(percentOverlay, Priority.ALWAYS);
        card.getChildren().set(1, percentOverlay);

        return card;
    }

    private HBox nameRow(Label label, Node value) {
        HBox row = new HBox(8, label, value);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private void refreshStatusLabel() {
        statusLabel.setText(I18n.get("plugin.hpl.learningStatus") + ": " + I18n.get(currentStatusKey));
    }

    private void setStatus(String statusKey) {
        this.currentStatusKey = statusKey;
        refreshStatusLabel();
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
        this.key = text;
        showAlert(Alert.AlertType.INFORMATION, I18n.get("plugin.hpl.passkeySuccess"));
        log.info("[UI] Passkey set successfully");
    }

    private void handleStart() {
        if (currentTask != null && !currentTask.isDone()) {
            log.warn("[UI] Start clicked but learning is already running");
            showAlert(Alert.AlertType.INFORMATION, I18n.get("plugin.hpl.learningAlreadyRunning"));
            return;
        }

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
            majorGoal = period.getGroupLearningGoal().intValue();
            electiveGoal = period.getSelfLearningGoal().intValue();
            updateProgressDisplay(period);
        } catch (Exception e) {
            log.error("Failed to initialize progress", e);
            showAlert(Alert.AlertType.ERROR, I18n.get("plugin.hpl.failedInitProgress") + ": " + e.getMessage());
            return;
        }

        String finalLessonType = lessonType;
        String finalToken = token;
        currentTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
                service.autoLearning(finalLessonType, finalToken, key);
                return null;
            }
        };

        currentTask.setOnSucceeded(e -> {
            stopProgressPolling();
            resetButtons();
            setStatus("plugin.hpl.completed");
            pollProgress(finalToken);
            log.info("[UI] Learning task completed successfully");
        });

        currentTask.setOnFailed(e -> {
            stopProgressPolling();
            resetButtons();
            Throwable ex = currentTask.getException();
            log.error("[UI] Learning task failed", ex);
            setStatus("plugin.hpl.error");
            showAlert(Alert.AlertType.ERROR,
                    I18n.get("plugin.hpl.learningFailed") + ": " + (ex != null ? ex.getMessage() : I18n.get("plugin.hpl.unknownError")));
        });

        currentTask.setOnCancelled(e -> {
            stopProgressPolling();
            resetButtons();
            setStatus("plugin.hpl.stopped");
            pollProgress(finalToken);
            log.info("[UI] Learning task cancelled by user");
        });

        // Poll progress every 10 seconds for responsive updates
        progressTimeline = new Timeline(new KeyFrame(Duration.seconds(10), e -> {
            pollProgress(finalToken);
        }));
        progressTimeline.setCycleCount(Timeline.INDEFINITE);
        progressTimeline.play();

        startButton.setDisable(true);
        stopButton.setDisable(false);
        skipButton.setDisable(false);
        setStatus("plugin.hpl.learning");

        Thread thread = new Thread(currentTask, "HappyLearning-Worker");
        thread.setDaemon(true);
        thread.start();

        log.info("[UI] Learning task started, lessonType: {}", lessonType);
    }

    private void handleStop() {
        if (currentTask != null && !currentTask.isDone()) {
            log.info("[UI] Stop button clicked, cancelling task");
            currentTask.cancel(true);
        }
    }

    private void handleSkip() {
        if (currentTask != null && !currentTask.isDone()) {
            service.setSkipSignal(true);
        }
    }

    // ==================== Progress ====================

    private void pollProgress(String token) {
        Task<Void> pollTask = new Task<>() {
            @Override
            protected Void call() {
                try {
                    UserSearchResp resp = service.getPersonInfo(key, token);
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

        double majorPct = majorGoal > 0 ? (double) majorCurrent / majorGoal : 0;
        majorProgressBar.setProgress(majorPct);
        majorProgressPercent.setText((int) (majorPct * 100) + "%");
        majorProgressDetail.setText(majorCurrent + "/" + majorGoal + " h");

        // Color the bar based on completion
        if (majorPct >= 1.0) {
            majorProgressBar.setStyle("-fx-accent: #4caf50;");
        } else if (majorPct >= 0.7) {
            majorProgressBar.setStyle("-fx-accent: #66bb6a;");
        } else if (majorPct >= 0.3) {
            majorProgressBar.setStyle("-fx-accent: #42a5f5;");
        } else {
            majorProgressBar.setStyle(null);
        }

        double electivePct = electiveGoal > 0 ? (double) electiveCurrent / electiveGoal : 0;
        electiveProgressBar.setProgress(electivePct);
        electiveProgressPercent.setText((int) (electivePct * 100) + "%");
        electiveProgressDetail.setText(electiveCurrent + "/" + electiveGoal + " h");

        if (electivePct >= 1.0) {
            electiveProgressBar.setStyle("-fx-accent: #4caf50;");
        } else if (electivePct >= 0.7) {
            electiveProgressBar.setStyle("-fx-accent: #66bb6a;");
        } else if (electivePct >= 0.3) {
            electiveProgressBar.setStyle("-fx-accent: #42a5f5;");
        } else {
            electiveProgressBar.setStyle(null);
        }
    }

    // ==================== Helpers ====================

    private void resetButtons() {
        startButton.setDisable(false);
        stopButton.setDisable(true);
        skipButton.setDisable(true);
    }

    private void stopProgressPolling() {
        if (progressTimeline != null) {
            progressTimeline.stop();
            progressTimeline = null;
        }
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
