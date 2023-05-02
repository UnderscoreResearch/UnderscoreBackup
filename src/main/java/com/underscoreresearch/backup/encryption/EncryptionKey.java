package com.underscoreresearch.backup.encryption;

import static com.underscoreresearch.backup.configuration.EncryptionModule.ROOT_KEY;
import static com.underscoreresearch.backup.encryption.AesEncryptor.applyKeyData;
import static com.underscoreresearch.backup.utils.SerializationUtils.ENCRYPTION_KEY_READER;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.Objects;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.codec.binary.Base64;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.x25519.X25519;
import com.underscoreresearch.backup.manifest.AdditionalKeyManager;
import com.underscoreresearch.backup.manifest.ManifestManager;
import com.underscoreresearch.backup.manifest.implementation.AdditionalKeyManagerImpl;

import de.mkammerer.argon2.Argon2Advanced;
import de.mkammerer.argon2.Argon2Factory;

@Slf4j
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
public class EncryptionKey {
    public static final String DISPLAY_PREFIX = "=";
    private static final int LEGACY_ITERATIONS = 64 * 1024;
    private static final String CURRENT_ALGORITHM = "ARGON2";

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

    public static EncryptionKey changeEncryptionPassword(String oldPassword, String newPassword, EncryptionKey key) {
        PrivateKey oldPrivateKey = key.getPrivateKey(oldPassword);

        EncryptionKey ret = generateKeyWithPassword(newPassword);
        ret.keyData = applyKeyData(applyKeyData(oldPrivateKey.privateKey, ret.getPrivateKey(newPassword).privateKey), ret.keyData);
        ret.publicKey = oldPrivateKey.getParent().publicKey;
        ret.cachedPrivateKey.privateKey = oldPrivateKey.privateKey;
        ret.cachedPrivateKey.keyManager = oldPrivateKey.keyManager;
        ret.encryptedAdditionalKeys = key.encryptedAdditionalKeys;
        ret.sharingPublicKey = key.sharingPublicKey;
        return ret;
    }

    public static EncryptionKey generateKeyWithPassword(String password) {
        try {
            byte[] saltData = new byte[32];
            SecureRandom random = new SecureRandom();
            random.nextBytes(saltData);

            EncryptionKey ret = new EncryptionKey();
            ret.algorithm = CURRENT_ALGORITHM;

            byte[] bytes = getPasswordDerivative(ret.algorithm, password, saltData);

            ret.keyData = new byte[bytes.length];
            random.nextBytes(ret.keyData);
            byte[] privateKey = applyKeyData(ret.keyData, bytes);
            makePrivateKey(privateKey);
            ret.cachedPrivateKey = new PrivateKey(password, privateKey, ret);
            ret.salt = saltData;
            ret.publicKey = X25519.publicFromPrivate(privateKey);
            try {
                ret.cachedPrivateKey.populateSharingKey(null);
            } catch (IOException exc) {
                throw new InvalidKeyException(exc);
            }
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
        if (cachedPrivateKey != null && cachedPrivateKey.password == null)
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
    public String getKeyData() {
        if (keyData != null)
            return Hash.encodeBytes(keyData);
        return null;
    }

    @JsonProperty("keyData")
    public void setKeyData(String xor) {
        if (xor != null)
            keyData = Hash.decodeBytes(xor);
        else
            keyData = null;
    }

    @JsonIgnore
    public PrivateKey getPrivateKey(String password) {
        if (cachedPrivateKey == null || !Objects.equals(cachedPrivateKey.getPassword(), password)) {
            try {
                if (getSalt() == null) {
                    EncryptionKey rootKey = InstanceFactory.getInstance(ROOT_KEY, EncryptionKey.class);
                    EncryptionKey key = rootKey.getPrivateKey(password)
                            .getAdditionalKeyManager().findMatchingPrivateKey(this);
                    if (key != null) {
                        cachedPrivateKey = new PrivateKey(password, key.getPrivateKey(null).privateKey,
                                this);
                        return cachedPrivateKey;
                    }
                }
                byte[] saltData = Hash.decodeBytes(getSalt());

                byte[] bytes = getPasswordDerivative(algorithm, password, saltData);

                if (keyData != null) {
                    bytes = applyKeyData(bytes, keyData);
                    makePrivateKey(bytes);
                } else if (passwordKey != null) {
                    byte[] privateKey = bytes;
                    makePrivateKey(privateKey);
                    AesEncryptor aesEncryptor = new AesEncryptor();
                    bytes = aesEncryptor.decodeBlock(null, passwordKey,
                            new PrivateKey(null, privateKey, null));

                    // We will migrate key data in place here so anytime the key is written anywhere it will have the
                    // keyData format.
                    keyData = applyKeyData(privateKey, bytes);
                    passwordKey = null;
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
            } catch (InvalidKeyException | NoSuchAlgorithmException | InvalidKeySpecException | IOException e) {
                throw new IllegalArgumentException("Could not unpack private key with password");
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
    public String getPublicKeyHash() {
        if (publicKey != null)
            publicKeyHash = Hash.hash64(publicKey);
        return publicKeyHash;
    }

    @JsonProperty
    public void setPublicKeyHash(String publicKeyHash) {
        if (publicKeyHash != null)
            this.publicKeyHash = publicKeyHash;
        else
            this.publicKeyHash = null;
    }

    @JsonProperty
    public String getSharingPublicKey() {
        if (sharingPublicKey != null)
            return Hash.encodeBytes(sharingPublicKey);
        return null;
    }

    @JsonProperty
    public void setSharingPublicKey(String sharingPublicKey) {
        if (sharingPublicKey != null)
            this.sharingPublicKey = Hash.decodeBytes(sharingPublicKey);
        else
            this.sharingPublicKey = null;
    }

    @JsonIgnore
    public EncryptionKey getSharingPublicEncryptionKey() {
        EncryptionKey key = new EncryptionKey();
        key.publicKey = sharingPublicKey;
        return key;
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
    public String getPasswordKey() {
        if (passwordKey != null)
            return Hash.encodeBytes(passwordKey);
        return null;
    }

    @JsonProperty
    public void setPasswordKey(String passwordKey) {
        if (passwordKey != null)
            this.passwordKey = Hash.decodeBytes(passwordKey);
        else
            this.passwordKey = null;
    }

    public EncryptionKey publicOnly() {
        EncryptionKey ret = publicOnlyHash();
        ret.publicKey = publicKey;
        return ret;
    }

    public EncryptionKey publicOnlyHash() {
        EncryptionKey ret = new EncryptionKey();
        ret.publicKeyHash = getPublicKeyHash();
        ret.salt = salt;
        ret.passwordKey = passwordKey;
        ret.keyData = keyData;
        ret.algorithm = algorithm;
        ret.encryptedAdditionalKeys = encryptedAdditionalKeys;
        ret.sharingPublicKey = sharingPublicKey;
        return ret;
    }

    public EncryptionKey shareableKey() {
        EncryptionKey ret = new EncryptionKey();
        ret.publicKey = publicKey;
        return ret;
    }

    public static class PrivateKey {
        @Getter
        private final String password;
        @Getter
        private final EncryptionKey parent;
        private byte[] privateKey;
        private AdditionalKeyManagerImpl keyManager;


        PrivateKey(String password, byte[] privateKey, EncryptionKey parent) {
            this.password = password;
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

        @JsonIgnore
        public void populateSharingKey(ManifestManager manifestManager) throws IOException {
            if (parent.sharingPublicKey == null) {
                EncryptionKey key = EncryptionKey.generateKeys();
                parent.sharingPublicKey = key.publicKey;
                getAdditionalKeyManager().addNewKey(key, manifestManager);
            }
        }
    }
}
