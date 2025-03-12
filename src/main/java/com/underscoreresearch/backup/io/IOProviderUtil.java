package com.underscoreresearch.backup.io;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableMap;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static com.underscoreresearch.backup.manifest.implementation.BaseManifestManagerImpl.IDENTITY_MANIFEST_LOCATION;
import static com.underscoreresearch.backup.manifest.implementation.BaseManifestManagerImpl.PUBLICKEY_FILENAME;
import static com.underscoreresearch.backup.utils.LogUtil.debug;

@Slf4j
public class IOProviderUtil {
    private static final Duration COMMON_TIMEOUT = Duration.ofSeconds(30);
    private static final Map<String, Cache<String, byte[]>> COMMON_CACHE = ImmutableMap.of(
            IDENTITY_MANIFEST_LOCATION, createCache(), PUBLICKEY_FILENAME, createCache()
    );

    private static Cache<String, byte[]> createCache() {
        return CacheBuilder
                .newBuilder()
                .expireAfterWrite(COMMON_TIMEOUT)
                .maximumSize(10)
                .build();
    }

    private static void cacheCommon(IOProvider provider, String key, byte[] data) {
        Cache<String, byte[]> cachedFile = COMMON_CACHE.get(key);
        if (cachedFile != null) {
            synchronized (cachedFile) {
                cachedFile.put(provider.getCacheKey(), data);
                debug(() -> log.debug("Cached for \"{}\" until {}", key, Instant.now().plus(COMMON_TIMEOUT)));
            }
        }
    }

    public static String upload(IOProvider provider, String suggestedKey, byte[] data) throws IOException {
        String actualKey = provider.upload(suggestedKey, data);
        cacheCommon(provider, suggestedKey, data);
        return actualKey;
    }

    public static byte[] download(IOProvider provider, String suggestedKey) throws IOException {
        Cache<String, byte[]> cachedFile = COMMON_CACHE.get(suggestedKey);
        if (cachedFile != null) {
            synchronized (cachedFile) {
                byte[] data = cachedFile.getIfPresent(provider.getCacheKey());
                if (data != null) {
                    debug(() -> log.debug("Using cached data for \"{}\"", suggestedKey));
                    return data;
                }
            }
        }
        byte[] data = provider.download(suggestedKey);
        cacheCommon(provider, suggestedKey, data);
        return data;
    }
}
