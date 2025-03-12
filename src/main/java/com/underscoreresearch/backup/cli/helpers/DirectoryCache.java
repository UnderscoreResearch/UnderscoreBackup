package com.underscoreresearch.backup.cli.helpers;

import com.google.common.cache.LoadingCache;
import com.underscoreresearch.backup.manifest.model.BackupDirectory;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

import static com.underscoreresearch.backup.utils.LogUtil.debug;

@Slf4j
public class DirectoryCache {
    private final Function<Integer, LoadingCache<String, BackupDirectory>> creator;
    @Getter
    private LoadingCache<String, BackupDirectory> cache;
    @Getter
    private int cacheSize;

    public DirectoryCache(Function<Integer, LoadingCache<String, BackupDirectory>> creator) {
        this.creator = creator;
        setCacheSize(10);
    }

    public void setCacheSize(int size) {
        ConcurrentMap<String, BackupDirectory> oldCache = cache != null ? cache.asMap() : null;
        debug(() -> log.debug("Adjusting directory cache size to {}", size));
        cache = creator.apply(size);
        if (oldCache != null)
            cache.putAll(oldCache);
        cacheSize = size;
    }
}

