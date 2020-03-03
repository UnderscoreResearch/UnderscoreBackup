package com.underscoreresearch.backup.model;

import java.util.List;

public interface BackupBlockCompletion {
    void completed(List<BackupLocation> locations);
}
