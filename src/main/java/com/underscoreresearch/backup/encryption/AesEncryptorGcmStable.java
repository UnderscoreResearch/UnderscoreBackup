package com.underscoreresearch.backup.encryption;

import com.underscoreresearch.backup.model.BackupBlockStorage;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.inject.Inject;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.AlgorithmParameterSpec;

import static com.underscoreresearch.backup.encryption.AesEncryptor.applyKeyData;

@Slf4j
public class AesEncryptorGcmStable extends AesEncryptorGcm {
    public static final byte PADDED_GCM_STABLE = 4;
    public static final byte NON_PADDED_GCM_STABLE = 3;

    @Inject
    public AesEncryptorGcmStable(PublicKeyEncrypion key) {
        super(key);
    }

    public byte[] encryptBlock(BackupBlockStorage storage, byte[] data) {
        if (storage == null) {
            throw new IllegalArgumentException();
        }

        // So we are using a constant IV here which is usually catastrophically bad when using GCM.
        // However, we never ever use the same key to encrypt more than one message so it should be sage.
        byte[] iv = new byte[getIvSize()];

        PublicKeyEncrypion privateKey = PublicKeyEncrypion.generateKeys();
        storage.addProperty(PUBLIC_KEY, privateKey.getPublicKey());
        byte[] combinedKey = PublicKeyEncrypion.combinedSecret(privateKey, getKey());

        HashSha3 hashSha3 = new HashSha3();
        hashSha3.addBytes(data);
        byte[] encryptionKey = hashSha3.getHashBytes();

        byte[] keyData = AesEncryptor.applyKeyData(encryptionKey, combinedKey);
        storage.addProperty(KEY_DATA, Hash.encodeBytes(keyData));

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

    public byte[] decodeBlock(BackupBlockStorage storage, byte[] encryptedData, int offset) {
        if (storage == null) {
            throw new IllegalArgumentException();
        }
        byte[] iv = new byte[getIvSize()];

        PublicKeyEncrypion publicKey = new PublicKeyEncrypion();

        publicKey.setPublicKey(storage.getProperties().get(PUBLIC_KEY));

        if (getKey().getPrivateKey() == null) {
            throw new IllegalStateException("Missing private key for decryption");
        }

        byte[] encryptionKey = PublicKeyEncrypion.combinedSecret(getKey(), publicKey);
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
        switch(format) {
            case PADDED_GCM:
                return PADDED_GCM_STABLE;
            case NON_PADDED_GCM:
                return NON_PADDED_GCM_STABLE;
            default:
                throw new IllegalArgumentException();
        }
    }
}
