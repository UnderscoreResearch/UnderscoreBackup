package com.underscoreresearch.backup.block;

import com.underscoreresearch.backup.model.BackupBlock;
import com.underscoreresearch.backup.model.BackupBlockStorage;

import java.io.IOException;
import java.util.Set;

public interface BlockDownloader {
    byte[] downloadBlock(BackupBlock block, String password) throws IOException;

    byte[] downloadEncryptedBlockStorage(BackupBlock block, BackupBlockStorage storage, Set<String> available) throws IOException;

    void shutdown();
}
