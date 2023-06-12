package com.underscoreresearch.backup.file.implementation;

import java.io.File;
import java.io.IOException;

public class UpgradeMetadataRepositoryTest extends LockingMetadataRepositoryTest {
    @Override
    protected LockingMetadataRepository createRepository(File tempDir) {
        if (LockingMetadataRepository.getDefaultVersion() == LockingMetadataRepository.LMDB_STORAGE) {
            return new LockingMetadataRepository(tempDir.getPath(), false, 1);
        }
        return null;
    }

    @Override
    protected void halfwayUpgrade() throws IOException {
        repository.upgradeStorage();
    }
}
