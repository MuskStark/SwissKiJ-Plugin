package plugin.swisskit.offlinepython.infra;

import fan.summer.api.log.PluginLogger;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.nio.file.NoSuchFileException;

/**
 * 替代 UI 日志控制台的文件日志器:每条日志同时写入项目目录下的
 * {@code .offline-python.log} 和宿主 {@link PluginLogger}(终端/IDE 控制台可见)。
 *
 * <p>线程安全:文件追加用同步块保护。{@link #log(String)} 保持与旧
 * {@code LogConsole.log(String)} 相同的签名,使面板调用方无需改动。
 */
public class OpbLogger {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("HH:mm:ss");
    private final PluginLogger hostLog;

    /**
     * @param hostLog the host-supplied logger (from {@code host.logger(...)}); routes
     *                log lines into the host logging backbone (IDE console/terminal).
     */
    public OpbLogger(PluginLogger hostLog) {
        this.hostLog = hostLog;
    }

    /** 当前日志文件路径;null 时只走宿主 logger(项目未打开时)。 */
    private volatile Path logFile;

    /** 设置日志文件(通常 = projectDir/.offline-python.log)。null = 只走宿主 logger。 */
    public void setLogFile(Path file) {
        this.logFile = file;
    }

    /** 追加一行日志(INFO 级)。 */
    public void log(String line) {
        log("INFO", line);
    }

    /** 追加一行指定级别的日志。 */
    public void log(String level, String line) {
        String ts = LocalTime.now().withNano(0).format(TS);
        String rendered = "[" + ts + "] " + (level.equals("INFO") ? "" : "[" + level + "] ") + line;
        // 1. 宿主 logger(IDE 控制台/终端)
        routeToHost(level, line);
        // 2. 文件追加
        appendToFile(rendered);
    }

    private void routeToHost(String level, String line) {
        switch (level) {
            case "ERROR" -> hostLog.error(line);
            case "WARN"  -> hostLog.warn(line);
            case "DEBUG" -> hostLog.debug(line);
            default      -> hostLog.info(line);
        }
    }

    private synchronized void appendToFile(String rendered) {
        Path f = logFile;
        if (f == null) return;
        try {
            Files.writeString(f, rendered + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (NoSuchFileException ignored) {
            // 父目录可能尚未创建,尝试创建后重试一次
            try {
                Files.createDirectories(f.getParent());
                Files.writeString(f, rendered + System.lineSeparator(),
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException ignored2) { /* best-effort */ }
        } catch (IOException ignored) { /* best-effort:文件不可写不阻塞主流程 */ }
    }
}
