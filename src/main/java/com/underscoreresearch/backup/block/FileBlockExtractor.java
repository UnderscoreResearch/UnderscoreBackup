package com.underscoreresearch.backup.block;

import com.underscoreresearch.backup.model.BackupFilePart;

import java.io.IOException;

public interface FileBlockExtractor {
    byte[] extractPart(BackupFilePart file, byte[] blockData) throws IOException;

    boolean shouldCache();
}
