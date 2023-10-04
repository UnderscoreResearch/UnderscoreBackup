package com.underscoreresearch.backup.file.implementation;

import java.io.File;

public class MapdbVersionedMetadataRepositoryTest extends LockingMetadataRepositoryTest {
    @Override
    protected LockingMetadataRepository createRepository(File tempDir) {
        return new LockingMetadataRepository(tempDir.getPath(), false, LockingMetadataRepository.MAPDB_STORAGE_VERSIONED);
    }
}
