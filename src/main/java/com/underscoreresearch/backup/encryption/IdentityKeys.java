package com.underscoreresearch.backup.encryption;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.underscoreresearch.backup.encryption.encryptors.kyber.KyberKeyMethod;
import com.underscoreresearch.backup.encryption.encryptors.x25519.X25519;
import com.underscoreresearch.backup.encryption.encryptors.x25519.X25519KeyMethod;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static com.underscoreresearch.backup.encryption.encryptors.AesEncryptorFormat.PUBLIC_KEY;
import static com.underscoreresearch.backup.encryption.encryptors.BaseAesEncryptor.applyKeyData;
import static com.underscoreresearch.backup.utils.SerializationUtils.MAPPER;
import static java.util.stream.Collectors.toMap;

/**
 * Manages a collection of public and private keys for encryption operations.
 * This class handles key generation, encryption parameters, and key management
 * for different encryption algorithms.
 */
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IdentityKeys {
    public final static String X25519_KEY = PUBLIC_KEY;
    public final static String KYBER_KEY = "y";
    public static final int SYMMETRIC_KEY_SIZE = 32;
    private final static X25519KeyMethod X25519_KEY_METHOD = new X25519KeyMethod();
    private final static KyberKeyMethod KYBER_KEY_METHOD = new KyberKeyMethod();
    private final static ObjectReader READER = MAPPER.readerFor(IdentityKeys.class);
    private final static ObjectWriter WRITER = MAPPER.writerFor(IdentityKeys.class);
    private final static ObjectWriter PRIVATE_WRITER = MAPPER.writerFor(IdentityKeys.PrivateIdentityKeys.class);
    private final static ObjectReader LEGACY_READER = MAPPER.readerFor(LegacyEncryptionKey.class);
    @JsonProperty("k")
    private Map<String, PublicKey> keys;

    /**
     * Constructor for IdentityKeys.
     * Creates an instance with the specified map of key types to public keys.
     *
     * @param keys Map of key types to public keys
     */
    @JsonCreator
    public IdentityKeys(@JsonProperty("k") Map<String, PublicKey> keys) {
        this.keys = keys;
    }

    /**
     * Creates identity keys with both X25519 and Kyber key pairs.
     * Generates new key pairs using the provided private identity.
     *
     * @param privateIdentity The private identity to use for key generation
     * @return A new IdentityKeys instance with generated key pairs
     * @throws GeneralSecurityException If there's an error during key generation
     */
    public static IdentityKeys createIdentityKeys(EncryptionIdentity.PrivateIdentity privateIdentity)
            throws GeneralSecurityException {
        return new IdentityKeys(Map.of(
                X25519_KEY, X25519_KEY_METHOD.createKeyPair(privateIdentity),
                KYBER_KEY, KYBER_KEY_METHOD.createKeyPair(privateIdentity)
        ));
    }

    /**
     * Creates identity keys with an X25519 key pair from an existing private key.
     * Uses the provided private key to generate the public key.
     *
     * @param privateIdentity The private identity to use for key encryption
     * @param privateKey The existing private key to use
     * @return A new IdentityKeys instance with the generated key pair
     * @throws GeneralSecurityException If there's an error during key generation
     */
    public static IdentityKeys createIdentityKeys(EncryptionIdentity.PrivateIdentity privateIdentity, byte[] privateKey)
            throws GeneralSecurityException {
        return new IdentityKeys(Map.of(
                X25519_KEY, new PublicKey(X25519.publicFromPrivate(privateKey), privateKey, privateIdentity)));
    }

    /**
     * Creates identity keys from a string representation.
     * Supports multiple formats: JSON, legacy format, and raw key formats.
     *
     * @param str The string representation of the keys
     * @param privateIdentity The private identity to use for key unpacking (can be null)
     * @return A new IdentityKeys instance parsed from the string
     * @throws GeneralSecurityException If there's an error during parsing or key processing
     */
    public static IdentityKeys fromString(String str, EncryptionIdentity.PrivateIdentity privateIdentity)
            throws GeneralSecurityException {
        if (str.startsWith("{")) {
            try {
                IdentityKeys ret = READER.readValue(str);
                if (privateIdentity != null) {
                    ret.unpackKeys(privateIdentity);
                }
                return ret;
            } catch (Exception e) {
                try {
                    LegacyEncryptionKey key = LEGACY_READER.readValue(str);
                    if (key.getPrivateKeySerializing() != null) {
                        byte[] privateKey = key.getPrivateKey(null).getPrivateKey();
                        byte[] publicKey = X25519.publicFromPrivate(privateKey);
                        return new IdentityKeys(Map.of(
                                X25519_KEY, new PublicKey(publicKey, privateKey, privateIdentity)
                        ));
                    }
                } catch (JsonProcessingException ignored) {
                }
                throw new GeneralSecurityException(e);
            }
        } else if (str.startsWith("=")) {
            byte[] privateKey = Hash.decodeBytes(str.substring(1));
            byte[] publicKey = X25519.publicFromPrivate(privateKey);

            return new IdentityKeys(Map.of(
                    X25519_KEY, new PublicKey(publicKey, privateKey, privateIdentity)
            ));
        } else {
            byte[] publicKey = Hash.decodeBytes(str);
            return new IdentityKeys(Map.of(
                    X25519_KEY, new PublicKey(publicKey)
            ));
        }
    }

    /**
     * Gets encryption parameters for the specified key types.
     * Generates a shared secret and encapsulated keys for each key type.
     *
     * @param keysToUse Set of key types to use for encryption
     * @return Encryption parameters containing the secret and encapsulated keys
     * @throws GeneralSecurityException If there's an error during key processing or if a key type is not available
     */
    public EncryptionParameters getEncryptionParameters(Set<String> keysToUse) throws GeneralSecurityException {
        byte[] secret = null;
        Map<String, PublicKeyMethod.EncapsulatedKey> encapsulatedKeys = new HashMap<>();
        for (String keyType : keysToUse) {
            PublicKeyMethod method = switch (keyType) {
                case X25519_KEY -> X25519_KEY_METHOD;
                case KYBER_KEY -> KYBER_KEY_METHOD;
                default -> throw new InvalidKeyException("Unknown key type: " + keyType);
            };
            PublicKey publicKey = keys.get(keyType);
            if (publicKey == null) {
                throw new InvalidKeyException("No key of type: " + keyType);
            }
            PublicKeyMethod.GeneratedKey generatedKey = method.generateNewSecret(publicKey);
            if (secret != null) {
                secret = applyKeyData(secret, generatedKey.getSecret());
            } else {
                secret = generatedKey.getSecret();
            }
            encapsulatedKeys.put(keyType, generatedKey);
        }
        return new EncryptionParameters(secret, encapsulatedKeys);
    }

    /**
     * Gets the key identifier.
     * Returns the Base64 encoded X25519 public key.
     *
     * @return The key identifier as a Base64 encoded string
     */
    @JsonIgnore
    public String getKeyIdentifier() {
        return Hash.encodeBytes(keys.get(X25519_KEY).getPublicKey());
    }

    /**
     * Gets the public key hash.
     * Returns the hash of the X25519 public key.
     *
     * @return The public key hash
     */
    @JsonIgnore
    public String getPublicKeyHash() {
        return keys.get(X25519_KEY).getPublicKeyHash();
    }

    /**
     * Returns a string representation of the identity keys.
     * Serializes the keys to a JSON string.
     *
     * @return JSON string representation of the identity keys
     * @throws RuntimeException If there's an error during serialization
     */
    @Override
    public String toString() {
        try {
            return WRITER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Creates a copy of this identity keys without public keys.
     * Useful for creating a version with only key hashes for storage or transmission.
     *
     * @return A new IdentityKeys instance without public keys
     */
    public IdentityKeys withoutPublicKeys() {
        return new IdentityKeys(keys.entrySet().stream()
                .map(entry -> Map.entry(entry.getKey(),
                        entry.getValue().withoutPublicKey()))
                .collect(toMap(Map.Entry::getKey, Map.Entry::getValue)));
    }

    /**
     * Unpacks encrypted keys using the provided private identity.
     * Decrypts all keys and ensures a Kyber key is available.
     *
     * @param privateIdentity The private identity to use for unpacking
     * @throws GeneralSecurityException If there's an error during key decryption
     */
    public void unpackKeys(EncryptionIdentity.PrivateIdentity privateIdentity) throws GeneralSecurityException {
        for (PublicKey key : keys.values()) {
            key.getPrivateKey(privateIdentity);
        }
        if (keys.get(KYBER_KEY) == null) {
            keys = Map.of(X25519_KEY, keys.get(X25519_KEY),
                    KYBER_KEY, KYBER_KEY_METHOD.createKeyPair(privateIdentity));
        }
    }

    /**
     * Gets the private keys for the provided private identity.
     * Creates a PrivateKeys instance that can be used for decryption.
     *
     * @param privateIdentity The private identity to use
     * @return A PrivateKeys instance for decryption operations
     */
    public PrivateKeys getPrivateKeys(EncryptionIdentity.PrivateIdentity privateIdentity) {
        return new PrivateKeys(privateIdentity);
    }

    /**
     * Changes the encryption of the private keys.
     * Re-encrypts all private keys using a new private identity.
     *
     * @param existingPI The existing private identity for decryption
     * @param newPI The new private identity for encryption
     * @return A new IdentityKeys instance with re-encrypted private keys
     * @throws GeneralSecurityException If there's an error during key processing
     */
    public IdentityKeys changeEncryption(EncryptionIdentity.PrivateIdentity existingPI,
                                         EncryptionIdentity.PrivateIdentity newPI)
            throws GeneralSecurityException {
        Map<String, PublicKey> map = new HashMap<>();
        for (Map.Entry<String, PublicKey> stringPublicKeyEntry : keys.entrySet()) {
            PublicKey.PrivateKey pk = stringPublicKeyEntry.getValue().getPrivateKey(existingPI);
            map.put(stringPublicKeyEntry.getKey(),
                    new PublicKey(pk.getPublicKey().getPublicKey(), pk.getPrivateKey(), newPI));
        }
        return new IdentityKeys(map);
    }

    /**
     * Checks if any of the keys have an encrypted private key.
     *
     * @return true if at least one key has an encrypted private key, false otherwise
     */
    @JsonIgnore
    public boolean hasPrivateKey() {
        return keys.values().stream().anyMatch(t -> t.getEncryptedPrivateKey() != null);
    }

    /**
     * Checks if any of the keys need unpacking.
     * A key needs unpacking if its public key is null.
     *
     * @return true if at least one key needs unpacking, false otherwise
     */
    @JsonIgnore
    public boolean needKeyUnpack() {
        return keys.values().stream().anyMatch(t -> t.getPublicKey() == null);
    }

    /**
     * Gets a string representation of the private keys.
     * Serializes the private keys to a JSON string.
     *
     * @param privateIdentity The private identity to use for accessing private keys
     * @return JSON string representation of the private keys
     * @throws GeneralSecurityException If there's an error accessing the private keys
     * @throws RuntimeException If there's an error during serialization
     */
    @JsonIgnore
    public String getPrivateKeyString(EncryptionIdentity.PrivateIdentity privateIdentity) throws GeneralSecurityException {
        try {
            return PRIVATE_WRITER.writeValueAsString(new PrivateIdentityKeys(keys, privateIdentity));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Gets a string representation of the public keys only.
     * Creates a new IdentityKeys with only public keys and serializes it to a JSON string.
     *
     * @return JSON string representation of the public keys
     * @throws RuntimeException If there's an error during serialization
     */
    @JsonIgnore
    public String getPublicKeyString() {
        try {
            TreeMap<String, PublicKey> sortedKeys = new TreeMap<>();
            keys.forEach((key, value) -> sortedKeys.put(key, new PublicKey(value.getPublicKey())));
            return WRITER.writeValueAsString(new IdentityKeys(sortedKeys));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Converts this IdentityKeys to a public EncryptionIdentity.
     * Creates an EncryptionIdentity with only public keys.
     *
     * @return A new EncryptionIdentity with only public keys
     */
    public EncryptionIdentity toPublicEncryptionIdentity() {
        EncryptionIdentity ret = new EncryptionIdentity();
        ret.primaryKeys = new IdentityKeys(keys.entrySet().stream()
                .map(entry -> Map.entry(entry.getKey(),
                        new PublicKey(entry.getValue().getPublicKey())))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
        return ret;
    }

    /**
     * Compares this IdentityKeys with another object for equality.
     * Two IdentityKeys are considered equal if they have the same X25519 public key hash.
     *
     * @param o The object to compare with
     * @return true if the objects are equal, false otherwise
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IdentityKeys that = (IdentityKeys) o;
        PublicKey k1 = keys.get(X25519_KEY);
        PublicKey k2 = that.keys.get(X25519_KEY);
        return Objects.equals(k1.getPublicKeyHash(), k2.getPublicKeyHash());
    }

    /**
     * Generates a hash code for this IdentityKeys.
     * The hash code is based on the X25519 public key string and encrypted public key string.
     *
     * @return The hash code
     */
    @Override
    public int hashCode() {
        PublicKey k1 = keys.get(X25519_KEY);
        return Objects.hash(k1.getPublicKeyString(), k1.getEncryptedPublicKeyString());
    }

    /**
     * Private class for serializing private identity keys.
     * This is used for non-service managed sharing of private keys.
     */
    private static class PrivateIdentityKeys {
        @JsonProperty("k")
        Map<String, PublicKey.PrivateKey> keys;

        /**
         * Constructor for PrivateIdentityKeys.
         * Creates an instance with private keys extracted from the provided public keys.
         *
         * @param keys Map of key types to public keys
         * @param privateIdentity The private identity to use for accessing private keys
         * @throws GeneralSecurityException If there's an error accessing the private keys
         */
        public PrivateIdentityKeys(Map<String, PublicKey> keys, EncryptionIdentity.PrivateIdentity privateIdentity)
                throws GeneralSecurityException {
            Map<String, PublicKey.PrivateKey> map = new TreeMap<>();
            for (Map.Entry<String, PublicKey> entry : keys.entrySet()) {
                map.put(entry.getKey(), entry.getValue().getPrivateKey(privateIdentity));
            }
            this.keys = map;
        }
    }

    /**
     * Class representing encryption parameters for secure data exchange.
     * Contains a shared secret and encapsulated keys used for encryption.
     */
    @AllArgsConstructor
    @Getter
    public static class EncryptionParameters {
        /**
         * The shared secret used for symmetric encryption.
         */
        private final byte[] secret;
        
        /**
         * Map of key types to their corresponding encapsulated keys.
         * These encapsulated keys are used by the recipient to recreate the shared secret.
         */
        private final Map<String, PublicKeyMethod.EncapsulatedKey> keys;
    }

    /**
     * Class providing access to private keys for decryption operations.
     * Acts as a wrapper around the private identity to perform operations requiring private keys.
     */
    @Getter
    @RequiredArgsConstructor
    public class PrivateKeys {
        /**
         * The private identity used to access encrypted private keys.
         */
        private final EncryptionIdentity.PrivateIdentity privateIdentity;

        /**
         * Gets the identity that owns these private keys.
         *
         * @return The parent IdentityKeys instance
         */
        public IdentityKeys getIdentity() {
            return IdentityKeys.this;
        }

        /**
         * Gets the private key for the specified key type.
         *
         * @param type The key type to get the private key for
         * @return The private key
         * @throws GeneralSecurityException If there's an error accessing the private key
         */
        public PublicKey.PrivateKey getPrivateKey(String type) throws GeneralSecurityException {
            PublicKey key = IdentityKeys.this.keys.get(type);
            return key.getPrivateKey(privateIdentity);
        }

        /**
         * Recreates the shared secret from encapsulated keys.
         * Uses the private keys to decrypt the encapsulated keys and recreate the original secret.
         *
         * @param encapsulatedKeys Map of key types to encapsulated keys
         * @return The recreated secret
         * @throws GeneralSecurityException If there's an error during decryption or if a key type is not available
         */
        public byte[] recreateSecret(Map<String, PublicKeyMethod.EncapsulatedKey> encapsulatedKeys)
                throws GeneralSecurityException {
            byte[] combinedSecret = null;
            for (Map.Entry<String, PublicKeyMethod.EncapsulatedKey> entry : encapsulatedKeys.entrySet()) {
                PublicKeyMethod method = switch (entry.getKey()) {
                    case X25519_KEY -> X25519_KEY_METHOD;
                    case KYBER_KEY -> KYBER_KEY_METHOD;
                    default -> throw new InvalidKeyException("Unknown key type: " + entry.getKey());
                };
                PublicKey publicKey = keys.get(entry.getKey());
                if (publicKey == null) {
                    throw new InvalidKeyException("No key of type: " + entry.getKey());
                }
                PublicKey.PrivateKey privateKey = publicKey.getPrivateKey(privateIdentity);
                byte[] secret = method.recreateSecret(privateKey, entry.getValue());
                if (combinedSecret != null) {
                    combinedSecret = applyKeyData(combinedSecret, secret);
                } else {
                    combinedSecret = secret;
                }
            }
            return combinedSecret;
        }
    }
}
