package com.underscoreresearch.backup.utils;

import java.io.Closeable;
import java.util.List;

import com.underscoreresearch.backup.cli.ui.UIHandler;

public class PausedStatusLogger implements ManualStatusLogger {
    private final List<StatusLine> lines;

    public PausedStatusLogger(String reason) {
        lines = List.of(new StatusLine(PausedStatusLogger.class, "PAUSED", reason));
    }

    public static Closeable startPause(String reason) {
        PausedStatusLogger instance = new PausedStatusLogger(reason);
        StateLogger.addLogger(instance);
        Closeable closeable = UIHandler.registerTask(reason);

        return () -> {
            closeable.close();
            StateLogger.removeLogger(instance);
        };
    }

    @Override
    public void resetStatus() {

    }

    @Override
    public List<StatusLine> status() {
        return lines;
    }
}
