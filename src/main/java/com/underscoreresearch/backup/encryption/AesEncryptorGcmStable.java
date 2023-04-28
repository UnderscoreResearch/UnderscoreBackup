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

import lombok.extern.slf4j.Slf4j;

import com.underscoreresearch.backup.model.BackupBlockStorage;

@Slf4j
public class AesEncryptorGcmStable extends AesEncryptorGcm {
    public static final byte PADDED_GCM_STABLE = 4;
    public static final byte NON_PADDED_GCM_STABLE = 3;

    @Override
    public byte[] encryptBlock(BackupBlockStorage storage, byte[] data, EncryptionKey key) {
        if (storage == null) {
            throw new IllegalArgumentException();
        }

        // So we are using a constant IV here which is usually catastrophically bad when using GCM.
        // However, we never ever use the same key to encrypt more than one message so it should be sage.
        byte[] iv = new byte[getIvSize()];

        EncryptionKey privateKey = EncryptionKey.generateKeys();
        storage.addProperty(PUBLIC_KEY, privateKey.getPublicKey());
        byte[] combinedKey = EncryptionKey.combinedSecret(privateKey.getPrivateKey(null), key);

        HashSha3 hashSha3 = new HashSha3();
        hashSha3.addBytes(data);
        byte[] encryptionKey = hashSha3.getHashBytes();

        byte[] keyData = AesEncryptor.applyKeyData(encryptionKey, combinedKey);
        storage.addProperty(KEY_DATA, Hash.encodeBytes(keyData));

        applyAdditionalStorageKeyData(encryptionKey, storage, privateKey);

        SecretKeySpec secretKeySpec = new SecretKeySpec(encryptionKey, KEY_ALGORITHM);
        try {
            Cipher cipher = Cipher.getInstance(getKeyAlgorithm());

            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, createAlgorithmParameterSpec(iv));

            int estimatedSize = cipher.getOutputSize(data.length);
            byte format = paddingFormat(estimatedSize);
            byte[] ret = new byte[adjustEstimatedSize(format, estimatedSize) + 1];

            int length = cipher.doFinal(data, 0, data.length, ret, 1);
            if (length != estimatedSize) {
                throw new IllegalBlockSizeException("Got wrong size of block");
            }

            ret[0] = convertFormat(format);

            return ret;
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | IllegalBlockSizeException | BadPaddingException |
                 InvalidKeyException | InvalidAlgorithmParameterException | ShortBufferException e) {
            throw new RuntimeException("Failed to load AES", e);
        }
    }

    public byte[] decodeBlock(BackupBlockStorage storage, byte[] encryptedData, int offset, EncryptionKey.PrivateKey key) {
        if (storage == null) {
            throw new IllegalArgumentException();
        }
        byte[] iv = new byte[getIvSize()];

        EncryptionKey publicKey = new EncryptionKey();

        publicKey.setPublicKey(storage.getProperties().get(PUBLIC_KEY));

        if (key.getPrivateKey() == null) {
            throw new IllegalStateException("Missing private key for decryption");
        }

        byte[] encryptionKey = EncryptionKey.combinedSecret(key, publicKey);
        encryptionKey = applyKeyData(storage, encryptionKey);

        SecretKeySpec secretKeySpec = new SecretKeySpec(encryptionKey, KEY_ALGORITHM);

        try {
            Cipher cipher = Cipher.getInstance(getKeyAlgorithm());
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, createAlgorithmParameterSpec(iv));
            return cipher.doFinal(encryptedData, offset,
                    adjustDecodeLength(encryptedData[0], encryptedData.length - offset));
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException |
                 InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException e) {
            throw new RuntimeException("Failed to load AES", e);
        }
    }

    public void backfillEncryption(BackupBlockStorage storage, byte[] encryptedData, int offset) {
    }

    @Override
    protected int adjustDecodeLength(byte paddingFormat, int payloadLength) {
        switch (paddingFormat) {
            case NON_PADDED_GCM_STABLE:
                return payloadLength;
            case PADDED_GCM_STABLE:
                return payloadLength - 1;
        }
        throw new IllegalArgumentException("Unknown AES padding format");
    }

    private byte convertFormat(byte format) {
        switch (format) {
            case PADDED_GCM:
                return PADDED_GCM_STABLE;
            case NON_PADDED_GCM:
                return NON_PADDED_GCM_STABLE;
            default:
                throw new IllegalArgumentException();
        }
    }
}
