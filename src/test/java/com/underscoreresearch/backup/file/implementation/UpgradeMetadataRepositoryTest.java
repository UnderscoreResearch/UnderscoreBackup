package com.underscoreresearch.backup.file.implementation;

import java.io.File;
import java.io.IOException;

import org.apache.commons.lang3.SystemUtils;

public class UpgradeMetadataRepositoryTest extends LockingMetadataRepositoryTest {
    @Override
    protected LockingMetadataRepository createRepository(File tempDir) {
        if (SystemUtils.IS_OS_WINDOWS)
            return new LockingMetadataRepository(tempDir.getPath(), false, LockingMetadataRepository.LMDB_STORAGE);
        if (SystemUtils.IS_OS_LINUX)
            return new LockingMetadataRepository(tempDir.getPath(), false, LockingMetadataRepository.LMDB_NON_MAPPING_STORAGE);
        return new LockingMetadataRepository(tempDir.getPath(), false, LockingMetadataRepository.MAPDB_STORAGE);
    }

    @Override
    protected void halfwayUpgrade() throws IOException {
        repository.upgradeStorage();
    }
}
