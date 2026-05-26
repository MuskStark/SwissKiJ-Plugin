package plugin.swisskitj;

import javafx.concurrent.Task;

public class QccWorker extends Task<Void> {

    private final String inputFile;
    private final String outputFile;

    public QccWorker(String inputFile, String outputFile) {
        this.inputFile = inputFile;
        this.outputFile = outputFile;
    }

    @Override
    protected Void call() throws Exception {
        updateMessage("Processing...");
        updateProgress(0, 1);
        CsvToExcelProcessor.process(inputFile, outputFile);
        updateProgress(1, 1);
        updateMessage("Done");
        return null;
    }
}
