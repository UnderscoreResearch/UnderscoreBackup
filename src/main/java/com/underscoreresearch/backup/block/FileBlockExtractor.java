package com.underscoreresearch.backup.block;

import java.io.IOException;

import com.underscoreresearch.backup.model.BackupBlock;
import com.underscoreresearch.backup.model.BackupFilePart;

public interface FileBlockExtractor {
    byte[] extractPart(BackupFilePart file, BackupBlock block, String password) throws IOException;

    long blockSize(BackupFilePart file, byte[] blockData) throws IOException;
}
