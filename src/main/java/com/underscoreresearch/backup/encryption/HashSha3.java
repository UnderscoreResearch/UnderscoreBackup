package com.underscoreresearch.backup.encryption;

import lombok.extern.slf4j.Slf4j;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static com.underscoreresearch.backup.encryption.Hash.encodeBytes;

/**
 * Implementation of hash functionality using SHA3-256 algorithm.
 * Provides methods for creating hashes with the SHA3 algorithm.
 */
@Slf4j
public class HashSha3 {
    private final MessageDigest hasher;
    private byte[] hashBytes;

    /**
     * Creates a new SHA3-256 hash instance.
     * 
     * @throws RuntimeException if the SHA3-256 algorithm is not available
     */
    public HashSha3() {
        try {
            hasher = MessageDigest.getInstance("SHA3-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Creates a hash from a byte array using SHA3-256.
     *
     * @param buffer The bytes to hash
     * @return The hash string in base32 encoding
     */
    public static String hash(byte[] buffer) {
        HashSha3 hash = new HashSha3();
        hash.addBytes(buffer);
        return hash.getHash();
    }

    /**
     * Adds bytes to the hash calculation.
     *
     * @param bytes The bytes to add
     */
    public void addBytes(byte[] bytes) {
        hasher.update(bytes);
    }

    /**
     * Gets the raw hash bytes.
     *
     * @return The hash bytes
     */
    public byte[] getHashBytes() {
        if (hashBytes == null) {
            hashBytes = hasher.digest();
        }
        return hashBytes;
    }

    /**
     * Gets the hash value in base32 encoding.
     *
     * @return The hash string
     */
    public String getHash() {
        return encodeBytes(getHashBytes());
    }
}
