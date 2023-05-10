package com.underscoreresearch.backup.encryption;

import static com.underscoreresearch.backup.encryption.AesEncryptor.applyKeyData;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;

import lombok.extern.slf4j.Slf4j;

import com.underscoreresearch.backup.model.BackupBlockStorage;

@Slf4j
public abstract class AesEncryptorFormat {
    public static final String PUBLIC_KEY = "p";
    public static final String KEY_DATA = "k";
    protected static final int PUBLIC_KEY_SIZE = 32;
    protected static final String KEY_ALGORITHM = "AES";
    private static final SecureRandom RANDOM = new SecureRandom();

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

    public byte[] encryptBlock(BackupBlockStorage storage, byte[] data, EncryptionKey key) {
        byte[] iv = new byte[getIvSize()];
        synchronized (RANDOM) {
            RANDOM.nextBytes(iv);
        }
        EncryptionKey privateKey = EncryptionKey.generateKeys();
        byte[] combinedKey = EncryptionKey.combinedSecret(privateKey.getPrivateKey(null), key);
        byte[] encryptionKey;
        if (storage != null && randomizeKeyData()) {
            encryptionKey = new byte[combinedKey.length];
            RANDOM.nextBytes(encryptionKey);
            byte[] keyData = AesEncryptor.applyKeyData(encryptionKey, combinedKey);
            storage.addProperty(KEY_DATA, Hash.encodeBytes(keyData));
        } else {
            encryptionKey = combinedKey;
        }

        applyAdditionalStorageKeyData(encryptionKey, storage, privateKey);

        SecretKeySpec secretKeySpec = new SecretKeySpec(encryptionKey, KEY_ALGORITHM);
        try {
            Cipher cipher = Cipher.getInstance(getKeyAlgorithm());

            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, createAlgorithmParameterSpec(iv));

            byte[] publicKey = Hash.decodeBytes(privateKey.getPublicKey());
            if (publicKey.length != PUBLIC_KEY_SIZE) {
                throw new IllegalStateException("Wrong publicKey length");
            }
            if (storage != null) {
                storage.addProperty(PUBLIC_KEY, privateKey.getPublicKey());
            }
            int estimatedSize = cipher.getOutputSize(data.length);
            byte format = paddingFormat(estimatedSize);
            byte[] ret = new byte[adjustEstimatedSize(format, estimatedSize) + getIvSize() + PUBLIC_KEY_SIZE + 1];

            int length = cipher.doFinal(data, 0, data.length, ret, getIvSize() + PUBLIC_KEY_SIZE + 1);
            if (length != estimatedSize) {
                throw new IllegalBlockSizeException("Got wrong size of block");
            }

            ret[0] = format;
            System.arraycopy(iv, 0, ret, 1, getIvSize());
            System.arraycopy(publicKey, 0, ret, getIvSize() + 1, PUBLIC_KEY_SIZE);

            return ret;
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | IllegalBlockSizeException | BadPaddingException |
                 InvalidKeyException | InvalidAlgorithmParameterException | ShortBufferException e) {
            throw new RuntimeException("Failed to load AES", e);
        }
    }

    protected boolean randomizeKeyData() {
        return true;
    }

    public byte[] decodeBlock(BackupBlockStorage storage, byte[] encryptedData, int offset, EncryptionKey.PrivateKey key) {
        byte[] iv = new byte[getIvSize()];

        System.arraycopy(encryptedData, offset, iv, 0, getIvSize());

        EncryptionKey publicKey = new EncryptionKey();

        if (storage != null && storage.getProperties() != null && storage.getProperties().containsKey(PUBLIC_KEY)) {
            publicKey.setPublicKey(storage.getProperties().get(PUBLIC_KEY));
        } else {
            byte[] publicKeyBytes = new byte[PUBLIC_KEY_SIZE];
            System.arraycopy(encryptedData, getIvSize() + offset, publicKeyBytes, 0, PUBLIC_KEY_SIZE);
            publicKey.setPublicKey(Hash.encodeBytes(publicKeyBytes));
        }

        if (key.getPrivateKey() == null) {
            throw new IllegalStateException("Missing private key for decryption");
        }

        byte[] encryptionKey = EncryptionKey.combinedSecret(key, publicKey);
        encryptionKey = applyKeyData(storage, encryptionKey);

        SecretKeySpec secretKeySpec = new SecretKeySpec(encryptionKey, KEY_ALGORITHM);

        try {
            Cipher cipher = Cipher.getInstance(getKeyAlgorithm());
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, createAlgorithmParameterSpec(iv));
            return cipher.doFinal(encryptedData, getIvSize() + PUBLIC_KEY_SIZE + offset,
                    adjustDecodeLength(encryptedData[0], encryptedData.length - getIvSize() - PUBLIC_KEY_SIZE - offset));
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException |
                 InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException e) {
            throw new RuntimeException("Failed to load AES", e);
        }
    }

    public void backfillEncryption(BackupBlockStorage storage, byte[] encryptedData, int offset) {
        byte[] publicKeyBytes = new byte[PUBLIC_KEY_SIZE];

        System.arraycopy(encryptedData, getIvSize() + offset, publicKeyBytes, 0, PUBLIC_KEY_SIZE);

        storage.addProperty(PUBLIC_KEY, Hash.encodeBytes(publicKeyBytes));
    }

    public void applyAdditionalStorageKeyData(byte[] encryptionKey, BackupBlockStorage storage, EncryptionKey privateKey) {
        if (storage != null && storage.hasAdditionalStorageProperties()) {
            storage.getAdditionalStorageProperties().entrySet().forEach(entry -> {
                byte[] combinedKey = EncryptionKey.combinedSecret(privateKey.getPrivateKey(null), entry.getKey());
                byte[] keyData = AesEncryptor.applyKeyData(encryptionKey, combinedKey);
                entry.getValue().put(KEY_DATA, Hash.encodeBytes(keyData));
                entry.getValue().put(PUBLIC_KEY, privateKey.getPublicKey());
            });
        }
    }
}