package com.underscoreresearch.backup.cli.helpers;

import static com.underscoreresearch.backup.utils.LogUtil.debug;

import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import com.google.common.cache.LoadingCache;
import com.underscoreresearch.backup.manifest.model.BackupDirectory;

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

