package com.underscoreresearch.backup.ui.helpers;

import com.google.common.cache.LoadingCache;
import com.underscoreresearch.backup.manifest.model.BackupDirectory;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

import static com.underscoreresearch.backup.utils.log.LogUtil.debug;

/**
 * Cache for backup directories to improve performance when accessing directory information.
 * This class provides a configurable cache size and manages the loading and caching of
 * directory information.
 */
@Slf4j
public class DirectoryCache {
    private final Function<Integer, LoadingCache<String, BackupDirectory>> creator;
    @Getter
    private LoadingCache<String, BackupDirectory> cache;
    @Getter
    private int cacheSize;

    /**
     * Creates a new directory cache with the specified cache creator function.
     *
     * @param creator A function that creates a loading cache with the specified size
     */
    public DirectoryCache(Function<Integer, LoadingCache<String, BackupDirectory>> creator) {
        this.creator = creator;
        setCacheSize(10);
    }

    /**
     * Sets the size of the directory cache.
     * If a cache already exists, its contents are transferred to the new cache.
     *
     * @param size The new cache size
     */
    public void setCacheSize(int size) {
        ConcurrentMap<String, BackupDirectory> oldCache = cache != null ? cache.asMap() : null;
        debug(() -> log.debug("Adjusting directory cache size to {}", size));
        cache = creator.apply(size);
        if (oldCache != null)
            cache.putAll(oldCache);
        cacheSize = size;
    }
}
