package com.underscoreresearch.backup.encryption;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;

import com.underscoreresearch.backup.model.BackupBlockStorage;

@Slf4j
@EncryptorPlugin("AES256")
public class AesEncryptor implements Encryptor {
    private static final int BLOCK_SIZE = 16;
    private static final int PUBLIC_KEY_SIZE = 32;
    private static final String KEY_ALGORITHM = "AES";
    private static final String ENCRYPTION_ALGORITHM = "AES/CBC/PKCS5Padding";
    public static final String PUBLIC_KEY = "p";

    private final PublicKeyEncrypion key;
    private static SecureRandom random = new SecureRandom();

    @Inject
    public AesEncryptor(PublicKeyEncrypion key) {
        this.key = key;
    }

    @Override
    public byte[] encryptBlock(BackupBlockStorage storage, byte[] data) {
        byte[] iv = new byte[BLOCK_SIZE];
        synchronized (random) {
            random.nextBytes(iv);
        }
        PublicKeyEncrypion privateKey = PublicKeyEncrypion.generateKeys();
        byte[] combinedKey = PublicKeyEncrypion.combinedSecret(privateKey, key);

        SecretKeySpec secretKeySpec = new SecretKeySpec(combinedKey, KEY_ALGORITHM);
        try {
            Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);

            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, new IvParameterSpec(iv));

            byte[] publicKey = Hash.decodeBytes(privateKey.getPublicKey());
            if (publicKey.length != PUBLIC_KEY_SIZE) {
                throw new IllegalStateException("Wrong publicKey length");
            }
            if (storage != null) {
                storage.addProperty(PUBLIC_KEY, privateKey.getPublicKey());
            }
            int estimatedSize = (data.length + BLOCK_SIZE) / BLOCK_SIZE * BLOCK_SIZE + BLOCK_SIZE + PUBLIC_KEY_SIZE;
            byte[] ret = new byte[estimatedSize];

            int length = cipher.doFinal(data, 0, data.length, ret, BLOCK_SIZE + PUBLIC_KEY_SIZE);
            if (length != estimatedSize - iv.length - publicKey.length) {
                log.warn("Guessed wrong size {} of block {}", estimatedSize - BLOCK_SIZE - PUBLIC_KEY_SIZE, length);
                byte[] newRet = new byte[length + BLOCK_SIZE + PUBLIC_KEY_SIZE];
                System.arraycopy(ret, 0, newRet, 0, length);
                ret = newRet;
            }

            System.arraycopy(iv, 0, ret, 0, BLOCK_SIZE);
            System.arraycopy(publicKey, 0, ret, BLOCK_SIZE, PUBLIC_KEY_SIZE);

            return ret;
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | IllegalBlockSizeException | BadPaddingException |
                 InvalidKeyException | InvalidAlgorithmParameterException | ShortBufferException e) {
            throw new RuntimeException("Failed to load AES", e);
        }
    }

    @Override
    public byte[] decodeBlock(BackupBlockStorage storage, byte[] encryptedData) {
        byte[] iv = new byte[BLOCK_SIZE];
        byte[] publicKeyBytes = new byte[PUBLIC_KEY_SIZE];

        System.arraycopy(encryptedData, 0, iv, 0, BLOCK_SIZE);
        System.arraycopy(encryptedData, BLOCK_SIZE, publicKeyBytes, 0, PUBLIC_KEY_SIZE);

        PublicKeyEncrypion publicKey = new PublicKeyEncrypion();
        publicKey.setPublicKey(Hash.encodeBytes(publicKeyBytes));

        if (key.getPrivateKey() == null) {
            throw new IllegalStateException("Missing private key for decryption");
        }

        SecretKeySpec secretKeySpec = new SecretKeySpec(PublicKeyEncrypion.combinedSecret(key, publicKey),
                KEY_ALGORITHM);

        try {
            Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, new IvParameterSpec(iv));
            return cipher.doFinal(encryptedData, BLOCK_SIZE + PUBLIC_KEY_SIZE,
                    encryptedData.length - BLOCK_SIZE - PUBLIC_KEY_SIZE);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException |
                 InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException e) {
            throw new RuntimeException("Failed to load AES", e);
        }
    }

    @Override
    public void backfillEncryption(BackupBlockStorage storage, byte[] encryptedData) {
        byte[] publicKeyBytes = new byte[PUBLIC_KEY_SIZE];

        System.arraycopy(encryptedData, BLOCK_SIZE, publicKeyBytes, 0, PUBLIC_KEY_SIZE);

        storage.addProperty(PUBLIC_KEY, Hash.encodeBytes(publicKeyBytes));
    }

    @Override
    public boolean validStorage(BackupBlockStorage storage) {
        return storage.getProperties() != null && storage.getProperties().containsKey(PUBLIC_KEY);
    }
}
