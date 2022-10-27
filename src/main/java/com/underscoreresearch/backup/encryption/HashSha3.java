package com.underscoreresearch.backup.encryption;

import static com.underscoreresearch.backup.encryption.Hash.encodeBytes;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HashSha3 {
    private final MessageDigest hasher;
    private byte[] hashBytes;

    public HashSha3() {
        try {
            hasher = MessageDigest.getInstance("SHA3-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static String hash(byte[] buffer) {
        HashSha3 hash = new HashSha3();
        hash.addBytes(buffer);
        return hash.getHash();
    }

    public void addBytes(byte[] bytes) {
        hasher.update(bytes);
    }

    public byte[] getHashBytes() {
        if (hashBytes == null) {
            hashBytes = hasher.digest();
        }
        return hashBytes;
    }

    public String getHash() {
        return encodeBytes(getHashBytes());
    }
}
