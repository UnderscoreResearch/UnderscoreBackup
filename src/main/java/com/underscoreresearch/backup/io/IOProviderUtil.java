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
import static com.underscoreresearch.backup.utils.log.LogUtil.debug;

/**
 * Utility class for IO provider operations.
 * Provides caching and common operations for IO providers.
 */
@Slf4j
public class IOProviderUtil {
    private static final Duration COMMON_TIMEOUT = Duration.ofSeconds(30);
    private static final Map<String, Cache<String, byte[]>> COMMON_CACHE = ImmutableMap.of(
            IDENTITY_MANIFEST_LOCATION, createCache(), PUBLICKEY_FILENAME, createCache()
    );

    /**
     * Create a cache for common files.
     *
     * @return The cache
     */
    private static Cache<String, byte[]> createCache() {
        return CacheBuilder
                .newBuilder()
                .expireAfterWrite(COMMON_TIMEOUT)
                .maximumSize(10)
                .build();
    }

    /**
     * Cache common files for faster access.
     *
     * @param provider The IO provider
     * @param key The key for the data
     * @param data The data to cache
     */
    private static void cacheCommon(IOProvider provider, String key, byte[] data) {
        Cache<String, byte[]> cachedFile = COMMON_CACHE.get(key);
        if (cachedFile != null) {
            synchronized (cachedFile) {
                cachedFile.put(provider.getCacheKey(), data);
                debug(() -> log.debug("Cached for \"{}\" until {}", key, Instant.now().plus(COMMON_TIMEOUT)));
            }
        }
    }

    /**
     * Upload data to a provider with caching.
     *
     * @param provider The IO provider
     * @param suggestedKey The suggested key for the data
     * @param data The data to upload
     * @return The actual key used for the uploaded data
     * @throws IOException If there's an error uploading the data
     */
    public static String upload(IOProvider provider, String suggestedKey, byte[] data) throws IOException {
        String actualKey = provider.upload(suggestedKey, data);
        cacheCommon(provider, suggestedKey, data);
        return actualKey;
    }

    /**
     * Download data from a provider with caching.
     *
     * @param provider The IO provider
     * @param suggestedKey The key for the data to download
     * @return The downloaded data
     * @throws IOException If there's an error downloading the data
     */
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
