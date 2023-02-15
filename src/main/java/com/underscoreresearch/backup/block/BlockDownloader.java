package com.underscoreresearch.backup.block;

import java.io.IOException;

import com.underscoreresearch.backup.model.BackupBlock;
import com.underscoreresearch.backup.model.BackupBlockStorage;

public interface BlockDownloader {
    byte[] downloadBlock(String blockHash, String password) throws IOException;

    byte[] downloadEncryptedBlockStorage(BackupBlock block, BackupBlockStorage storage) throws IOException;

    void shutdown();
}
