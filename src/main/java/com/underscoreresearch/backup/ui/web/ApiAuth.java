package com.underscoreresearch.backup.ui.web;

import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.EncryptionIdentity;
import com.underscoreresearch.backup.encryption.Hash;
import com.underscoreresearch.backup.encryption.encryptors.x25519.X25519;
import com.underscoreresearch.backup.model.BackupConfiguration;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;

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

import static com.underscoreresearch.backup.configuration.EncryptionModule.ROOT_KEY;
import static com.underscoreresearch.backup.encryption.EncryptionIdentity.RANDOM;

/**
 * Authentication manager for the API endpoints.
 * This class handles endpoint registration, authentication, and encryption/decryption
 * of data exchanged between the client and server.
 */
public class ApiAuth {
    private static final int MAX_SIZE = 100;
    private static final int IV_SIZE = 16;
    private static final ApiAuth INSTANCE = new ApiAuth();
    private final SortedSet<EndpointInfo> endpointInfoSortedSet = new TreeSet<>();
    private final Map<String, EndpointInfo> endpoints = new HashMap<>();
    private InstanceFactory cachedFactory;
    private boolean needAuthentication;

    /**
     * Private constructor to enforce singleton pattern.
     */
    private ApiAuth() {
    }

    /**
     * Gets the singleton instance of ApiAuth.
     *
     * @return The ApiAuth instance
     */
    public static ApiAuth getInstance() {
        return INSTANCE;
    }

    /**
     * Encrypts data using a shared key.
     *
     * @param sharedKey The shared key for encryption
     * @param data The data to encrypt
     * @return The encrypted data
     * @throws IOException If there's an error during encryption
     */
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

    /**
     * Encrypts data using an endpoint's shared key.
     *
     * @param info The endpoint information
     * @param data The data to encrypt
     * @return The encrypted data
     * @throws IOException If there's an error during encryption
     */
    public EncryptedData encryptData(EndpointInfo info, String data) throws IOException {
        return encryptData(info.getSharedKeyBytes(), data);
    }

    /**
     * Decrypts data using a shared key.
     *
     * @param sharedKey The shared key for decryption
     * @param data The encrypted data
     * @param expectedHash The expected hash of the decrypted data
     * @return The decrypted data
     * @throws IOException If there's an error during decryption
     */
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

    /**
     * Decrypts data using an endpoint's shared key.
     *
     * @param info The endpoint information
     * @param data The encrypted data
     * @param hash The expected hash of the decrypted data
     * @return The decrypted data
     * @throws IOException If there's an error during decryption
     */
    public String decryptData(EndpointInfo info, byte[] data, String hash) throws IOException {
        return decryptData(info.getSharedKeyBytes(), data, hash);
    }

    /**
     * Registers a new endpoint with the given foreign key.
     *
     * @param foreignKey The foreign key for the endpoint
     * @return The public key for the endpoint
     */
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

    /**
     * Gets endpoint information for a foreign key.
     *
     * @param foreignKey The foreign key
     * @return The endpoint information, or null if not found
     */
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

    /**
     * Marks an endpoint as authenticated.
     *
     * @param endpointInfo The endpoint to mark as authenticated
     */
    public void setEndpointAuthenticated(EndpointInfo endpointInfo) {
        if (!endpointInfo.authenticated) {
            synchronized (endpoints) {
                endpointInfoSortedSet.remove(endpointInfo);
                endpointInfo.authenticated = true;
                endpointInfoSortedSet.add(endpointInfo);
            }
        }
    }

    /**
     * Checks if authentication is needed for the current configuration.
     *
     * @return True if authentication is needed, false otherwise
     */
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

    /**
     * Information about an endpoint, including keys and authentication status.
     */
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

        /**
         * Creates a new EndpointInfo with the given foreign key.
         *
         * @param foreignKey The foreign key for the endpoint
         */
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

        /**
         * Computes a hash for authentication.
         *
         * @param method The HTTP method
         * @param path The request path
         * @param nonce The nonce value
         * @param sharedKey The shared key
         * @return The computed hash
         */
        public static String computeHash(String method, String path, String nonce, String sharedKey) {
            String auth = method + ":" + path + ":" + sharedKey + ":" + nonce;
//            System.out.printf("auth: %s%n", auth);
            return Hash.hash64(auth.getBytes(StandardCharsets.UTF_8));
        }

        /**
         * Validates a nonce to prevent replay attacks.
         *
         * @param nonce The nonce to validate
         * @return True if the nonce is valid, false otherwise
         */
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

        /**
         * Records a nonce as used.
         *
         * @param nonce The nonce to record
         * @return True if the nonce was recorded, false if it was already used
         */
        public boolean recordNonce(String nonce) {
            if (!lastNonce.add(Long.parseLong(nonce))) {
                return false;
            }
            if (lastNonce.size() > MAX_SIZE) {
                lastNonce.remove(lastNonce.first());
            }
            return true;
        }

        /**
         * Compares this endpoint to another for sorting.
         *
         * @param o The other endpoint
         * @return A negative integer, zero, or a positive integer as this endpoint
         *         is less than, equal to, or greater than the specified endpoint
         */
        @Override
        public int compareTo(EndpointInfo o) {
            if (o.authenticated != authenticated) {
                return Boolean.compare(o.authenticated, authenticated);
            }
            return lastAccess.compareTo(o.lastAccess);
        }
    }

    /**
     * Data class representing encrypted data with its hash.
     */
    @Data
    @AllArgsConstructor
    public class EncryptedData {
        private String hash;
        private byte[] data;
    }
}
