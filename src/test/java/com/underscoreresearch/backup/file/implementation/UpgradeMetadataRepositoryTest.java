package com.underscoreresearch.backup.file.implementation;

import java.io.File;
import java.io.IOException;

public class UpgradeMetadataRepositoryTest extends LockingMetadataRepositoryTest {
    @Override
    protected LockingMetadataRepository createRepository(File tempDir) {
        return new LockingMetadataRepository(tempDir.getPath(), false, LockingMetadataRepository.MAPDB_STORAGE);
    }

    @Override
    protected void halfwayUpgrade() throws IOException {
        repository.forceUpgrade();
    }
}
