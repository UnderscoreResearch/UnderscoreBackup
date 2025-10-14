package com.underscoreresearch.backup.utils.log;

import com.underscoreresearch.backup.ui.desktop.UIHandler;

import java.io.Closeable;
import java.util.List;

/**
 * A status logger that indicates a paused state in the backup system.
 * This logger creates a status line showing that the system is paused for a specific reason,
 * and registers the pause with the UI handler.
 */
public class PausedStatusLogger implements ManualStatusLogger {
    private final List<StatusLine> lines;

    /**
     * Creates a new PausedStatusLogger with the specified reason for the pause.
     *
     * @param reason The reason why the system is paused
     */
    public PausedStatusLogger(String reason) {
        lines = List.of(new StatusLine(PausedStatusLogger.class, "PAUSED", reason));
    }

    /**
     * Starts a pause in the system, registering it with both the status logger and UI handler.
     * Returns a Closeable that can be used to end the pause.
     *
     * @param reason The reason for the pause
     * @return A Closeable that ends the pause when closed
     */
    public static Closeable startPause(String reason) {
        PausedStatusLogger instance = new PausedStatusLogger(reason);
        StateLogger.addLogger(instance);
        Closeable closeable = UIHandler.registerTask(reason, false);

        return () -> {
            closeable.close();
            StateLogger.removeLogger(instance);
        };
    }

    /**
     * No-op implementation as the paused status should not be reset until explicitly ended.
     */
    @Override
    public void resetStatus() {

    }

    /**
     * Returns the status lines indicating the paused state.
     *
     * @return A list containing a single status line with the pause reason
     */
    @Override
    public List<StatusLine> status() {
        return lines;
    }
}
