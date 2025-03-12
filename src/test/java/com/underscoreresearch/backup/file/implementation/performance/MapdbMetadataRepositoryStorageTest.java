package com.underscoreresearch.backup.file.implementation.performance;

import com.underscoreresearch.backup.file.MetadataRepositoryStorage;
import com.underscoreresearch.backup.file.implementation.MapdbMetadataRepositoryStorage;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;

@Slf4j
public class MapdbMetadataRepositoryStorageTest extends MetadataRepositoryStoragePerformance {
    @Override
    protected MetadataRepositoryStorage createStorageEngine(Path directory) {
        return new MapdbMetadataRepositoryStorage(directory.toString(), 0, 0, false);
    }
}