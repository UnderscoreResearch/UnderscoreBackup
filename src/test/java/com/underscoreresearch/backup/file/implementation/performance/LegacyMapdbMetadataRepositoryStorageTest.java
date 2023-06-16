package com.underscoreresearch.backup.file.implementation.performance;

import java.nio.file.Path;

import lombok.extern.slf4j.Slf4j;

import com.underscoreresearch.backup.file.MetadataRepositoryStorage;
import com.underscoreresearch.backup.file.implementation.MapdbMetadataRepositoryStorage;

@Slf4j
public class LegacyMapdbMetadataRepositoryStorageTest extends MetadataRepositoryStoragePerformance {
    @Override
    protected MetadataRepositoryStorage createStorageEngine(Path directory) {
        return new MapdbMetadataRepositoryStorage.Legacy(directory.toString(), false);
    }
}