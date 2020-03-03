package com.underscoreresearch.backup.encryption;

import lombok.extern.slf4j.Slf4j;

import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.common.io.BaseEncoding;

@Slf4j
public class Hash {
    private final Hasher hasher;
    private String hash;

    public Hash() {
        hasher = Hashing.sha256().newHasher();
    }

    public void addBytes(byte[] bytes) {
        hasher.putBytes(bytes);
    }

    public String getHash() {
        if (hash == null) {
            hash = encodeBytes(hasher.hash().asBytes());
        }
        return hash;
    }

    public static String hash(byte[] buffer) {
        Hash hash = new Hash();
        hash.addBytes(buffer);
        return hash.getHash();
    }

    public static String encodeBytes(byte[] bytes) {
        return BaseEncoding.base32().encode(bytes).replace("=", "");
    }

    public static byte[] decodeBytes(String data) {
        return BaseEncoding.base32().decode(data);
    }
}
