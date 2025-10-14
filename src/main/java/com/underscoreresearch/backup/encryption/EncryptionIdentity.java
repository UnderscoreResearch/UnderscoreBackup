package com.underscoreresearch.backup.encryption;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import de.mkammerer.argon2.Argon2Advanced;
import de.mkammerer.argon2.Argon2Factory;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;

import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.underscoreresearch.backup.encryption.encryptors.BaseAesEncryptor.applyKeyData;
import static com.underscoreresearch.backup.utils.SerializationUtils.MAPPER;

/**
 * Manages encryption identity including public and private keys for backup encryption.
 * This class handles key generation, encryption/decryption of data, and management of
 * multiple identity keys for sharing and additional purposes.
 */
@NoArgsConstructor
@Getter
@Slf4j
public class EncryptionIdentity {
    public static final SecureRandom RANDOM = new SecureRandom();
    protected static final String CURRENT_ALGORITHM = "ARGON2";
    private final static ObjectWriter IDENTITY_WRITER = MAPPER.writerFor(EncryptionIdentity.class);
    private final static ObjectWriter LEGACY_KEY_WRITER = MAPPER.writerFor(LegacyEncryptionKey.class);

    private final static ObjectReader IDENTITY_READER = MAPPER.readerFor(EncryptionIdentity.class);
    private final static ObjectReader LEGACY_KEY_READER = MAPPER.readerFor(LegacyEncryptionKey.class);
    private static final String KEY_ALGORITHM = "AES";
    private static final String ENCRYPTION_ALGORITHM = "AES/GCM/NoPadding";

    @Getter
    @Setter
    protected String algorithm;
    protected byte[] salt;
    protected byte[] keyData;
    protected byte[] privateHash;
    @JsonIgnore
    protected PrivateIdentity cachedPrivateIdentity;
    @JsonProperty("primaryKeys")
    @Getter
    protected IdentityKeys primaryKeys;

    @JsonProperty("additionalKeys")
    private List<IdentityKeys> additionalKeys;
    @JsonProperty("sharingKeys")
    private IdentityKeys sharingKeys;
    private byte[] blockHashSalt;
    @JsonProperty("blockHashSaltEncrypted")
    private String blockHashSaltEncrypted;
    @JsonIgnore
    private LegacyEncryptionKey legacyKey;

    /**
     * Constructor with JSON properties.
     * Creates an EncryptionIdentity from serialized properties.
     *
     * @param algorithm The encryption algorithm used
     * @param salt Base64 encoded salt for password derivation
     * @param keyData Base64 encoded key data
     * @param privateHash Base64 encoded hash of the private key
     * @param keys The primary identity keys
     * @param additionalKeys List of additional identity keys
     * @param sharingKey The sharing identity keys
     * @param blockHashSalt Base64 encoded salt for block hashing
     * @param blockHashSaltEncrypted Encrypted version of the block hash salt
     */
    public EncryptionIdentity(
            @JsonProperty("algorithm") String algorithm,
            @JsonProperty("salt") String salt,
            @JsonProperty("keyData") String keyData,
            @JsonProperty("privateHash") String privateHash,
            @JsonProperty("primaryKeys") IdentityKeys keys,
            @JsonProperty("additionalKeys") List<IdentityKeys> additionalKeys,
            @JsonProperty("sharingKeys") IdentityKeys sharingKey,
            @JsonProperty("blockHashSalt") String blockHashSalt,
            @JsonProperty("blockHashSaltEncrypted") String blockHashSaltEncrypted) {
        this.primaryKeys = keys;
        this.algorithm = algorithm;
        if (salt != null)
            this.salt = Hash.decodeBytes(salt);
        if (keyData != null)
            this.keyData = Hash.decodeBytes(keyData);
        if (privateHash != null)
            this.privateHash = Hash.decodeBytes64(privateHash);

        this.additionalKeys = additionalKeys;
        this.sharingKeys = sharingKey;

        if (blockHashSalt != null) {
            this.blockHashSalt = Hash.decodeBytes64(blockHashSalt);
        }
        this.blockHashSaltEncrypted = blockHashSaltEncrypted;
    }

    /**
     * Constructor from legacy encryption key.
     * Creates an EncryptionIdentity using a password and legacy private key.
     *
     * @param password The password to protect the private key
     * @param privateKey The legacy private key
     * @throws GeneralSecurityException If there's an error during key conversion
     */
    public EncryptionIdentity(String password, LegacyEncryptionKey.PrivateKey privateKey) throws GeneralSecurityException {
        populateFromLegacyPrivateKey(password, privateKey);
    }

    /**
     * Constructor from legacy encryption key.
     * Creates an EncryptionIdentity from a legacy key format.
     *
     * @param legacyKey The legacy encryption key
     * @throws GeneralSecurityException If there's an error during key conversion
     */
    public EncryptionIdentity(LegacyEncryptionKey legacyKey) throws GeneralSecurityException {
        this.legacyKey = legacyKey;
        this.algorithm = legacyKey.getAlgorithm();
        if (legacyKey.getSalt() != null) {
            this.salt = Hash.decodeBytes(legacyKey.getSalt());
        }
        if (legacyKey.getKeyData() != null) {
            this.keyData = Hash.decodeBytes(legacyKey.getKeyData());
        }

        if (legacyKey.getPublicKey() != null) {
            primaryKeys = IdentityKeys.fromString(legacyKey.getPublicKey(), null);
        } else if (legacyKey.getPublicKeyHash() != null) {
            primaryKeys = new IdentityKeys(Map.of(IdentityKeys.X25519_KEY,
                    new PublicKey(null,
                            null,
                            legacyKey.getPublicKeyHash(),
                            null,
                            null)));
        }
        if (legacyKey.getSharingPublicKey() != null) {
            sharingKeys = IdentityKeys.fromString(legacyKey.getSharingPublicKey(), null);
        }

        if (legacyKey.getBlockHashSalt() != null)
            blockHashSalt = Hash.decodeBytes64(legacyKey.getBlockHashSalt());
    }

    /**
     * Generates a new encryption identity with a password.
     * Creates a new identity with fresh keys protected by the provided password.
     *
     * @param password The password to protect the private key
     * @return A new EncryptionIdentity instance
     * @throws GeneralSecurityException If there's an error during key generation
     */
    public static EncryptionIdentity generateKeyWithPassword(String password) throws GeneralSecurityException {
        EncryptionIdentity ret = new EncryptionIdentity();
        ret.generatePrivateIdentity(password, null);

        ret.primaryKeys = IdentityKeys.createIdentityKeys(ret.cachedPrivateIdentity);
        ret.sharingKeys = IdentityKeys.createIdentityKeys(ret.cachedPrivateIdentity);
        ret.additionalKeys = new ArrayList<>();

        ret.blockHashSalt = new byte[32];
        RANDOM.nextBytes(ret.blockHashSalt);

        ret.updateBlockHashSaltEncryption(ret.cachedPrivateIdentity);

        return ret;
    }

    /**
     * Restores an encryption identity from a serialized string.
     * Attempts to parse the string as a modern identity first, then as a legacy key.
     *
     * @param str The serialized encryption identity
     * @return The restored EncryptionIdentity instance
     * @throws GeneralSecurityException If there's an error during deserialization
     */
    public static EncryptionIdentity restoreFromString(String str) throws GeneralSecurityException {
        try {
            EncryptionIdentity identity = IDENTITY_READER.readValue(str);
            if (identity.getPrimaryKeys() != null) {
                return identity;
            }
        } catch (JsonProcessingException ignored) {
        }
        try {
            return new EncryptionIdentity(LEGACY_KEY_READER.readValue(str));
        } catch (JsonProcessingException ex) {
            throw new GeneralSecurityException(ex);
        }
    }

    /**
     * Derives a key from a password using the specified algorithm.
     * Currently supports ARGON2 algorithm for password-based key derivation.
     *
     * @param algorithm The algorithm to use for key derivation
     * @param password The password to derive the key from
     * @param saltData The salt to use in the derivation process
     * @return The derived key bytes
     * @throws InvalidKeySpecException If the algorithm is not supported
     */
    protected static byte[] getPasswordDerivative(String algorithm, String password, byte[] saltData) throws InvalidKeySpecException {
        if (CURRENT_ALGORITHM.equals(algorithm)) {
            Argon2Advanced argon2 = Argon2Factory.createAdvanced();
            String hash = argon2.hash(64, 8192, 2, password.toCharArray(), StandardCharsets.UTF_8, saltData);
            int lastPart = hash.lastIndexOf('$');
            return Base64.decodeBase64(hash.substring(lastPart + 1));
        }

        throw new InvalidKeySpecException();
    }

    /**
     * Creates an EncryptionIdentity with the specified identity keys.
     * This is a factory method for creating an identity with pre-existing keys.
     *
     * @param keys The identity keys to use
     * @return A new EncryptionIdentity instance with the specified keys
     */
    public static EncryptionIdentity withIdentityKeys(IdentityKeys keys) {
        EncryptionIdentity ret = new EncryptionIdentity();
        ret.primaryKeys = keys;
        return ret;
    }

    /**
     * Changes the password used to encrypt the private key.
     * Optionally generates a new main key pair.
     *
     * @param existingPassword The current password
     * @param newPassword The new password to use
     * @param newMainKey Whether to generate a new main key pair
     * @return A new EncryptionIdentity with the updated password
     * @throws GeneralSecurityException If there's an error during key re-encryption
     */
    public EncryptionIdentity changeEncryptionPassword(String existingPassword, String newPassword, boolean newMainKey)
            throws GeneralSecurityException {
        PrivateIdentity existingPI = getPrivateIdentity(existingPassword);
        EncryptionIdentity newIdentity = new EncryptionIdentity();
        newIdentity.generatePrivateIdentity(newPassword, newMainKey ? null : existingPI.privateKey);

        PrivateIdentity newPI = newIdentity.getPrivateIdentity(newPassword);

        unpackKeys(existingPI);

        if (getSharingKeys() != null)
            newIdentity.sharingKeys = getSharingKeys().changeEncryption(existingPI, newPI);
        if (!newMainKey) {
            newIdentity.primaryKeys = getPrimaryKeys().changeEncryption(existingPI, newPI);
        } else {
            newIdentity.primaryKeys = IdentityKeys.createIdentityKeys(newPI);
        }
        if (additionalKeys != null) {
            List<IdentityKeys> newAdditionalKeys = new ArrayList<>();
            for (IdentityKeys keys : additionalKeys) {
                newAdditionalKeys.add(keys.changeEncryption(existingPI, newPI));
            }
            newIdentity.additionalKeys = newAdditionalKeys;
        }
        newIdentity.blockHashSalt = blockHashSalt;
        newIdentity.updateBlockHashSaltEncryption(newPI);
        return newIdentity;
    }

    /**
     * Populates this identity from a legacy private key.
     * Converts the legacy key format to the current format.
     *
     * @param password The password to protect the private key
     * @param privateKey The legacy private key
     * @throws GeneralSecurityException If there's an error during key conversion
     */
    private void populateFromLegacyPrivateKey(String password, LegacyEncryptionKey.PrivateKey privateKey) throws GeneralSecurityException {
        generatePrivateIdentity(password, privateKey.getPrivateKey());

        PrivateIdentity privateIdentity = getPrivateIdentity(password);

        byte[] oldPrivateKey = privateKey.getPrivateKey();
        primaryKeys = IdentityKeys.createIdentityKeys(privateIdentity, oldPrivateKey);

        if (privateKey.getParent().getBlockHashSalt() != null) {
            this.blockHashSalt = Hash.decodeBytes64(privateKey.getParent().getBlockHashSalt());
            updateBlockHashSaltEncryption(cachedPrivateIdentity);
        }

        LegacyEncryptionKey.AdditionalKeyManager additionalKeyManager;
        try {
            additionalKeyManager = new LegacyEncryptionKey.AdditionalKeyManager(getPrivateKeys(password),
                    privateKey.getParent().getEncryptedAdditionalKeys());
        } catch (IOException e) {
            throw new GeneralSecurityException(e);
        }

        if (privateKey.getParent().getSharingPublicKey() != null) {
            LegacyEncryptionKey key = LegacyEncryptionKey.createWithPublicKey(privateKey.getParent().getSharingPublicKey());
            LegacyEncryptionKey sharingKey = additionalKeyManager.findMatchingPrivateKey(key);

            if (sharingKey != null) {
                sharingKeys = new IdentityKeys(Map.of(IdentityKeys.X25519_KEY,
                        new PublicKey(Hash.decodeBytes(sharingKey.getPublicKey()),
                                sharingKey.getPrivateKey(null).getPrivateKey(),
                                cachedPrivateIdentity)));
            } else {
                sharingKeys = null;
            }
        } else {
            sharingKeys = null;
        }

        additionalKeys = new ArrayList<>();
        for (LegacyEncryptionKey key : additionalKeyManager.getKeys()) {
            if (key.getPublicKey().equals(privateKey.getParent().getSharingPublicKey()))
                continue;

            additionalKeys.add(new IdentityKeys(Map.of(IdentityKeys.X25519_KEY,
                    new PublicKey(Hash.decodeBytes(key.getPublicKey()),
                            key.getPrivateKey(null).getPrivateKey(),
                            cachedPrivateIdentity))));
        }
    }

    /**
     * Checks if the keys need to be unpacked.
     * Returns true if the identity is using legacy keys or if the primary keys need unpacking.
     *
     * @return True if keys need to be unpacked, false otherwise
     */
    @JsonIgnore
    public boolean needKeyUnpack() {
        if (legacyKey != null) {
            return true;
        }
        if (primaryKeys == null || primaryKeys.needKeyUnpack()) {
            return true;
        }
        return false;
    }

    /**
     * Writes the encryption key to an output stream in the specified format.
     * Supports different formats for different use cases.
     *
     * @param format The format to write the key in
     * @param stream The output stream to write to
     * @throws IOException If there's an error writing to the stream
     */
    public void writeKey(KeyFormat format, OutputStream stream) throws IOException {
        if (legacyKey != null) {
            LEGACY_KEY_WRITER.writeValue(stream, switch (format) {
                case PUBLIC -> legacyKey;
                case UPLOAD -> legacyKey.publicOnlyHash();
                case SERVICE -> legacyKey.serviceOnlyKey();
            });
        } else {
            IDENTITY_WRITER.writeValue(stream, switch (format) {
                case PUBLIC -> this;
                case UPLOAD -> uploadKey();
                case SERVICE -> serviceKey();
            });
        }
    }

    /**
     * Unpacks encrypted keys using the provided private identity.
     * Decrypts all keys in the identity using the private identity.
     *
     * @param privateIdentity The private identity to use for unpacking
     * @throws GeneralSecurityException If there's an error during key decryption
     */
    public void unpackKeys(PrivateIdentity privateIdentity) throws GeneralSecurityException {
        primaryKeys.unpackKeys(privateIdentity);
        if (additionalKeys != null) {
            for (IdentityKeys additionalKey : additionalKeys) {
                additionalKey.unpackKeys(privateIdentity);
            }
        }
        if (sharingKeys != null) {
            sharingKeys.unpackKeys(privateIdentity);
        }
        if (blockHashSaltEncrypted != null && blockHashSalt == null) {
            blockHashSalt = privateIdentity.decryptKeyData(Hash.decodeBytes64(blockHashSaltEncrypted));
        }
    }

    /**
     * Updates the encrypted block hash salt.
     * Encrypts the block hash salt using the provided private identity.
     *
     * @param privateIdentity The private identity to use for encryption
     * @throws GeneralSecurityException If there's an error during encryption
     */
    private void updateBlockHashSaltEncryption(PrivateIdentity privateIdentity) throws GeneralSecurityException {
        if (blockHashSalt != null) {
            blockHashSaltEncrypted = Hash.encodeBytes64(privateIdentity.encryptKeyData(blockHashSalt));
        } else {
            blockHashSaltEncrypted = null;
        }
    }

    /**
     * Generates a private identity from a password.
     * Optionally uses an existing private key or generates a new one.
     *
     * @param password The password to protect the private key
     * @param existingPrivateKey The existing private key to use, or null to generate a new one
     * @throws GeneralSecurityException If there's an error during key generation
     */
    protected void generatePrivateIdentity(String password, byte[] existingPrivateKey) throws GeneralSecurityException {
        algorithm = CURRENT_ALGORITHM;

        byte[] saltData = new byte[32];
        RANDOM.nextBytes(saltData);

        byte[] bytes = getPasswordDerivative(algorithm, password, saltData);

        keyData = new byte[bytes.length];
        byte[] privateKey;
        if (existingPrivateKey != null) {
            keyData = applyKeyData(existingPrivateKey, bytes);
            privateKey = existingPrivateKey;
        } else {
            RANDOM.nextBytes(keyData);
            privateKey = applyKeyData(keyData, bytes);
        }
        cachedPrivateIdentity = new PrivateIdentity(password, privateKey);
        salt = saltData;

        HashSha3 hashSha3 = new HashSha3();
        hashSha3.addBytes(privateKey);
        privateHash = hashSha3.getHashBytes();
    }

    /**
     * Gets the Base64 encoded salt.
     *
     * @return The Base64 encoded salt, or null if not set
     */
    @JsonProperty("salt")
    public String getSalt() {
        if (salt != null)
            return Hash.encodeBytes(salt);
        return null;
    }

    /**
     * Gets the Base64 encoded key data.
     *
     * @return The Base64 encoded key data, or null if not set
     */
    @JsonProperty("keyData")
    public String getKeyData() {
        if (keyData != null)
            return Hash.encodeBytes(keyData);
        return null;
    }

    /**
     * Creates a version of this identity suitable for upload.
     * Includes only the necessary information for upload purposes.
     *
     * @return A new EncryptionIdentity with only the upload-relevant information
     */
    private EncryptionIdentity uploadKey() {
        EncryptionIdentity ret = serviceKey();
        if (additionalKeys != null) {
            ret.additionalKeys = additionalKeys.stream().map(IdentityKeys::withoutPublicKeys).collect(Collectors.toList());
        }
        if (sharingKeys != null) {
            ret.sharingKeys = sharingKeys.withoutPublicKeys();
        }
        ret.blockHashSaltEncrypted = blockHashSaltEncrypted;
        return ret;
    }

    /**
     * Creates a version of this identity with only password-related information.
     * Excludes all key information.
     *
     * @return A new EncryptionIdentity with only password-related information
     */
    private EncryptionIdentity onlyPassword() {
        EncryptionIdentity ret = new EncryptionIdentity();
        ret.algorithm = algorithm;
        ret.salt = salt;
        ret.keyData = keyData;
        ret.privateHash = privateHash;
        return ret;
    }

    /**
     * Creates a version of this identity suitable for service use.
     * Includes only the necessary information for service purposes.
     *
     * @return A new EncryptionIdentity with only the service-relevant information
     */
    private EncryptionIdentity serviceKey() {
        EncryptionIdentity ret = onlyPassword();
        ret.primaryKeys = primaryKeys.withoutPublicKeys();
        return ret;
    }

    /**
     * Gets the private identity for the given password.
     * If using a legacy key, converts it to the current format first.
     *
     * @param password The password to use
     * @return The private identity
     * @throws GeneralSecurityException If the password is incorrect or there's an error during key processing
     */
    @JsonIgnore
    public synchronized PrivateIdentity getPrivateIdentity(String password) throws GeneralSecurityException {
        if (legacyKey != null) {
            LegacyEncryptionKey.PrivateKey privateKey = legacyKey.getPrivateKey(password);
            legacyKey = null;
            try {
                populateFromLegacyPrivateKey(password, privateKey);
            } catch (Exception exc) {
                log.error("Failed to unpack legacy key", exc);
                legacyKey = privateKey.getParent();
                throw exc;
            }
        }

        if (cachedPrivateIdentity == null || !Objects.equals(cachedPrivateIdentity.getPassword(), password)) {
            byte[] bytes = getPasswordDerivative(algorithm, password, salt);
            byte[] privateKey = applyKeyData(keyData, bytes);

            HashSha3 hashSha3 = new HashSha3();
            hashSha3.addBytes(privateKey);
            if (!Arrays.equals(privateHash, hashSha3.getHashBytes()))
                throw new InvalidKeyException("Invalid password");

            cachedPrivateIdentity = new PrivateIdentity(password, privateKey);
        }
        return cachedPrivateIdentity;
    }

    /**
     * Gets the Base64 encoded hash of the private key.
     *
     * @return The Base64 encoded private key hash, or null if not set
     */
    @JsonProperty("privateHash")
    public String getPrivateHash() {
        if (privateHash != null)
            return Hash.encodeBytes64(privateHash);
        return null;
    }

    /**
     * Gets the Base64 encoded block hash salt.
     *
     * @return The Base64 encoded block hash salt, or null if not set
     */
    @JsonProperty("blockHashSalt")
    public String getBlockHashSalt() {
        if (blockHashSalt != null)
            return Hash.encodeBytes64(blockHashSalt);
        return null;
    }

    /**
     * Gets the raw block hash salt bytes.
     *
     * @return The block hash salt bytes, or null if not set
     */
    @JsonIgnore
    public byte[] getBlockHashSaltBytes() {
        return blockHashSalt;
    }

    /**
     * Updates the block hash salt.
     * Decodes the provided salt and updates the encrypted version.
     *
     * @param salt The Base64 encoded salt
     * @param privateIdentity The private identity to use for encryption
     * @throws GeneralSecurityException If there's an error during encryption
     */
    @JsonProperty("blockHashSalt")
    public void updateBlockHashSalt(String salt, PrivateIdentity privateIdentity) throws GeneralSecurityException {
        if (salt != null)
            blockHashSalt = Hash.decodeBytes64(salt);
        else
            blockHashSalt = null;
        updateBlockHashSaltEncryption(privateIdentity);
    }

    /**
     * Gets the identity keys for a public identity.
     * Searches through sharing keys and additional keys to find a match.
     *
     * @param publicKey The public identity to find keys for
     * @return The matching identity keys
     * @throws IndexOutOfBoundsException If no matching keys are found
     */
    public IdentityKeys getIdentityKeysForPublicIdentity(IdentityKeys publicKey) {
        if (sharingKeys != null) {
            if (sharingKeys.getPublicKeyHash().equals(publicKey.getPublicKeyHash())) {
                return sharingKeys;
            }
        }
        if (additionalKeys != null) {
            for (IdentityKeys keys : additionalKeys) {
                if (keys.getPublicKeyHash().equals(publicKey.getPublicKeyHash())) {
                    return keys;
                }
            }
        }
        throw new IndexOutOfBoundsException("Could not find key for public identity");
    }

    /**
     * Gets the identity key for a hash.
     * Searches through additional keys to find a match, or creates a new key if not found.
     *
     * @param hash The hash to find the key for
     * @return The matching identity key, or a new key if not found
     */
    public IdentityKeys getIdentityKeyForHash(String hash) {
        if (additionalKeys != null) {
            for (IdentityKeys keys : additionalKeys) {
                if (keys.getKeyIdentifier().equals(hash)) {
                    return keys;
                }
            }
        }
        log.warn("Could not find key for hash {} (assuming X25519 only key)", hash);
        if (additionalKeys == null) {
            additionalKeys = new ArrayList<>();
        }
        IdentityKeys key = new IdentityKeys(Map.of(IdentityKeys.X25519_KEY,
                new PublicKey(hash,
                        null,
                        null,
                        null,
                        null)));
        additionalKeys.add(key);
        return key;
    }

    /**
     * Gets the private keys for the given password.
     * Retrieves the private identity and then gets the private keys from the primary keys.
     *
     * @param password The password to use
     * @return The private keys
     * @throws GeneralSecurityException If the password is incorrect or there's an error during key processing
     */
    public IdentityKeys.PrivateKeys getPrivateKeys(String password) throws GeneralSecurityException {
        PrivateIdentity pi = getPrivateIdentity(password);
        return getPrimaryKeys().getPrivateKeys(pi);
    }

    /**
     * Adds the block hash salt to a hash calculation.
     * If the block hash salt is set, adds it to the hash.
     *
     * @param hashCalc The hash calculation to add the salt to
     */
    public void addBlockHashSalt(Hash hashCalc) {
        if (blockHashSalt != null) {
            hashCalc.addBytes(blockHashSalt);
        }
    }

    /**
     * Copies additional data from another encryption identity.
     * Copies primary keys, additional keys, sharing keys, and block hash salt.
     *
     * @param sourceKey The source encryption identity to copy from
     * @throws GeneralSecurityException If the private keys don't match
     */
    public void copyAdditionalData(EncryptionIdentity sourceKey) throws GeneralSecurityException {
        if (!Arrays.equals(privateHash, sourceKey.privateHash))
            throw new GeneralSecurityException("Can not copy properties without already having matching private keys");

        primaryKeys = sourceKey.primaryKeys;
        additionalKeys = sourceKey.additionalKeys;
        sharingKeys = sourceKey.sharingKeys;
        blockHashSalt = sourceKey.blockHashSalt;
        blockHashSaltEncrypted = sourceKey.blockHashSaltEncrypted;
    }

    /**
     * Creates a copy of this identity with a different public primary key.
     * Uses the identity keys for the provided public key as the primary keys.
     *
     * @param publicPrimaryKeys The public primary keys to use
     * @return A new EncryptionIdentity with the specified primary keys
     */
    public EncryptionIdentity copyWithPublicPrimaryKey(IdentityKeys publicPrimaryKeys) {
        EncryptionIdentity ret = onlyPassword();
        ret.primaryKeys = getIdentityKeysForPublicIdentity(publicPrimaryKeys);
        return ret;
    }

    /**
     * Enum defining different formats for key serialization.
     */
    public enum KeyFormat {
        /**
         * Full public key format
         */
        PUBLIC,
        /**
         * Format suitable for uploading to a service
         */
        UPLOAD,
        /**
         * Format suitable for service use
         */
        SERVICE
    }

    /**
     * Inner class representing a private identity.
     * Contains the private key and methods for encrypting and decrypting data.
     */
    public class PrivateIdentity {
        private final byte[] privateKey;
        @Getter(AccessLevel.PROTECTED)
        private final String password;

        /**
         * Constructor for PrivateIdentity.
         *
         * @param password The password used to derive the private key
         * @param privateKey The private key bytes
         */
        public PrivateIdentity(String password, byte[] privateKey) {
            this.privateKey = privateKey;
            this.password = password;
        }

        /**
         * Gets the parent encryption identity.
         *
         * @return The parent encryption identity
         */
        public EncryptionIdentity getEncryptionIdentity() {
            return EncryptionIdentity.this;
        }

        /**
         * Encrypts data using the private key.
         * Uses AES-GCM encryption with a random IV.
         *
         * @param data The data to encrypt
         * @return The encrypted data
         * @throws GeneralSecurityException If there's an error during encryption
         */
        public byte[] encryptKeyData(byte[] data) throws GeneralSecurityException {
            SecretKeySpec secretKeySpec = new SecretKeySpec(privateKey, KEY_ALGORITHM);
            Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);

            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, new GCMParameterSpec(iv.length * 8, iv));
            int estimatedSize = cipher.getOutputSize(data.length);
            byte[] ret = new byte[estimatedSize + iv.length];
            System.arraycopy(iv, 0, ret, 0, iv.length);
            int length = cipher.doFinal(data, 0, data.length, ret, iv.length);
            if (length != estimatedSize) {
                throw new IllegalBlockSizeException("Wrong size of block");
            }
            return ret;
        }

        /**
         * Decrypts data using the private key.
         * Uses AES-GCM decryption with the IV from the encrypted data.
         *
         * @param data The data to decrypt
         * @return The decrypted data
         * @throws GeneralSecurityException If there's an error during decryption
         */
        public byte[] decryptKeyData(byte[] data) throws GeneralSecurityException {
            SecretKeySpec secretKeySpec = new SecretKeySpec(privateKey, KEY_ALGORITHM);
            Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);
            byte[] iv = new byte[12];
            System.arraycopy(data, 0, iv, 0, iv.length);
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, new GCMParameterSpec(iv.length * 8, iv));
            int estimatedSize = cipher.getOutputSize(data.length - iv.length);
            byte[] ret = new byte[estimatedSize];
            int length = cipher.doFinal(data, iv.length, data.length - iv.length, ret, 0);
            if (length != estimatedSize) {
                throw new IllegalBlockSizeException("Wrong size of block");
            }
            return ret;
        }
    }
}
