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

    public EncryptionIdentity(String password, LegacyEncryptionKey.PrivateKey privateKey) throws GeneralSecurityException {
        populateFromLegacyPrivateKey(password, privateKey);
    }

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

    protected static byte[] getPasswordDerivative(String algorithm, String password, byte[] saltData) throws InvalidKeySpecException {
        if (CURRENT_ALGORITHM.equals(algorithm)) {
            Argon2Advanced argon2 = Argon2Factory.createAdvanced();
            String hash = argon2.hash(64, 8192, 2, password.toCharArray(), StandardCharsets.UTF_8, saltData);
            int lastPart = hash.lastIndexOf('$');
            return Base64.decodeBase64(hash.substring(lastPart + 1));
        }

        throw new InvalidKeySpecException();
    }

    public static EncryptionIdentity withIdentityKeys(IdentityKeys keys) {
        EncryptionIdentity ret = new EncryptionIdentity();
        ret.primaryKeys = keys;
        return ret;
    }

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

    private void updateBlockHashSaltEncryption(PrivateIdentity privateIdentity) throws GeneralSecurityException {
        if (blockHashSalt != null) {
            blockHashSaltEncrypted = Hash.encodeBytes64(privateIdentity.encryptKeyData(blockHashSalt));
        } else {
            blockHashSaltEncrypted = null;
        }
    }

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

    @JsonProperty("salt")
    public String getSalt() {
        if (salt != null)
            return Hash.encodeBytes(salt);
        return null;
    }

    @JsonProperty("keyData")
    public String getKeyData() {
        if (keyData != null)
            return Hash.encodeBytes(keyData);
        return null;
    }

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

    private EncryptionIdentity onlyPassword() {
        EncryptionIdentity ret = new EncryptionIdentity();
        ret.algorithm = algorithm;
        ret.salt = salt;
        ret.keyData = keyData;
        ret.privateHash = privateHash;
        return ret;
    }

    private EncryptionIdentity serviceKey() {
        EncryptionIdentity ret = onlyPassword();
        ret.primaryKeys = primaryKeys.withoutPublicKeys();
        return ret;
    }

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

    @JsonProperty("privateHash")
    public String getPrivateHash() {
        if (privateHash != null)
            return Hash.encodeBytes64(privateHash);
        return null;
    }

    @JsonProperty("blockHashSalt")
    public String getBlockHashSalt() {
        if (blockHashSalt != null)
            return Hash.encodeBytes64(blockHashSalt);
        return null;
    }

    @JsonIgnore
    public byte[] getBlockHashSaltBytes() {
        return blockHashSalt;
    }

    @JsonProperty("blockHashSalt")
    public void updateBlockHashSalt(String salt, PrivateIdentity privateIdentity) throws GeneralSecurityException {
        if (salt != null)
            blockHashSalt = Hash.decodeBytes64(salt);
        else
            blockHashSalt = null;
        updateBlockHashSaltEncryption(privateIdentity);
    }

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

    public IdentityKeys.PrivateKeys getPrivateKeys(String password) throws GeneralSecurityException {
        PrivateIdentity pi = getPrivateIdentity(password);
        return getPrimaryKeys().getPrivateKeys(pi);
    }

    public void addBlockHashSalt(Hash hashCalc) {
        if (blockHashSalt != null) {
            hashCalc.addBytes(blockHashSalt);
        }
    }

    public void copyAdditionalData(EncryptionIdentity sourceKey) throws GeneralSecurityException {
        if (!Arrays.equals(privateHash, sourceKey.privateHash))
            throw new GeneralSecurityException("Can not copy properties without already having matching private keys");

        primaryKeys = sourceKey.primaryKeys;
        additionalKeys = sourceKey.additionalKeys;
        sharingKeys = sourceKey.sharingKeys;
        blockHashSalt = sourceKey.blockHashSalt;
        blockHashSaltEncrypted = sourceKey.blockHashSaltEncrypted;
    }

    public EncryptionIdentity copyWithPublicPrimaryKey(IdentityKeys publicPrimaryKeys) {
        EncryptionIdentity ret = onlyPassword();
        ret.primaryKeys = getIdentityKeysForPublicIdentity(publicPrimaryKeys);
        return ret;
    }

    public enum KeyFormat {
        PUBLIC,
        UPLOAD,
        SERVICE
    }

    public class PrivateIdentity {
        private final byte[] privateKey;
        @Getter(AccessLevel.PROTECTED)
        private final String password;

        public PrivateIdentity(String password, byte[] privateKey) {
            this.privateKey = privateKey;
            this.password = password;
        }

        public EncryptionIdentity getEncryptionIdentity() {
            return EncryptionIdentity.this;
        }

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
