package com.underscoreresearch.backup.block;

import java.io.IOException;

import com.underscoreresearch.backup.model.BackupFile;

public interface FileDownloader {
    void downloadFile(BackupFile source, String destination) throws IOException;

    void shutdown();
}
