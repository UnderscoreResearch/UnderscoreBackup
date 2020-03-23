package com.underscoreresearch.backup.block;

import com.underscoreresearch.backup.model.BackupFile;

import java.io.IOException;

public interface FileDownloader {
    void downloadFile(BackupFile source, String destination) throws IOException;

    void shutdown();
}
