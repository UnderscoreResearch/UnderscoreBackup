package com.underscoreresearch.backup.encryption.encryptors;

import static com.underscoreresearch.backup.encryption.EncryptionIdentity.RANDOM;
import static com.underscoreresearch.backup.encryption.IdentityKeys.X25519_KEY;
import static com.underscoreresearch.backup.encryption.encryptors.BaseAesEncryptor.applyKeyData;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import lombok.extern.slf4j.Slf4j;

import com.google.common.collect.Sets;
import com.underscoreresearch.backup.encryption.Hash;
import com.underscoreresearch.backup.encryption.IdentityKeys;
import com.underscoreresearch.backup.encryption.PublicKeyMethod;
import com.underscoreresearch.backup.model.BackupBlockStorage;

@Slf4j
public abstract class AesEncryptorFormat {
    public static final String PUBLIC_KEY = "p";
    public static final String KEY_DATA = "k";
    public static final Set<String> KEY_TYPES_X25519 = Sets.newHashSet(X25519_KEY);
    protected static final int PUBLIC_KEY_SIZE = 32;
    protected static final String KEY_ALGORITHM = "AES";

    protected abstract String getKeyAlgorithm();

    protected abstract int getIvSize();

    protected int adjustEstimatedSize(byte paddingFormat, int estimatedSize) {
        return estimatedSize;
    }

    protected abstract byte paddingFormat(int estimatedSize);

    protected int adjustDecodeLength(byte paddingFormat, int payloadLength) {
        return payloadLength;
    }

    protected abstract AlgorithmParameterSpec createAlgorithmParameterSpec(byte[] iv);

    public byte[] encryptBlock(BackupBlockStorage storage, byte[] data, IdentityKeys key) throws GeneralSecurityException {
        byte[] iv = new byte[getIvSize()];
        RANDOM.nextBytes(iv);
        IdentityKeys.EncryptionParameters parameters = createKeySecret(key);
        byte[] combinedKey = parameters.getSecret();

        byte[] encryptionKey;
        if (storage != null && randomizeKeyData()) {
            encryptionKey = new byte[combinedKey.length];
            RANDOM.nextBytes(encryptionKey);
            byte[] keyData = BaseAesEncryptor.applyKeyData(encryptionKey, combinedKey);
            storage.addProperty(KEY_DATA, Hash.encodeBytes(keyData));
        } else {
            encryptionKey = combinedKey;
        }

        applyAdditionalStorageKeyData(encryptionKey, storage);

        SecretKeySpec secretKeySpec = new SecretKeySpec(encryptionKey, KEY_ALGORITHM);
        try {
            Cipher cipher = Cipher.getInstance(getKeyAlgorithm());

            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, createAlgorithmParameterSpec(iv));

            int estimatedSize = cipher.getOutputSize(data.length);
            byte format = paddingFormat(estimatedSize);

            byte[] publicKey = getKeyEncapsulationData(storage, parameters);

            byte[] ret = new byte[adjustEstimatedSize(format, estimatedSize) + getIvSize() + publicKey.length + 1];

            int length = cipher.doFinal(data, 0, data.length, ret, getIvSize() + publicKey.length + 1);
            if (length != estimatedSize) {
                throw new IllegalBlockSizeException("Got wrong size of block");
            }

            ret[0] = format;
            System.arraycopy(iv, 0, ret, 1, getIvSize());
            System.arraycopy(publicKey, 0, ret, getIvSize() + 1, publicKey.length);

            return ret;
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | IllegalBlockSizeException | BadPaddingException |
                 InvalidKeyException | InvalidAlgorithmParameterException | ShortBufferException e) {
            throw new RuntimeException("Failed to load AES", e);
        }
    }

    protected IdentityKeys.EncryptionParameters createKeySecret(IdentityKeys key) throws GeneralSecurityException {
        return key.getEncryptionParameters(KEY_TYPES_X25519);
    }

    protected byte[] getKeyEncapsulationData(BackupBlockStorage storage, IdentityKeys.EncryptionParameters parameters) {
        byte[] publicKey = parameters.getKeys().get(X25519_KEY).getEncapsulation();
        if (publicKey.length != PUBLIC_KEY_SIZE) {
            throw new IllegalStateException("Wrong publicKey length");
        }
        if (storage != null) {
            storage.addProperty(PUBLIC_KEY, Hash.encodeBytes(publicKey));
        }
        return publicKey;
    }

    protected boolean randomizeKeyData() {
        return true;
    }

    public byte[] decodeBlock(BackupBlockStorage storage, byte[] encryptedData, int offset, IdentityKeys.PrivateKeys key)
            throws GeneralSecurityException {
        byte[] iv = new byte[getIvSize()];

        System.arraycopy(encryptedData, offset, iv, 0, getIvSize());

        AtomicInteger currentOffset = new AtomicInteger(offset + getIvSize());

        Map<String, PublicKeyMethod.EncapsulatedKey> encapsulatedKeys = extractKeyEncapsulation(storage, encryptedData, currentOffset);

        byte[] encryptionKey = key.recreateSecret(encapsulatedKeys);
        encryptionKey = applyKeyData(storage, encryptionKey);

        SecretKeySpec secretKeySpec = new SecretKeySpec(encryptionKey, KEY_ALGORITHM);

        try {
            Cipher cipher = Cipher.getInstance(getKeyAlgorithm());
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, createAlgorithmParameterSpec(iv));
            return cipher.doFinal(encryptedData, currentOffset.get(),
                    adjustDecodeLength(encryptedData[0], encryptedData.length - currentOffset.get()));
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException |
                 InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException e) {
            throw new RuntimeException("Failed to load AES", e);
        }
    }

    protected Map<String, PublicKeyMethod.EncapsulatedKey> extractKeyEncapsulation(BackupBlockStorage storage,
                                                                                   byte[] encryptedData,
                                                                                   AtomicInteger currentOffset)
            throws GeneralSecurityException {
        byte[] publicKey;

        if (storage != null && storage.getProperties() != null && storage.getProperties().containsKey(PUBLIC_KEY)) {
            publicKey = Hash.decodeBytes(storage.getProperties().get(PUBLIC_KEY));
        } else {
            byte[] publicKeyBytes = new byte[PUBLIC_KEY_SIZE];
            System.arraycopy(encryptedData, currentOffset.get(), publicKeyBytes, 0, PUBLIC_KEY_SIZE);
            publicKey = publicKeyBytes;
        }

        currentOffset.addAndGet(PUBLIC_KEY_SIZE);

        return Map.of(X25519_KEY, new PublicKeyMethod.EncapsulatedKey(publicKey));
    }

    public void applyAdditionalStorageKeyData(byte[] encryptionKey,
                                              BackupBlockStorage storage) throws GeneralSecurityException {
        if (storage != null && storage.hasAdditionalStorageProperties()) {
            for (Map.Entry<IdentityKeys, Map<String, String>> entry : storage.getAdditionalStorageProperties().entrySet()) {
                IdentityKeys key = entry.getKey();
                Map<String, String> value = entry.getValue();

                IdentityKeys.EncryptionParameters parameters = createKeySecret(key);

                byte[] keyData = applyKeyData(encryptionKey, parameters.getSecret());
                value.put(KEY_DATA, Hash.encodeBytes(keyData));
                for (Map.Entry<String, PublicKeyMethod.EncapsulatedKey> encapsulatedKey : parameters.getKeys().entrySet()) {
                    value.put(encapsulatedKey.getKey(), Hash.encodeBytes(encapsulatedKey.getValue().getEncapsulation()));
                }
            }
        }
    }
}