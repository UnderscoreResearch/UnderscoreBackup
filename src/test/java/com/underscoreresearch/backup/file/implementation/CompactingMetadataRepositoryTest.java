package com.underscoreresearch.backup.file.implementation;

import java.io.File;
import java.io.IOException;

public class CompactingMetadataRepositoryTest extends LockingMetadataRepositoryTest {
    @Override
    protected LockingMetadataRepository createRepository(File tempDir) {
        return new LockingMetadataRepository(tempDir.getPath(), false, LockingMetadataRepository.MAPDB_STORAGE_VERSIONED);
    }

    @Override
    protected void halfwayUpgrade() throws IOException {
        repository.compact();
    }
}
