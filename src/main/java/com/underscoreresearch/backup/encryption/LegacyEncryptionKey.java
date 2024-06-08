package com.underscoreresearch.backup.encryption;

import static com.underscoreresearch.backup.encryption.encryptors.BaseAesEncryptor.applyKeyData;
import static com.underscoreresearch.backup.utils.SerializationUtils.MAPPER;

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

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.codec.binary.Base64;

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

    public static LegacyEncryptionKey createWithPublicKey(String publicKey) {
        LegacyEncryptionKey ret = new LegacyEncryptionKey();
        ret.setPublicKey(publicKey);
        return ret;
    }

    public static LegacyEncryptionKey createWithKeyData(String keyData) throws InvalidKeyException, JsonProcessingException {
        if (keyData.startsWith(DISPLAY_PREFIX)) {
            return createWithPrivateKey(keyData);
        }
        return ENCRYPTION_KEY_READER.readValue(keyData);
    }

    public static LegacyEncryptionKey createWithPrivateKey(String privateKey) throws InvalidKeyException {
        if (!privateKey.startsWith(DISPLAY_PREFIX)) {
            throw new IllegalArgumentException("Invalid private key string. Should start with \"p-\"");
        }
        LegacyEncryptionKey ret = new LegacyEncryptionKey();
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

    @JsonProperty("blockHashSalt")
    public String getBlockHashSalt() {
        if (blockHashSalt != null)
            return Hash.encodeBytes64(blockHashSalt);
        return null;
    }

    @JsonProperty("blockHashSalt")
    public void setBlockHashSalt(String salt) {
        if (salt != null)
            blockHashSalt = Hash.decodeBytes64(salt);
        else
            blockHashSalt = null;
    }

    @JsonIgnore
    public void addBlockHashSalt(Hash hash) {
        if (blockHashSalt != null)
            hash.addBytes(blockHashSalt);
    }

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
        this.publicKeyHash = publicKeyHash;
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
    public LegacyEncryptionKey getSharingPublicEncryptionKey() {
        LegacyEncryptionKey key = new LegacyEncryptionKey();
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

    public LegacyEncryptionKey publicOnly() {
        LegacyEncryptionKey ret = publicOnlyHash();
        ret.publicKey = publicKey;
        ret.blockHashSalt = blockHashSalt;
        return ret;
    }

    public LegacyEncryptionKey publicOnlyHash() {
        LegacyEncryptionKey ret = serviceOnlyKey();
        ret.encryptedAdditionalKeys = encryptedAdditionalKeys;
        ret.sharingPublicKey = sharingPublicKey;
        ret.blockHashSaltEncrypted = blockHashSaltEncrypted;
        return ret;
    }

    public LegacyEncryptionKey serviceOnlyKey() {
        LegacyEncryptionKey ret = new LegacyEncryptionKey();
        ret.publicKeyHash = getPublicKeyHash();
        ret.salt = salt;
        ret.passwordKey = passwordKey;
        ret.keyData = keyData;
        ret.algorithm = algorithm;
        return ret;
    }

    @Getter
    public static class PrivateKey {
        private final String password;
        private final LegacyEncryptionKey parent;
        private byte[] privateKey;


        PrivateKey(String password, byte[] privateKey, LegacyEncryptionKey parent) {
            this.password = password;
            this.privateKey = privateKey;
            this.parent = parent;
        }

        public void setPrivateKey(byte[] privateKey) {
            this.privateKey = privateKey;
        }
    }

    public static class AdditionalKeyManager {
        private final static ObjectReader READER = MAPPER.readerFor(new TypeReference<List<String>>() {
        });
        private final List<LegacyEncryptionKey> keys;

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

        public LegacyEncryptionKey findMatchingPrivateKey(LegacyEncryptionKey publicKey) {
            for (LegacyEncryptionKey key : keys) {
                if (key.getPublicKeyHash().equals(publicKey.getPublicKeyHash())) {
                    return key;
                }
            }
            return null;
        }

        public synchronized LegacyEncryptionKey[] getKeys() {
            LegacyEncryptionKey[] ret = new LegacyEncryptionKey[keys.size()];
            return keys.toArray(ret);
        }
    }

}
