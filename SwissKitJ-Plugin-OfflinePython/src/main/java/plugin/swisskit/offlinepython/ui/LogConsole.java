package plugin.swisskit.offlinepython.ui;

import javafx.application.Platform;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;

public class LogConsole extends BorderPane {
    private final TextArea area = new TextArea();

    public LogConsole() {
        area.setEditable(false);
        area.setWrapText(true);
        area.setStyle("-fx-control-inner-background: rgba(0,0,0,0.28); -fx-text-fill: rgba(255,255,255,0.85);");
        setCenter(area);
        setPrefHeight(168);
    }

    public void log(String line) {
        String ts = java.time.LocalTime.now().withNano(0).toString();
        Platform.runLater(() -> {
            area.appendText("[" + ts + "] " + line + "\n");
            area.setScrollTop(Double.MAX_VALUE);
        });
    }
}
