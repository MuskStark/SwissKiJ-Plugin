package plugin.swisskit.offlinepython.task;

import javafx.concurrent.Task;
import javafx.concurrent.Worker;

/** Thin wrapper exposing the underlying Task for cancellation + hasRunningTasks. */
public abstract class PluginTask<T> extends Task<T> {
    public boolean isRunningTask() { return getState() == Worker.State.RUNNING; }
}
