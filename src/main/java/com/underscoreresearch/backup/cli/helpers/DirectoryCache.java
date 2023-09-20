package com.underscoreresearch.backup.cli.helpers;

import static com.underscoreresearch.backup.utils.LogUtil.debug;

import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import com.google.common.cache.LoadingCache;

@Slf4j
public class DirectoryCache {
    private final Function<Integer, LoadingCache<String, Set<String>>> creator;
    @Getter
    private LoadingCache<String, Set<String>> cache;
    @Getter
    private int cacheSize;

    public DirectoryCache(Function<Integer, LoadingCache<String, Set<String>>> creator) {
        this.creator = creator;
        setCacheSize(10);
    }

    public void setCacheSize(int size) {
        ConcurrentMap<String, Set<String>> oldCache = cache != null ? cache.asMap() : null;
        debug(() -> log.debug("Adjusting directory cache size to {}", size));
        cache = creator.apply(size);
        if (oldCache != null)
            cache.putAll(oldCache);
        cacheSize = size;
    }
}

