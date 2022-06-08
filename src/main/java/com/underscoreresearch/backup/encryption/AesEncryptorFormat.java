package com.underscoreresearch.backup.encryption;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.SecretKeySpec;

import lombok.extern.slf4j.Slf4j;

import com.underscoreresearch.backup.model.BackupBlockStorage;

@Slf4j
public abstract class AesEncryptorFormat {
    private static final int PUBLIC_KEY_SIZE = 32;
    private static final String KEY_ALGORITHM = "AES";
    public static final String PUBLIC_KEY = "p";

    private final PublicKeyEncrypion key;
    private static SecureRandom random = new SecureRandom();

    public AesEncryptorFormat(PublicKeyEncrypion key) {
        this.key = key;
    }

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

    public byte[] encryptBlock(BackupBlockStorage storage, byte[] data) {
        byte[] iv = new byte[getIvSize()];
        synchronized (random) {
            random.nextBytes(iv);
        }
        PublicKeyEncrypion privateKey = PublicKeyEncrypion.generateKeys();
        byte[] combinedKey = PublicKeyEncrypion.combinedSecret(privateKey, key);

        SecretKeySpec secretKeySpec = new SecretKeySpec(combinedKey, KEY_ALGORITHM);
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

    public byte[] decodeBlock(BackupBlockStorage storage, byte[] encryptedData, int offset) {
        byte[] iv = new byte[getIvSize()];
        byte[] publicKeyBytes = new byte[PUBLIC_KEY_SIZE];

        System.arraycopy(encryptedData, offset, iv, 0, getIvSize());
        System.arraycopy(encryptedData, getIvSize() + offset, publicKeyBytes, 0, PUBLIC_KEY_SIZE);

        PublicKeyEncrypion publicKey = new PublicKeyEncrypion();
        publicKey.setPublicKey(Hash.encodeBytes(publicKeyBytes));

        if (key.getPrivateKey() == null) {
            throw new IllegalStateException("Missing private key for decryption");
        }

        SecretKeySpec secretKeySpec = new SecretKeySpec(PublicKeyEncrypion.combinedSecret(key, publicKey),
                KEY_ALGORITHM);

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
}
