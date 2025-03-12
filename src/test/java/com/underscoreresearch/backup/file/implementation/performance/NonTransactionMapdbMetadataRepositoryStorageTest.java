package com.underscoreresearch.backup.file.implementation.performance;

import com.underscoreresearch.backup.file.MetadataRepositoryStorage;
import com.underscoreresearch.backup.file.RepositoryOpenMode;
import com.underscoreresearch.backup.file.implementation.MapdbMetadataRepositoryStorage;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Path;

import static com.underscoreresearch.backup.file.implementation.LockingMetadataRepository.MAPDB_STORAGE_LEAF_STORAGE;

@Slf4j
public class NonTransactionMapdbMetadataRepositoryStorageTest extends MetadataRepositoryStoragePerformance {
    @Override
    protected MetadataRepositoryStorage createStorageEngine(Path directory) {
        return new MapdbMetadataRepositoryStorage(directory.toString(), MAPDB_STORAGE_LEAF_STORAGE, 0, false);
    }

    @Override
    protected void openEngine(MetadataRepositoryStorage storage) throws IOException {
        storage.open(RepositoryOpenMode.WITHOUT_TRANSACTION);
    }
}