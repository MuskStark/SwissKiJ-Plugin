package plugin.swisskit.keepawake.ui;

import fan.summer.api.i18n.I18n;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.util.Duration;
import plugin.swisskit.keepawake.service.KeepAwakeService;

public class KeepAwakeUi {

    private static final String BG = "#1a1a2e";
    private static final String CARD_BG = "#16213e";
    private static final String ACCENT = "#e2b714";
    private static final String ACCENT_GREEN = "#00e676";
    private static final String ACCENT_RED = "#ff5252";
    private static final String TEXT_DIM = "#8892b0";
    private static final String TEXT_BRIGHT = "#ccd6f6";
    private static final String DIVIDER = "#233554";

    private final KeepAwakeService service = KeepAwakeService.getInstance();
    private final VBox root = new VBox();
    private final Label statusValue = new Label();
    private final Label methodValue = new Label();
    private final Label elapsedValue = new Label("00:00:00");
    private final Button startButton = new Button();
    private final Button stopButton = new Button();
    private final HBox dotIndicator = new HBox(4);
    private Timeline timer;
    private Timeline blinkTimer;

    public KeepAwakeUi() {
        initComponents();
    }

    public Node getView() {
        return root;
    }

    /**
     * Stop UI animations only. The service keeps running in the background.
     */
    public void suspendUi() {
        if (timer != null) { timer.stop(); timer = null; }
        if (blinkTimer != null) { blinkTimer.stop(); blinkTimer = null; }
    }

    /**
     * Resume UI animations, syncing with the singleton service state.
     */
    public void resumeUi() {
        refreshDisplay();
        if (service.isRunning()) {
            startButton.setDisable(true);
            startButton.setOpacity(0.35);
            stopButton.setDisable(false);
            stopButton.setOpacity(1.0);

            timer = new Timeline(new KeyFrame(Duration.seconds(1), e -> updateElapsed()));
            timer.setCycleCount(Timeline.INDEFINITE);
            timer.play();

            blinkTimer = new Timeline(new KeyFrame(Duration.seconds(1), e -> toggleDot()));
            blinkTimer.setCycleCount(Timeline.INDEFINITE);
            blinkTimer.play();
        } else {
            startButton.setDisable(false);
            startButton.setOpacity(1.0);
            stopButton.setDisable(true);
            stopButton.setOpacity(0.35);
        }
    }

    private void initComponents() {
        root.setStyle("-fx-background-color: " + BG + "; -fx-background-radius: 8;");
        root.setPadding(new Insets(20));
        root.setSpacing(0);
        root.setFillWidth(true);

        String p = "plugin.keepawake.";

        // Header
        Label header = new Label("✈  KEEP ALIVE BOARD");
        header.setStyle("-fx-text-fill: " + ACCENT + "; -fx-font-size: 15px;"
                + " -fx-font-weight: bold; -fx-font-family: 'Menlo', 'Consolas', monospace;");
        header.setMaxWidth(Double.MAX_VALUE);
        header.setAlignment(Pos.CENTER);
        header.setPadding(new Insets(0, 0, 12, 0));

        // Status row
        BoardRow statusRow = new BoardRow("STATUS");
        statusValue.setStyle("-fx-font-family: 'Menlo', 'Consolas', monospace; -fx-font-weight: bold;");
        statusRow.setValueNode(statusValue);

        // Method row
        BoardRow methodRow = new BoardRow("METHOD");
        methodValue.setStyle("-fx-font-family: 'Menlo', 'Consolas', monospace;");
        methodRow.setValueNode(methodValue);

        // Elapsed row
        BoardRow elapsedRow = new BoardRow("ELAPSED");
        elapsedValue.setStyle("-fx-font-family: 'Menlo', 'Consolas', monospace; -fx-font-size: 18px;");
        elapsedRow.setValueNode(elapsedValue);

        // Dot indicator
        dotIndicator.setAlignment(Pos.CENTER_LEFT);
        dotIndicator.setPadding(new Insets(8, 0, 0, 0));

        // Board body
        VBox board = new VBox();
        board.setStyle("-fx-background-color: " + CARD_BG + "; -fx-background-radius: 6;");
        board.setPadding(new Insets(14, 16, 14, 16));
        board.setSpacing(2);
        board.getChildren().addAll(statusRow, divider(), methodRow, divider(), elapsedRow, dotIndicator);

        // Buttons
        styleButton(startButton, ACCENT, BG);
        styleButton(stopButton, ACCENT_RED, "#fff");

        I18n.bind(startButton.textProperty(), p + "start");
        I18n.bind(stopButton.textProperty(), p + "stop");

        HBox buttonRow = new HBox(10, startButton, stopButton);
        buttonRow.setAlignment(Pos.CENTER);
        buttonRow.setPadding(new Insets(16, 0, 0, 0));

        root.getChildren().addAll(header, board, buttonRow);

        startButton.setOnAction(e -> handleStart());
        stopButton.setOnAction(e -> handleStop());

        // Sync with singleton state on first creation
        resumeUi();
        I18n.addListener(this::refreshDisplay);
    }

    private void styleButton(Button btn, String bgColor, String textColor) {
        btn.setStyle("-fx-background-color: " + bgColor + ";"
                + " -fx-text-fill: " + textColor + ";"
                + " -fx-font-family: 'Menlo', 'Consolas', monospace;"
                + " -fx-font-size: 12px; -fx-font-weight: bold;"
                + " -fx-background-radius: 4; -fx-padding: 7 24 7 24;"
                + " -fx-cursor: hand;");
        btn.setCursor(Cursor.HAND);
        btn.setOnMouseEntered(e -> btn.setStyle(btn.getStyle().replace("-fx-background-color: " + bgColor,
                "-fx-background-color: derive(" + bgColor + ", 20%)")));
        btn.setOnMouseExited(e -> btn.setStyle(btn.getStyle().replace("-fx-background-color: derive(" + bgColor + ", 20%)",
                "-fx-background-color: " + bgColor)));
    }

    private Node divider() {
        Region d = new Region();
        d.setStyle("-fx-background-color: " + DIVIDER + ";");
        d.setPrefHeight(1);
        d.setPadding(new Insets(4, 0, 4, 0));
        VBox.setMargin(d, new Insets(6, 0, 6, 0));
        return d;
    }

    private void handleStart() {
        service.start();
        resumeUi();
    }

    private void handleStop() {
        stopInternal();
    }

    private void stopInternal() {
        service.stop();
        suspendUi();

        startButton.setDisable(false);
        startButton.setOpacity(1.0);
        stopButton.setDisable(true);
        stopButton.setOpacity(0.35);

        refreshDisplay();
    }

    private void updateElapsed() {
        long elapsed = System.currentTimeMillis() - service.getStartTimeMs();
        long s = (elapsed / 1000) % 60;
        long m = (elapsed / 60_000) % 60;
        long h = elapsed / 3_600_000;
        elapsedValue.setText(String.format("%02d:%02d:%02d", h, m, s));
    }

    private void toggleDot() {
        if (dotIndicator.getChildren().isEmpty()) return;
        Label dot = (Label) dotIndicator.getChildren().getFirst();
        boolean on = dot.getStyle().contains(ACCENT_GREEN);
        dot.setStyle("-fx-text-fill: " + (on ? CARD_BG : ACCENT_GREEN) + "; -fx-font-size: 10px;");
    }

    private void refreshDisplay() {
        String p = "plugin.keepawake.";
        dotIndicator.getChildren().clear();

        if (!service.isRunning()) {
            statusValue.setText(I18n.get(p + "stopped"));
            statusValue.setStyle("-fx-text-fill: " + ACCENT_RED + ";"
                    + " -fx-font-family: 'Menlo', 'Consolas', monospace; -fx-font-weight: bold;");
            methodValue.setText(I18n.get(p + "methodNone"));
            methodValue.setStyle("-fx-text-fill: " + TEXT_DIM + ";"
                    + " -fx-font-family: 'Menlo', 'Consolas', monospace;");
            elapsedValue.setText("00:00:00");
            return;
        }

        statusValue.setText(I18n.get(p + "running"));
        statusValue.setStyle("-fx-text-fill: " + ACCENT_GREEN + ";"
                + " -fx-font-family: 'Menlo', 'Consolas', monospace; -fx-font-weight: bold;");

        String methodKey = service.isSystemApi() ? p + "methodSystem" : p + "methodMouse";
        methodValue.setText(I18n.get(methodKey));
        methodValue.setStyle("-fx-text-fill: " + TEXT_BRIGHT + ";"
                + " -fx-font-family: 'Menlo', 'Consolas', monospace;");

        Label dot = new Label("●");
        dot.setStyle("-fx-text-fill: " + ACCENT_GREEN + "; -fx-font-size: 10px;");
        Label dotLabel = new Label(" ACTIVE");
        dotLabel.setStyle("-fx-text-fill: " + ACCENT_GREEN + "; -fx-font-family: 'Menlo', 'Consolas', monospace; -fx-font-size: 10px;");
        dotIndicator.getChildren().addAll(dot, dotLabel);

        updateElapsed();
    }

    private static class BoardRow extends HBox {
        private final Label valueNode;

        BoardRow(String key) {
            setAlignment(Pos.CENTER_LEFT);
            setPadding(new Insets(6, 0, 6, 0));
            setSpacing(0);

            Label keyLabel = new Label(key);
            keyLabel.setStyle("-fx-text-fill: " + TEXT_DIM + ";"
                    + " -fx-font-family: 'Menlo', 'Consolas', monospace;"
                    + " -fx-font-size: 11px; -fx-font-weight: bold;");
            keyLabel.setMinWidth(90);

            Label sep = new Label(" │ ");
            sep.setStyle("-fx-text-fill: " + DIVIDER + "; -fx-font-family: monospace; -fx-font-size: 11px;");

            this.valueNode = new Label("—");
            getChildren().addAll(keyLabel, sep, this.valueNode);
        }

        void setValueNode(Label node) {
            getChildren().set(2, node);
        }
    }
}
