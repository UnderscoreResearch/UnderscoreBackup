package com.underscoreresearch.backup.file;

public interface MapSerializer<K, V> {
    byte[] encodeKey(K k);

    byte[] encodeValue(V v);

    V decodeValue(byte[] data);

    K decodeKey(byte[] data);
}
