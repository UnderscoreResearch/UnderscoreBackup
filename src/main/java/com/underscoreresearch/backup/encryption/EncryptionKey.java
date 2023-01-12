package com.underscoreresearch.backup.encryption;

import static com.underscoreresearch.backup.configuration.EncryptionModule.ROOT_KEY;
import static com.underscoreresearch.backup.encryption.AesEncryptor.applyKeyData;
import static com.underscoreresearch.backup.utils.SerializationUtils.ENCRYPTION_KEY_READER;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.Objects;

import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.x25519.X25519;
import com.underscoreresearch.backup.manifest.AdditionalKeyManager;
import com.underscoreresearch.backup.manifest.ManifestManager;
import com.underscoreresearch.backup.manifest.implementation.AdditionalKeyManagerImpl;

@Slf4j
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
public class EncryptionKey {
    private static final int FIXED_ITERATIONS = 64 * 1024;
    private static final String DISPLAY_PREFIX = "=";
    private byte[] publicKey;
    private byte[] salt;
    private byte[] passphraseKey;
    private byte[] keyData;
    @Getter
    @Setter
    private String encryptedAdditionalKeys;
    @JsonIgnore
    private PrivateKey cachedPrivateKey;

    public static byte[] combinedSecret(PrivateKey privateKey, EncryptionKey publicKey) {
        try {
            return X25519.computeSharedSecret(privateKey.privateKey, publicKey.publicKey);
        } catch (InvalidKeyException e) {
            throw new RuntimeException(e);
        }
    }

    public static EncryptionKey generateKeys() {
        EncryptionKey ret = new EncryptionKey();
        ret.cachedPrivateKey = new PrivateKey(null, X25519.generatePrivateKey(), ret);
        try {
            ret.publicKey = X25519.publicFromPrivate(ret.cachedPrivateKey.privateKey);
        } catch (InvalidKeyException e) {
            throw new RuntimeException(e);
        }
        return ret;
    }

    public static EncryptionKey changeEncryptionPassphrase(String oldPassphrase, String newPassphrase, EncryptionKey key) {
        PrivateKey oldPrivateKey = key.getPrivateKey(oldPassphrase);

        EncryptionKey ret = generateKeyWithPassphrase(newPassphrase);
        ret.keyData = applyKeyData(applyKeyData(oldPrivateKey.privateKey, ret.getPrivateKey(newPassphrase).privateKey), ret.keyData);
        ret.publicKey = oldPrivateKey.getParent().publicKey;
        ret.cachedPrivateKey.privateKey = oldPrivateKey.privateKey;
        ret.cachedPrivateKey.keyManager = oldPrivateKey.keyManager;
        ret.encryptedAdditionalKeys = key.encryptedAdditionalKeys;
        return ret;
    }

    public static EncryptionKey generateKeyWithPassphrase(String passphrase) {
        try {
            byte[] saltData = new byte[32];
            SecureRandom random = new SecureRandom();
            random.nextBytes(saltData);

            byte[] bytes = getPasswordDerivative(passphrase, saltData);

            EncryptionKey ret = new EncryptionKey();
            ret.keyData = new byte[bytes.length];
            random.nextBytes(ret.keyData);
            byte[] privateKey = applyKeyData(ret.keyData, bytes);
            makePrivateKey(privateKey);
            ret.cachedPrivateKey = new PrivateKey(passphrase, privateKey, ret);
            ret.salt = saltData;
            ret.publicKey = X25519.publicFromPrivate(privateKey);
            return ret;
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | InvalidKeyException e) {
            throw new RuntimeException(e);
        }
    }

    private static void makePrivateKey(byte[] privateKey) {
        privateKey[0] = (byte) (privateKey[0] | 7);
        privateKey[31] = (byte) (privateKey[31] & 63);
        privateKey[31] = (byte) (privateKey[31] | 128);

    }

    private static byte[] getPasswordDerivative(String passphrase, byte[] saltData) throws NoSuchAlgorithmException, InvalidKeySpecException {
        PBEKeySpec spec = new PBEKeySpec(passphrase.toCharArray(), saltData, FIXED_ITERATIONS, 32 * 8);
        SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
        return skf.generateSecret(spec).getEncoded();
    }

    public static EncryptionKey createWithPublicKey(String publicKey) {
        EncryptionKey ret = new EncryptionKey();
        ret.setPublicKey(publicKey);
        return ret;
    }

    public static EncryptionKey createWithKeyData(String keyData) throws InvalidKeyException, JsonProcessingException {
        if (keyData.startsWith(DISPLAY_PREFIX)) {
            return createWithPrivateKey(keyData);
        }
        return ENCRYPTION_KEY_READER.readValue(keyData);
    }

    public static EncryptionKey createWithPrivateKey(String privateKey) throws InvalidKeyException {
        if (!privateKey.startsWith(DISPLAY_PREFIX)) {
            throw new IllegalArgumentException("Invalid private key string. Should start with \"p-\"");
        }
        EncryptionKey ret = new EncryptionKey();
        ret.cachedPrivateKey = new PrivateKey(null, Hash.decodeBytes(privateKey.substring(DISPLAY_PREFIX.length())), ret);
        ret.publicKey = X25519.publicFromPrivate(ret.cachedPrivateKey.privateKey);
        return ret;
    }

    @JsonProperty("privateKey")
    public String getPrivateKeySerializing() {
        if (cachedPrivateKey != null && cachedPrivateKey.passphrase == null)
            return Hash.encodeBytes(cachedPrivateKey.privateKey);
        return null;
    }

    @JsonProperty("privateKey")
    public void setPrivateKeySerializing(String privateKey) {
        if (privateKey != null)
            this.cachedPrivateKey = new PrivateKey(null, Hash.decodeBytes(privateKey), this);
        else
            this.cachedPrivateKey = null;
    }

    @JsonProperty("keyData")
    public void setKeyData(String xor) {
        if (xor != null)
            keyData = Hash.decodeBytes(xor);
        else
            keyData = null;
    }

    @JsonProperty("keyData")
    public String getKeyData() {
        if (keyData != null)
            return Hash.encodeBytes(keyData);
        return null;
    }

    @JsonIgnore
    public PrivateKey getPrivateKey(String passphrase) {
        if (cachedPrivateKey == null || !Objects.equals(cachedPrivateKey.getPassphrase(), passphrase)) {
            try {
                if (getSalt() == null) {
                    EncryptionKey rootKey = InstanceFactory.getInstance(ROOT_KEY, EncryptionKey.class);
                    EncryptionKey key = rootKey.getPrivateKey(passphrase)
                            .getAdditionalKeyManager().findMatchingPrivateKey(this);
                    if (key != null) {
                        cachedPrivateKey = new PrivateKey(passphrase, key.getPrivateKey(null).privateKey,
                                this);
                        return cachedPrivateKey;
                    }
                }
                byte[] saltData = Hash.decodeBytes(getSalt());

                byte[] bytes = getPasswordDerivative(passphrase, saltData);

                if (keyData != null) {
                    bytes = applyKeyData(bytes, keyData);
                    makePrivateKey(bytes);
                } else if (passphraseKey != null) {
                    byte[] privateKey = bytes;
                    makePrivateKey(privateKey);
                    AesEncryptor aesEncryptor = new AesEncryptor();
                    bytes = aesEncryptor.decodeBlock(null, passphraseKey,
                            new PrivateKey(null, privateKey, null));

                    // We will migrate key data in place here so anytime the key is written anywhere it will have the
                    // keyData format.
                    keyData = applyKeyData(privateKey, bytes);
                    passphraseKey = null;
                }

                byte[] publicKey = X25519.publicFromPrivate(bytes);
                if (this.publicKey != null) {
                    if (!Arrays.equals(publicKey, this.publicKey)) {
                        throw new InvalidKeyException();
                    }
                } else {
                    this.publicKey = publicKey;
                }

                cachedPrivateKey = new PrivateKey(passphrase, bytes, this);
            } catch (InvalidKeyException | NoSuchAlgorithmException | InvalidKeySpecException | IOException e) {
                throw new IllegalArgumentException("Could not unpack private key with passphrase");
            }
        }
        return cachedPrivateKey;
    }

    @JsonProperty
    public String getPublicKey() {
        if (publicKey != null)
            return Hash.encodeBytes(publicKey);
        return null;
    }

    @JsonProperty
    public void setPublicKey(String publicKey) {
        if (publicKey != null)
            this.publicKey = Hash.decodeBytes(publicKey);
        else
            this.publicKey = null;
    }

    @JsonProperty
    public String getSalt() {
        if (salt != null)
            return Hash.encodeBytes(salt);
        return null;
    }

    @JsonProperty
    public void setSalt(String salt) {
        if (salt != null)
            this.salt = Hash.decodeBytes(salt);
        else
            this.salt = null;
    }

    @JsonProperty
    public String getPassphraseKey() {
        if (passphraseKey != null)
            return Hash.encodeBytes(passphraseKey);
        return null;
    }

    @JsonProperty
    public void setPassphraseKey(String passphraseKey) {
        if (passphraseKey != null)
            this.passphraseKey = Hash.decodeBytes(passphraseKey);
        else
            this.passphraseKey = null;
    }

    public EncryptionKey publicOnly() {
        EncryptionKey ret = new EncryptionKey();
        ret.publicKey = publicKey;
        ret.salt = salt;
        ret.passphraseKey = passphraseKey;
        ret.keyData = keyData;
        ret.encryptedAdditionalKeys = encryptedAdditionalKeys;
        return ret;
    }

    public EncryptionKey shareableKey() {
        EncryptionKey ret = new EncryptionKey();
        ret.publicKey = publicKey;
        return ret;
    }

    @Data
    public static class PrivateKey {
        private final String passphrase;
        private final EncryptionKey parent;
        private byte[] privateKey;
        private AdditionalKeyManagerImpl keyManager;


        PrivateKey(String passphrase, byte[] privateKey, EncryptionKey parent) {
            this.passphrase = passphrase;
            this.privateKey = privateKey;
            this.parent = parent;
        }

        @JsonProperty
        public String getPrivateKey() {
            if (privateKey != null)
                return Hash.encodeBytes(privateKey);
            return null;
        }

        @JsonProperty
        public void setPrivateKey(String privateKey) {
            if (privateKey != null)
                this.privateKey = Hash.decodeBytes(privateKey);
            else
                this.privateKey = null;
        }

        @JsonIgnore
        public String getDisplayPrivateKey() {
            if (privateKey != null)
                return DISPLAY_PREFIX + Hash.encodeBytes(privateKey);
            return null;
        }

        @JsonIgnore
        public AdditionalKeyManager getAdditionalKeyManager() throws IOException {
            if (keyManager == null) {
                keyManager = new AdditionalKeyManagerImpl(this);
            }
            return keyManager;
        }
    }
}
