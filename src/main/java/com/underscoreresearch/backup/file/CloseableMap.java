package com.underscoreresearch.backup.file;

import java.io.Closeable;

/**
 * Interface for a closeable key-value map.
 * Provides basic map operations with automatic resource management
 * through the Closeable interface.
 *
 * @param <K> the type of keys maintained by this map
 * @param <V> the type of mapped values
 */
public interface CloseableMap<K, V> extends Closeable {
    /**
     * Associates the specified value with the specified key in this map.
     *
     * @param k key with which the specified value is to be associated
     * @param v value to be associated with the specified key
     */
    void put(K k, V v);

    /**
     * Removes the mapping for a key from this map if it is present.
     *
     * @param k key whose mapping is to be removed from the map
     * @return true if the mapping was removed, false otherwise
     */
    boolean delete(K k);

    /**
     * Returns the value to which the specified key is mapped,
     * or null if this map contains no mapping for the key.
     *
     * @param k the key whose associated value is to be returned
     * @return the value to which the specified key is mapped, or
     *         null if this map contains no mapping for the key
     */
    V get(K k);

    /**
     * Returns true if this map contains a mapping for the specified key.
     *
     * @param k key whose presence in this map is to be tested
     * @return true if this map contains a mapping for the specified key
     */
    default boolean containsKey(K k) {
        return get(k) != null;
    }
}
