package com.underscoreresearch.backup.encryption;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectReader;
import com.underscoreresearch.backup.encryption.encryptors.PQCEncryptor;
import com.underscoreresearch.backup.encryption.encryptors.x25519.X25519;
import de.mkammerer.argon2.Argon2Advanced;
import de.mkammerer.argon2.Argon2Factory;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static com.underscoreresearch.backup.encryption.encryptors.BaseAesEncryptor.applyKeyData;
import static com.underscoreresearch.backup.utils.SerializationUtils.MAPPER;

/**
 * Legacy implementation of encryption key management.
 * This class provides backward compatibility with older key formats
 * and handles conversion between legacy and current key formats.
 */
@Slf4j
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
public class LegacyEncryptionKey {
    public static final String DISPLAY_PREFIX = "=";
    private static final int LEGACY_ITERATIONS = 64 * 1024;
    private static final String CURRENT_ALGORITHM = "ARGON2";
    private static final ObjectReader ENCRYPTION_KEY_READER = MAPPER.readerFor(LegacyEncryptionKey.class);
    static PQCEncryptor ENCRYPTOR = new PQCEncryptor();
    private String publicKeyHash;
    private byte[] publicKey;
    private byte[] sharingPublicKey;
    private byte[] salt;
    private byte[] passwordKey;
    private byte[] keyData;
    @Getter
    @Setter
    private String algorithm;
    @Getter
    @Setter
    private String encryptedAdditionalKeys;
    private byte[] blockHashSalt;
    @Getter
    @Setter
    private String blockHashSaltEncrypted;
    @JsonIgnore
    private PrivateKey cachedPrivateKey;

    /**
     * Adjusts a private key to conform to the required format.
     * Sets specific bits in the private key to ensure it meets the requirements
     * for X25519 key operations.
     *
     * @param privateKey The private key to adjust
     */
    private static void makePrivateKey(byte[] privateKey) {
        privateKey[0] = (byte) (privateKey[0] | 7);
        privateKey[31] = (byte) (privateKey[31] & 63);
        privateKey[31] = (byte) (privateKey[31] | 128);
    }

    /**
     * Derives a key from a password using the specified algorithm.
     * Supports both Argon2 (current) and PBKDF2 (legacy) algorithms.
     *
     * @param algorithm The algorithm to use for key derivation
     * @param password The password to derive the key from
     * @param saltData The salt to use for key derivation
     * @return The derived key
     * @throws NoSuchAlgorithmException If the algorithm is not available
     * @throws InvalidKeySpecException If the key specification is invalid
     */
    private static byte[] getPasswordDerivative(String algorithm, String password, byte[] saltData) throws NoSuchAlgorithmException, InvalidKeySpecException {
        if (CURRENT_ALGORITHM.equals(algorithm)) {
            Argon2Advanced argon2 = Argon2Factory.createAdvanced();
            String hash = argon2.hash(64, 8192, 2, password.toCharArray(), StandardCharsets.UTF_8, saltData);
            int lastPart = hash.lastIndexOf('$');
            return Base64.decodeBase64(hash.substring(lastPart + 1));
        } else if (algorithm == null) {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), saltData, LEGACY_ITERATIONS, 32 * 8);
            SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
            return skf.generateSecret(spec).getEncoded();
        }
        throw new InvalidKeySpecException();
    }

    /**
     * Creates a LegacyEncryptionKey with only a public key.
     * Used for encryption operations where only the public key is needed.
     *
     * @param publicKey The public key in encoded string format
     * @return A new LegacyEncryptionKey instance with the specified public key
     */
    public static LegacyEncryptionKey createWithPublicKey(String publicKey) {
        LegacyEncryptionKey ret = new LegacyEncryptionKey();
        ret.setPublicKey(publicKey);
        return ret;
    }

    /**
     * Creates a LegacyEncryptionKey from key data.
     * Supports both private key format (starting with prefix) and JSON format.
     *
     * @param keyData The key data in string format
     * @return A new LegacyEncryptionKey instance created from the key data
     * @throws InvalidKeyException If the key data is invalid
     * @throws JsonProcessingException If there's an error parsing the JSON
     */
    public static LegacyEncryptionKey createWithKeyData(String keyData) throws InvalidKeyException, JsonProcessingException {
        if (keyData.startsWith(DISPLAY_PREFIX)) {
            return createWithPrivateKey(keyData);
        }
        return ENCRYPTION_KEY_READER.readValue(keyData);
    }

    /**
     * Creates a LegacyEncryptionKey with a private key.
     * The private key must be in the expected format with the correct prefix.
     *
     * @param privateKey The private key in encoded string format with prefix
     * @return A new LegacyEncryptionKey instance with the specified private key
     * @throws InvalidKeyException If the private key is invalid or in the wrong format
     */
    public static LegacyEncryptionKey createWithPrivateKey(String privateKey) throws InvalidKeyException {
        if (!privateKey.startsWith(DISPLAY_PREFIX)) {
            throw new IllegalArgumentException("Invalid private key string. Should start with \"p-\"");
        }
        LegacyEncryptionKey ret = new LegacyEncryptionKey();
        ret.cachedPrivateKey = new PrivateKey(null, Hash.decodeBytes(privateKey.substring(DISPLAY_PREFIX.length())), ret);
        ret.publicKey = X25519.publicFromPrivate(ret.cachedPrivateKey.privateKey);
        return ret;
    }

    /**
     * Gets the private key for serialization.
     * Returns the encoded private key if it's available and not password-protected.
     *
     * @return The encoded private key or null if not available or password-protected
     */
    @JsonProperty("privateKey")
    public String getPrivateKeySerializing() {
        if (cachedPrivateKey != null && cachedPrivateKey.password == null)
            return Hash.encodeBytes(cachedPrivateKey.privateKey);
        return null;
    }

    /**
     * Sets the private key from a serialized string.
     * Creates a PrivateKey instance from the encoded private key.
     *
     * @param privateKey The encoded private key or null to clear the private key
     */
    @JsonProperty("privateKey")
    public void setPrivateKeySerializing(String privateKey) {
        if (privateKey != null)
            this.cachedPrivateKey = new PrivateKey(null, Hash.decodeBytes(privateKey), this);
        else
            this.cachedPrivateKey = null;
    }

    /**
     * Gets the key data for serialization.
     * Returns the encoded key data if available.
     *
     * @return The encoded key data or null if not available
     */
    @JsonProperty("keyData")
    public String getKeyData() {
        if (keyData != null)
            return Hash.encodeBytes(keyData);
        return null;
    }

    /**
     * Sets the key data from a serialized string.
     * Decodes the key data from the encoded string.
     *
     * @param xor The encoded key data or null to clear the key data
     */
    @JsonProperty("keyData")
    public void setKeyData(String xor) {
        if (xor != null)
            keyData = Hash.decodeBytes(xor);
        else
            keyData = null;
    }

    /**
     * Gets the block hash salt for serialization.
     * Returns the Base64 encoded block hash salt if available.
     *
     * @return The Base64 encoded block hash salt or null if not available
     */
    @JsonProperty("blockHashSalt")
    public String getBlockHashSalt() {
        if (blockHashSalt != null)
            return Hash.encodeBytes64(blockHashSalt);
        return null;
    }

    /**
     * Sets the block hash salt from a serialized string.
     * Decodes the block hash salt from the Base64 encoded string.
     *
     * @param salt The Base64 encoded block hash salt or null to clear the salt
     */
    @JsonProperty("blockHashSalt")
    public void setBlockHashSalt(String salt) {
        if (salt != null)
            blockHashSalt = Hash.decodeBytes64(salt);
        else
            blockHashSalt = null;
    }

    /**
     * Adds the block hash salt to a hash.
     * Used to incorporate the salt into hash calculations for blocks.
     *
     * @param hash The hash to add the block hash salt to
     */
    @JsonIgnore
    public void addBlockHashSalt(Hash hash) {
        if (blockHashSalt != null)
            hash.addBytes(blockHashSalt);
    }

    /**
     * Gets the private key using the provided password.
     * Derives the private key from the password and key data, and verifies it against the public key.
     *
     * @param password The password to use for deriving the private key
     * @return The private key
     * @throws IllegalArgumentException If the password is incorrect or the key cannot be unpacked
     */
    @JsonIgnore
    public PrivateKey getPrivateKey(String password) {
        if (cachedPrivateKey == null || !Objects.equals(cachedPrivateKey.getPassword(), password)) {
            try {
                byte[] saltData = Hash.decodeBytes(getSalt());

                byte[] bytes = getPasswordDerivative(algorithm, password, saltData);

                if (keyData != null) {
                    bytes = applyKeyData(bytes, keyData);
                    makePrivateKey(bytes);
                } else if (passwordKey != null) {
                    throw new InvalidKeyException("Key is in old unsupported format. Open with version 2.4 or earlier");
                }

                byte[] publicKey = X25519.publicFromPrivate(bytes);
                if (this.publicKey != null) {
                    if (!Arrays.equals(publicKey, this.publicKey)) {
                        throw new InvalidKeyException();
                    }
                } else {
                    if (this.publicKeyHash != null) {
                        if (!Hash.hash64(publicKey).equals(this.publicKeyHash)) {
                            throw new InvalidKeyException();
                        }
                    }
                    this.publicKey = publicKey;
                }

                cachedPrivateKey = new PrivateKey(password, bytes, this);
            } catch (InvalidKeyException | NoSuchAlgorithmException | InvalidKeySpecException e) {
                throw new IllegalArgumentException("Could not unpack private key with password");
            }
        }
        return cachedPrivateKey;
    }

    /**
     * Gets the public key for serialization.
     * Returns the encoded public key if available.
     *
     * @return The encoded public key or null if not available
     */
    @JsonProperty
    public String getPublicKey() {
        if (publicKey != null)
            return Hash.encodeBytes(publicKey);
        return null;
    }

    /**
     * Sets the public key from a serialized string.
     * Decodes the public key from the encoded string.
     *
     * @param publicKey The encoded public key or null to clear the public key
     */
    @JsonProperty
    public void setPublicKey(String publicKey) {
        if (publicKey != null)
            this.publicKey = Hash.decodeBytes(publicKey);
        else
            this.publicKey = null;
    }

    /**
     * Gets the public key hash.
     * Calculates the hash from the public key if available.
     *
     * @return The public key hash
     */
    @JsonProperty
    public String getPublicKeyHash() {
        if (publicKey != null)
            publicKeyHash = Hash.hash64(publicKey);
        return publicKeyHash;
    }

    /**
     * Sets the public key hash.
     *
     * @param publicKeyHash The public key hash
     */
    @JsonProperty
    public void setPublicKeyHash(String publicKeyHash) {
        this.publicKeyHash = publicKeyHash;
    }

    /**
     * Gets the sharing public key for serialization.
     * Returns the encoded sharing public key if available.
     *
     * @return The encoded sharing public key or null if not available
     */
    @JsonProperty
    public String getSharingPublicKey() {
        if (sharingPublicKey != null)
            return Hash.encodeBytes(sharingPublicKey);
        return null;
    }

    /**
     * Sets the sharing public key from a serialized string.
     * Decodes the sharing public key from the encoded string.
     *
     * @param sharingPublicKey The encoded sharing public key or null to clear the sharing public key
     */
    @JsonProperty
    public void setSharingPublicKey(String sharingPublicKey) {
        if (sharingPublicKey != null)
            this.sharingPublicKey = Hash.decodeBytes(sharingPublicKey);
        else
            this.sharingPublicKey = null;
    }

    /**
     * Gets a new LegacyEncryptionKey with only the sharing public key.
     * Used for sharing operations where only the sharing public key is needed.
     *
     * @return A new LegacyEncryptionKey instance with only the sharing public key
     */
    @JsonIgnore
    public LegacyEncryptionKey getSharingPublicEncryptionKey() {
        LegacyEncryptionKey key = new LegacyEncryptionKey();
        key.publicKey = sharingPublicKey;
        return key;
    }

    /**
     * Gets the salt for serialization.
     * Returns the encoded salt if available.
     *
     * @return The encoded salt or null if not available
     */
    @JsonProperty
    public String getSalt() {
        if (salt != null)
            return Hash.encodeBytes(salt);
        return null;
    }

    /**
     * Sets the salt from a serialized string.
     * Decodes the salt from the encoded string.
     *
     * @param salt The encoded salt or null to clear the salt
     */
    @JsonProperty
    public void setSalt(String salt) {
        if (salt != null)
            this.salt = Hash.decodeBytes(salt);
        else
            this.salt = null;
    }

    /**
     * Gets the password key for serialization.
     * Returns the encoded password key if available.
     *
     * @return The encoded password key or null if not available
     */
    @JsonProperty
    public String getPasswordKey() {
        if (passwordKey != null)
            return Hash.encodeBytes(passwordKey);
        return null;
    }

    /**
     * Sets the password key from a serialized string.
     * Decodes the password key from the encoded string.
     *
     * @param passwordKey The encoded password key or null to clear the password key
     */
    @JsonProperty
    public void setPasswordKey(String passwordKey) {
        if (passwordKey != null)
            this.passwordKey = Hash.decodeBytes(passwordKey);
        else
            this.passwordKey = null;
    }

    /**
     * Creates a copy of this key with only public information.
     * Includes the public key and block hash salt.
     *
     * @return A new LegacyEncryptionKey instance with only public information
     */
    public LegacyEncryptionKey publicOnly() {
        LegacyEncryptionKey ret = publicOnlyHash();
        ret.publicKey = publicKey;
        ret.blockHashSalt = blockHashSalt;
        return ret;
    }

    /**
     * Creates a copy of this key with only the public key hash.
     * Includes additional keys, sharing public key, and encrypted block hash salt.
     *
     * @return A new LegacyEncryptionKey instance with only the public key hash and related information
     */
    public LegacyEncryptionKey publicOnlyHash() {
        LegacyEncryptionKey ret = serviceOnlyKey();
        ret.encryptedAdditionalKeys = encryptedAdditionalKeys;
        ret.sharingPublicKey = sharingPublicKey;
        ret.blockHashSaltEncrypted = blockHashSaltEncrypted;
        return ret;
    }

    /**
     * Creates a copy of this key with only service-related information.
     * Includes the public key hash, salt, password key, key data, and algorithm.
     *
     * @return A new LegacyEncryptionKey instance with only service-related information
     */
    public LegacyEncryptionKey serviceOnlyKey() {
        LegacyEncryptionKey ret = new LegacyEncryptionKey();
        ret.publicKeyHash = getPublicKeyHash();
        ret.salt = salt;
        ret.passwordKey = passwordKey;
        ret.keyData = keyData;
        ret.algorithm = algorithm;
        return ret;
    }

    /**
     * Represents a private key associated with a LegacyEncryptionKey.
     * Contains the private key bytes and a reference to the parent key.
     */
    @Getter
    public static class PrivateKey {
        /**
         * The password used to derive this private key, if any.
         */
        private final String password;
        
        /**
         * The parent LegacyEncryptionKey that this private key belongs to.
         */
        private final LegacyEncryptionKey parent;
        
        /**
         * The actual private key bytes.
         */
        private byte[] privateKey;


        /**
         * Constructor for PrivateKey.
         * Creates a new private key with the specified password, key bytes, and parent.
         *
         * @param password The password used to derive this private key, or null if not password-derived
         * @param privateKey The private key bytes
         * @param parent The parent LegacyEncryptionKey
         */
        PrivateKey(String password, byte[] privateKey, LegacyEncryptionKey parent) {
            this.password = password;
            this.privateKey = privateKey;
            this.parent = parent;
        }

        /**
         * Sets the private key bytes.
         *
         * @param privateKey The new private key bytes
         */
        public void setPrivateKey(byte[] privateKey) {
            this.privateKey = privateKey;
        }
    }

    /**
     * Manages additional encryption keys for a backup source.
     * Handles decryption and management of additional private keys.
     */
    public static class AdditionalKeyManager {
        private final static ObjectReader READER = MAPPER.readerFor(new TypeReference<List<String>>() {
        });
        /**
         * List of additional encryption keys.
         */
        private final List<LegacyEncryptionKey> keys;

        /**
         * Constructor for AdditionalKeyManager.
         * Decrypts and loads additional keys from the encrypted string.
         *
         * @param privateKeys The private keys to use for decryption
         * @param encryptedAdditionalKeys The encrypted additional keys string
         * @throws IOException If there's an error reading the keys
         * @throws GeneralSecurityException If there's an error decrypting the keys
         */
        public AdditionalKeyManager(IdentityKeys.PrivateKeys privateKeys, String encryptedAdditionalKeys) throws IOException, GeneralSecurityException {
            keys = new ArrayList<>();

            if (encryptedAdditionalKeys != null) {
                List<String> additionalPrivateKeys;
                try {
                    additionalPrivateKeys = READER.readValue(ENCRYPTOR.decodeBlock(null,
                            Hash.decodeBytes64(encryptedAdditionalKeys), privateKeys));
                } catch (Exception exc) {
                    // This is only for backwards compatability.
                    additionalPrivateKeys = READER.readValue(ENCRYPTOR.decodeBlock(null,
                            Hash.decodeBytes(encryptedAdditionalKeys), privateKeys));
                }
                for (String key : additionalPrivateKeys) {
                    try {
                        keys.add(LegacyEncryptionKey.createWithPrivateKey(key));
                    } catch (InvalidKeyException e) {
                        throw new IOException("Invalid key", e);
                    }
                }
            }
        }

        /**
         * Finds a private key that matches the given public key.
         * Searches through the loaded keys for one with a matching public key hash.
         *
         * @param publicKey The public key to match
         * @return The matching private key, or null if no match is found
         */
        public LegacyEncryptionKey findMatchingPrivateKey(LegacyEncryptionKey publicKey) {
            for (LegacyEncryptionKey key : keys) {
                if (key.getPublicKeyHash().equals(publicKey.getPublicKeyHash())) {
                    return key;
                }
            }
            return null;
        }

        /**
         * Gets all the loaded keys.
         * Returns an array containing all the loaded encryption keys.
         *
         * @return Array of LegacyEncryptionKey instances
         */
        public synchronized LegacyEncryptionKey[] getKeys() {
            LegacyEncryptionKey[] ret = new LegacyEncryptionKey[keys.size()];
            return keys.toArray(ret);
        }
    }

}
