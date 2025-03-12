package com.underscoreresearch.backup.block;

import com.underscoreresearch.backup.model.BackupBlock;
import com.underscoreresearch.backup.model.BackupFilePart;

import java.io.IOException;

public interface FileBlockExtractor {
    byte[] extractPart(BackupFilePart file, BackupBlock block, String password) throws IOException;

    long blockSize(BackupFilePart file, byte[] blockData) throws IOException;
}
