package com.underscoreresearch.backup.file.implementation;

import java.io.File;

public class LmdbMetadataRepositoryTest extends LockingMetadataRepositoryTest {
    @Override
    protected LockingMetadataRepository createRepository(File tempDir) {
        if (LockingMetadataRepository.getDefaultVersion() == LockingMetadataRepository.LMDB_STORAGE) {
            return new LockingMetadataRepository(tempDir.getPath(), false, 2);
        }
        return null;
    }
}
