package com.underscoreresearch.backup.utils;

import java.util.List;

public interface ManualStatusLogger {
    void resetStatus();

    default Type type() {
        return Type.NORMAL;
    }

    default void filterItems(List<StatusLine> lines) {
    }

    List<StatusLine> status();

    enum Type {
        NORMAL,
        LOG,
        PERMANENT
    }
}
