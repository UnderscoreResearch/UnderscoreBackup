package com.underscoreresearch.backup.block;

import java.io.IOException;

import com.underscoreresearch.backup.model.BackupFilePart;

public interface FileBlockExtractor {
    byte[] extractPart(BackupFilePart file) throws IOException;

    boolean shouldCache();
}
