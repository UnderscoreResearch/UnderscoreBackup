package com.underscoreresearch.backup.file.implementation;

import java.io.File;

public class LegacyMapdbMetadataRepositoryTest extends LockingMetadataRepositoryTest {
    @Override
    protected LockingMetadataRepository createRepository(File tempDir) {
        return new LockingMetadataRepository(tempDir.getPath(), false, 0);
    }
}
