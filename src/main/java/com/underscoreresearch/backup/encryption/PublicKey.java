package com.underscoreresearch.backup.encryption;

import java.security.GeneralSecurityException;

import lombok.Getter;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

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

    public PublicKey(byte[] publicKey) {
        this.publicKey = publicKey;
        this.publicKeyHash = Hash.hash64(publicKey);
    }

    private PublicKey(byte[] encryptedPublicKey, byte[] encryptedPrivateKey, String publicKeyHash) {
        this.encryptedPrivateKey = encryptedPrivateKey;
        this.encryptedPublicKey = encryptedPublicKey;
        this.publicKeyHash = publicKeyHash;
    }

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

    public PublicKey withoutPublicKey() {
        return new PublicKey(encryptedPublicKey, encryptedPrivateKey, publicKeyHash);
    }

    @JsonProperty("u")
    public String getPublicKeyString() {
        if (publicKey != null) {
            return Hash.encodeBytes64(publicKey);
        }
        return null;
    }

    @JsonProperty("h")
    public String getPublicKeyHashJson() {
        if (publicKey == null) {
            return publicKeyHash;
        }
        return null;
    }

    @JsonIgnore
    public String getPublicKeyHash() {
        return publicKeyHash;
    }

    @JsonProperty("e")
    public String getEncryptedPublicKeyString() {
        if (encryptedPublicKey != null) {
            return Hash.encodeBytes64(encryptedPublicKey);
        }
        return null;
    }

    @JsonProperty("r")
    public String getEncryptedPrivateKey() {
        if (encryptedPrivateKey != null) {
            return Hash.encodeBytes64(encryptedPrivateKey);
        }
        return null;
    }

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

    @Getter
    public class PrivateKey {
        @JsonIgnore
        private final byte[] privateKey;

        private PrivateKey(byte[] privateKey) {
            this.privateKey = privateKey;
        }

        @JsonProperty("u")
        public String getPublicKeyString() {
            if (publicKey != null) {
                return Hash.encodeBytes64(publicKey);
            }
            return null;
        }

        @JsonProperty("p")
        public String getPrivateKeyString() {
            if (privateKey != null) {
                return Hash.encodeBytes64(privateKey);
            }
            return null;
        }

        @JsonIgnore
        public PublicKey getPublicKey() {
            return PublicKey.this;
        }
    }
}
