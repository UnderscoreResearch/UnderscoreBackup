package com.underscoreresearch.backup.file;

import java.util.Map;
import java.util.stream.Stream;

/**
 * Interface for a closeable sorted key-value map.
 * Extends CloseableMap to provide sorted access to entries.
 *
 * @param <K> the type of keys maintained by this map
 * @param <V> the type of mapped values
 */
public interface CloseableSortedMap<K, V> extends CloseableMap<K, V> {

    /**
     * Returns a read-only stream of all entries in this map in sorted order.
     *
     * @param ascending true for ascending order, false for descending order
     * @return a stream of map entries in the requested order
     */
    Stream<Map.Entry<K, V>> readOnlyEntryStream(boolean ascending);
}
