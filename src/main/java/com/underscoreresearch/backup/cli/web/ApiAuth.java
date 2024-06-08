package com.underscoreresearch.backup.cli.web;

import static com.underscoreresearch.backup.configuration.EncryptionModule.ROOT_KEY;
import static com.underscoreresearch.backup.encryption.EncryptionIdentity.RANDOM;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;

import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.EncryptionIdentity;
import com.underscoreresearch.backup.encryption.Hash;
import com.underscoreresearch.backup.encryption.encryptors.x25519.X25519;
import com.underscoreresearch.backup.model.BackupConfiguration;

public class ApiAuth {
    private static final int MAX_SIZE = 100;
    private static final int IV_SIZE = 16;
    private static final ApiAuth INSTANCE = new ApiAuth();
    private final SortedSet<EndpointInfo> endpointInfoSortedSet = new TreeSet<>();
    private final Map<String, EndpointInfo> endpoints = new HashMap<>();
    private InstanceFactory cachedFactory;
    private boolean needAuthentication;

    private ApiAuth() {
    }

    public static ApiAuth getInstance() {
        return INSTANCE;
    }

    public EncryptedData encryptData(byte[] sharedKey, String data) throws IOException {
        byte[] byteData = data.getBytes(StandardCharsets.UTF_8);
        byte[] iv = new byte[IV_SIZE];
        RANDOM.nextBytes(iv);

        try {
            SecretKeySpec secretKeySpec = new SecretKeySpec(sharedKey, "AES");
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, new IvParameterSpec(iv));

            int estimatedSize = cipher.getOutputSize(byteData.length);
            byte[] ret = new byte[estimatedSize + IV_SIZE];

            cipher.doFinal(byteData, 0, byteData.length, ret, IV_SIZE);

            System.arraycopy(iv, 0, ret, 0, IV_SIZE);

            return new EncryptedData(Hash.hash64(byteData), ret);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | ShortBufferException |
                 IllegalBlockSizeException | BadPaddingException | InvalidAlgorithmParameterException e) {
            throw new IOException(e);
        }
    }

    public EncryptedData encryptData(EndpointInfo info, String data) throws IOException {
        return encryptData(info.getSharedKeyBytes(), data);
    }

    public String decryptData(byte[] sharedKey, byte[] data, String expectedHash) throws IOException {
        try {
            SecretKeySpec secretKeySpec = new SecretKeySpec(sharedKey, "AES");
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, new IvParameterSpec(data, 0, IV_SIZE));

            byte[] ret = cipher.doFinal(data, IV_SIZE, data.length - IV_SIZE);

            String hash = Hash.hash64(ret);
            if (!hash.equals(expectedHash))
                throw new IOException("Invalid hash for encrypted payload");

            return new String(ret, StandardCharsets.UTF_8);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException |
                 IllegalBlockSizeException | BadPaddingException | InvalidAlgorithmParameterException e) {
            throw new IOException(e);
        }
    }

    public String decryptData(EndpointInfo info, byte[] data, String hash) throws IOException {
        return decryptData(info.getSharedKeyBytes(), data, hash);
    }

    public String registerEndpoint(String foreignKey) {
        EndpointInfo info = new EndpointInfo(foreignKey);
        synchronized (endpoints) {
            if (endpointInfoSortedSet.size() >= MAX_SIZE) {
                EndpointInfo first = endpointInfoSortedSet.first();
                endpointInfoSortedSet.remove(first);
                endpoints.remove(first.foreignKey);
            }
            endpointInfoSortedSet.add(info);
            endpoints.put(foreignKey, info);
        }
        return info.getPublicKey();
    }

    public EndpointInfo getEndpoint(String foreignKey) {
        synchronized (endpoints) {
            EndpointInfo info = endpoints.get(foreignKey);
            if (info != null) {
                endpointInfoSortedSet.remove(info);
                info.lastAccess = Instant.now();
                endpointInfoSortedSet.add(info);
            }
            return info;
        }
    }

    public void setEndpointAuthenticated(EndpointInfo endpointInfo) {
        if (!endpointInfo.authenticated) {
            synchronized (endpoints) {
                endpointInfoSortedSet.remove(endpointInfo);
                endpointInfo.authenticated = true;
                endpointInfoSortedSet.add(endpointInfo);
            }
        }
    }

    public synchronized boolean needAuthentication() {
        InstanceFactory factory = InstanceFactory.getFactory(getClass());
        if (factory == cachedFactory) {
            return needAuthentication;
        }
        needAuthentication = false;
        cachedFactory = factory;
        if (InstanceFactory.hasConfiguration(true)) {
            BackupConfiguration configuration = InstanceFactory.getInstance(BackupConfiguration.class);
            if (configuration != null && configuration.getManifest() != null &&
                    configuration.getManifest().getAuthenticationRequired() != null &&
                    configuration.getManifest().getAuthenticationRequired()) {
                try {
                    InstanceFactory.getInstance(ROOT_KEY, EncryptionIdentity.class);
                    needAuthentication = true;
                    return true;
                } catch (Exception ignored) {
                }
            }
        }
        return false;
    }

    public static class EndpointInfo implements Comparable<EndpointInfo> {
        private final String foreignKey;
        @Getter
        private final String sharedKey;
        @Getter
        private final String publicKey;
        @Getter
        private final byte[] sharedKeyBytes;
        @Getter
        private final SortedSet<Long> lastNonce = new TreeSet<>();
        private Instant lastAccess = Instant.now();
        private boolean authenticated = false;

        public EndpointInfo(String foreignKey) {
            byte[] privateKey = X25519.generatePrivateKey();
            try {
                publicKey = Hash.encodeBytes64(X25519.publicFromPrivate(privateKey));
                sharedKeyBytes = X25519.computeSharedSecret(privateKey, Hash.decodeBytes64(foreignKey));
                sharedKey = Hash.encodeBytes(sharedKeyBytes);
            } catch (InvalidKeyException e) {
                throw new RuntimeException(e);
            }
            this.foreignKey = foreignKey;
        }

        public static String computeHash(String method, String path, String nonce, String sharedKey) {
            String auth = method + ":" + path + ":" + sharedKey + ":" + nonce;
//            System.out.printf("auth: %s%n", auth);
            return Hash.hash64(auth.getBytes(StandardCharsets.UTF_8));
        }

        public boolean validateNonce(String nonce) {
            long nonceLong = Long.parseLong(nonce);
            if (lastNonce.isEmpty()) {
                return true;
            }
            if (nonceLong < lastNonce.first()) {
                return false;
            }
            return !lastNonce.contains(nonceLong);
        }

        public boolean recordNonce(String nonce) {
            if (!lastNonce.add(Long.parseLong(nonce))) {
                return false;
            }
            if (lastNonce.size() > MAX_SIZE) {
                lastNonce.remove(lastNonce.first());
            }
            return true;
        }

        @Override
        public int compareTo(EndpointInfo o) {
            if (o.authenticated != authenticated) {
                return Boolean.compare(o.authenticated, authenticated);
            }
            return lastAccess.compareTo(o.lastAccess);
        }
    }

    @Data
    @AllArgsConstructor
    public class EncryptedData {
        private String hash;
        private byte[] data;
    }
}
