package com.underscoreresearch.backup.utils;

import java.util.List;

public class PausedStatusLogger implements ManualStatusLogger {
    public static final PausedStatusLogger INSTANCE = new PausedStatusLogger();
    private static final List<StatusLine> FIXED_LINE = List.of(
            new StatusLine(PausedStatusLogger.class, "PAUSED", "Paused")
    );

    @Override
    public void resetStatus() {

    }

    @Override
    public List<StatusLine> status() {
        return FIXED_LINE;
    }
}
