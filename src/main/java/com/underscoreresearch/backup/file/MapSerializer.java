package com.underscoreresearch.backup.file;

/**
 * Interface for serializing and deserializing map keys and values.
 * Provides methods to encode and decode keys and values for storage.
 *
 * @param <K> the type of keys
 * @param <V> the type of values
 */
public interface MapSerializer<K, V> {
    /**
     * Encodes a key to a byte array.
     *
     * @param k The key to encode
     * @return The encoded key as a byte array
     */
    byte[] encodeKey(K k);

    /**
     * Encodes a value to a byte array.
     *
     * @param v The value to encode
     * @return The encoded value as a byte array
     */
    byte[] encodeValue(V v);

    /**
     * Decodes a value from a byte array.
     *
     * @param data The byte array to decode
     * @return The decoded value
     */
    V decodeValue(byte[] data);

    /**
     * Decodes a key from a byte array.
     *
     * @param data The byte array to decode
     * @return The decoded key
     */
    K decodeKey(byte[] data);
}
