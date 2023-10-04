package com.underscoreresearch.backup.file;

import java.io.Closeable;

public interface CloseableMap<K, V> extends Closeable {
    void put(K k, V v);

    boolean delete(K k);

    V get(K k);

    default boolean containsKey(K k) {
        return get(k) != null;
    }
}
