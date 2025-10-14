package com.underscoreresearch.backup.encryption;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.security.GeneralSecurityException;

/**
 * Represents a public key for encryption operations.
 * This class manages both public and private key data, with private keys
 * being encrypted when stored and only decrypted when needed.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PublicKey {
    @Getter(onMethod_ = {@JsonIgnore})
    private byte[] publicKey;

    @JsonIgnore
    private byte[] encryptedPrivateKey;
    @JsonIgnore
    private byte[] encryptedPublicKey;
    @JsonIgnore
    private String publicKeyHash;

    @JsonIgnore
    private PrivateKey cachedPrivateKey;

    /**
     * Creates a public key with an optional private key, encrypting the private key.
     *
     * @param publicKey The public key bytes
     * @param privateKey The private key bytes, or null if not available
     * @param privateIdentity The identity to use for encrypting the private key
     * @throws GeneralSecurityException If encryption fails
     */
    public PublicKey(byte[] publicKey, byte[] privateKey,
                     EncryptionIdentity.PrivateIdentity privateIdentity) throws GeneralSecurityException {
        this.publicKey = publicKey;
        this.encryptedPublicKey = privateIdentity.encryptKeyData(publicKey);
        this.publicKeyHash = Hash.hash64(publicKey);
        if (privateKey != null) {
            this.cachedPrivateKey = new PrivateKey(privateKey);
            this.encryptedPrivateKey = privateIdentity.encryptKeyData(privateKey);
        } else {
            encryptedPrivateKey = null;
        }
    }

    /**
     * Creates a public key without a private key.
     *
     * @param publicKey The public key bytes
     */
    public PublicKey(byte[] publicKey) {
        this.publicKey = publicKey;
        this.publicKeyHash = Hash.hash64(publicKey);
    }

    /**
     * Creates a public key with encrypted components.
     *
     * @param encryptedPublicKey The encrypted public key bytes
     * @param encryptedPrivateKey The encrypted private key bytes
     * @param publicKeyHash The hash of the public key
     */
    private PublicKey(byte[] encryptedPublicKey, byte[] encryptedPrivateKey, String publicKeyHash) {
        this.encryptedPrivateKey = encryptedPrivateKey;
        this.encryptedPublicKey = encryptedPublicKey;
        this.publicKeyHash = publicKeyHash;
    }

    /**
     * Creates a public key from JSON properties.
     *
     * @param publicKey The public key string in base64 encoding
     * @param encryptedPublicKey The encrypted public key string in base64 encoding
     * @param publicKeyHash The hash of the public key
     * @param encryptedPrivateKey The encrypted private key string in base64 encoding
     * @param privateKey The private key string in base64 encoding
     */
    @JsonCreator
    public PublicKey(@JsonProperty("u") String publicKey,
                     @JsonProperty("e") String encryptedPublicKey,
                     @JsonProperty("h") String publicKeyHash,
                     @JsonProperty("r") String encryptedPrivateKey,
                     @JsonProperty("p") String privateKey) {
        if (publicKey != null) {
            this.publicKey = Hash.decodeBytes64(publicKey);
            this.publicKeyHash = Hash.hash64(this.publicKey);
        } else {
            this.publicKey = null;
            this.publicKeyHash = publicKeyHash;
        }
        if (encryptedPublicKey != null)
            this.encryptedPublicKey = Hash.decodeBytes64(encryptedPublicKey);
        else
            this.encryptedPublicKey = null;
        if (encryptedPrivateKey != null)
            this.encryptedPrivateKey = Hash.decodeBytes64(encryptedPrivateKey);
        else
            this.encryptedPrivateKey = null;

        if (privateKey != null) {
            this.cachedPrivateKey = new PrivateKey(Hash.decodeBytes64(privateKey));
        }
    }

    /**
     * Creates a copy of this public key without the actual public key data.
     *
     * @return A new public key with only encrypted data and hash
     */
    public PublicKey withoutPublicKey() {
        return new PublicKey(encryptedPublicKey, encryptedPrivateKey, publicKeyHash);
    }

    /**
     * Gets the public key as a base64 encoded string.
     *
     * @return The public key string, or null if not available
     */
    @JsonProperty("u")
    public String getPublicKeyString() {
        if (publicKey != null) {
            return Hash.encodeBytes64(publicKey);
        }
        return null;
    }

    /**
     * Gets the public key hash for JSON serialization.
     *
     * @return The public key hash, or null if the public key is available
     */
    @JsonProperty("h")
    public String getPublicKeyHashJson() {
        if (publicKey == null) {
            return publicKeyHash;
        }
        return null;
    }

    /**
     * Gets the hash of the public key.
     *
     * @return The public key hash
     */
    @JsonIgnore
    public String getPublicKeyHash() {
        return publicKeyHash;
    }

    /**
     * Gets the encrypted public key as a base64 encoded string.
     *
     * @return The encrypted public key string, or null if not available
     */
    @JsonProperty("e")
    public String getEncryptedPublicKeyString() {
        if (encryptedPublicKey != null) {
            return Hash.encodeBytes64(encryptedPublicKey);
        }
        return null;
    }

    /**
     * Gets the encrypted private key as a base64 encoded string.
     *
     * @return The encrypted private key string, or null if not available
     */
    @JsonProperty("r")
    public String getEncryptedPrivateKey() {
        if (encryptedPrivateKey != null) {
            return Hash.encodeBytes64(encryptedPrivateKey);
        }
        return null;
    }

    /**
     * Gets the private key, decrypting it if necessary.
     *
     * @param privateIdentity The identity to use for decryption
     * @return The private key
     * @throws GeneralSecurityException If decryption fails
     */
    @JsonIgnore
    public PrivateKey getPrivateKey(EncryptionIdentity.PrivateIdentity privateIdentity) throws GeneralSecurityException {
        if (publicKey == null) {
            publicKey = privateIdentity.decryptKeyData(encryptedPublicKey);
        } else if (encryptedPublicKey == null) {
            encryptedPublicKey = privateIdentity.encryptKeyData(publicKey);
        }
        if (cachedPrivateKey != null) {
            if (encryptedPrivateKey == null) {
                encryptedPrivateKey = privateIdentity.encryptKeyData(cachedPrivateKey.privateKey);
            }
            return cachedPrivateKey;
        }
        cachedPrivateKey = new PrivateKey(privateIdentity.decryptKeyData(encryptedPrivateKey));
        return cachedPrivateKey;
    }

    /**
     * Represents a private key associated with a public key.
     */
    @Getter
    public class PrivateKey {
        @JsonIgnore
        private final byte[] privateKey;

        /**
         * Creates a private key.
         *
         * @param privateKey The private key bytes
         */
        private PrivateKey(byte[] privateKey) {
            this.privateKey = privateKey;
        }

        /**
         * Gets the public key as a base64 encoded string.
         *
         * @return The public key string, or null if not available
         */
        @JsonProperty("u")
        public String getPublicKeyString() {
            if (publicKey != null) {
                return Hash.encodeBytes64(publicKey);
            }
            return null;
        }

        /**
         * Gets the private key as a base64 encoded string.
         *
         * @return The private key string, or null if not available
         */
        @JsonProperty("p")
        public String getPrivateKeyString() {
            if (privateKey != null) {
                return Hash.encodeBytes64(privateKey);
            }
            return null;
        }

        /**
         * Gets the associated public key.
         *
         * @return The public key
         */
        @JsonIgnore
        public PublicKey getPublicKey() {
            return PublicKey.this;
        }
    }
}
