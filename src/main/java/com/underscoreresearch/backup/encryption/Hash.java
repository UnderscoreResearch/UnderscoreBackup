package com.underscoreresearch.backup.encryption;

import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.common.io.BaseEncoding;
import lombok.extern.slf4j.Slf4j;

/**
 * Utility class for hashing and encoding operations.
 * Provides methods for creating hashes, encoding and decoding bytes in different formats.
 */
@Slf4j
public class Hash {
    private final Hasher hasher;
    private String hash;

    /**
     * Creates a new hash instance with SHA-256 algorithm.
     */
    public Hash() {
        hasher = Hashing.sha256().newHasher();
    }

    /**
     * Creates a hash from a byte array.
     *
     * @param buffer The bytes to hash
     * @return The hash string in base32 encoding
     */
    public static String hash(byte[] buffer) {
        Hash hash = new Hash();
        hash.addBytes(buffer);
        return hash.getHash();
    }

    /**
     * Encodes bytes to a base32 string without padding.
     *
     * @param bytes The bytes to encode
     * @return The encoded string
     */
    public static String encodeBytes(byte[] bytes) {
        return BaseEncoding.base32().encode(bytes).replace("=", "");
    }

    /**
     * Decodes a base32 string to bytes.
     *
     * @param data The string to decode
     * @return The decoded bytes
     */
    public static byte[] decodeBytes(String data) {
        return BaseEncoding.base32().decode(data);
    }

    /**
     * Creates a hash from a byte array in base64 encoding.
     *
     * @param buffer The bytes to hash
     * @return The hash string in base64 encoding
     */
    public static String hash64(byte[] buffer) {
        Hash hash = new Hash();
        hash.addBytes(buffer);
        return hash.getHash64();
    }

    /**
     * Encodes bytes to a base64 URL-safe string without padding.
     *
     * @param bytes The bytes to encode
     * @return The encoded string
     */
    public static String encodeBytes64(byte[] bytes) {
        return BaseEncoding.base64Url().encode(bytes).replace("=", "");
    }

    /**
     * Decodes a base64 URL-safe string to bytes.
     *
     * @param bytes The string to decode
     * @return The decoded bytes
     */
    public static byte[] decodeBytes64(String bytes) {
        return BaseEncoding.base64Url().decode(bytes);
    }

    /**
     * Adds bytes to the hash calculation.
     *
     * @param bytes The bytes to add
     */
    public void addBytes(byte[] bytes) {
        hasher.putBytes(bytes);
    }

    /**
     * Gets the hash value in base32 encoding.
     *
     * @return The hash string
     */
    public String getHash() {
        if (hash == null) {
            hash = encodeBytes(hasher.hash().asBytes());
        }
        return hash;
    }

    /**
     * Gets the hash value in base64 URL-safe encoding.
     *
     * @return The hash string
     */
    public String getHash64() {
        if (hash == null) {
            hash = encodeBytes64(hasher.hash().asBytes());
        }
        return hash;
    }

    /**
     * Gets the raw hash bytes.
     *
     * @return The hash bytes
     */
    public byte[] getHashBytes() {
        return hasher.hash().asBytes();
    }
}
