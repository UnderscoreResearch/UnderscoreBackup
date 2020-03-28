package com.underscoreresearch.backup.utils;

import java.util.List;

public interface StatusLogger {
    void resetStatus();

    default boolean temporal() {
        return false;
    }

    List<StatusLine> status();
}
