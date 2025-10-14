package com.underscoreresearch.backup.encryption.encryptors;

import com.underscoreresearch.backup.encryption.Hash;
import com.underscoreresearch.backup.encryption.HashSha3;
import com.underscoreresearch.backup.encryption.IdentityKeys;
import com.underscoreresearch.backup.encryption.PublicKeyMethod;
import com.underscoreresearch.backup.model.BackupBlockStorage;
import lombok.extern.slf4j.Slf4j;

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
import java.util.Map;

import static com.underscoreresearch.backup.encryption.IdentityKeys.X25519_KEY;
import static com.underscoreresearch.backup.encryption.encryptors.AesEncryptionFormatTypes.NON_PADDED_GCM;
import static com.underscoreresearch.backup.encryption.encryptors.AesEncryptionFormatTypes.NON_PADDED_GCM_STABLE;
import static com.underscoreresearch.backup.encryption.encryptors.AesEncryptionFormatTypes.PADDED_GCM;
import static com.underscoreresearch.backup.encryption.encryptors.AesEncryptionFormatTypes.PADDED_GCM_STABLE;
import static com.underscoreresearch.backup.encryption.encryptors.BaseAesEncryptor.applyKeyData;

/**
 * AES encryptor implementation using GCM mode with stable output.
 * This class provides encryption and decryption using the AES/GCM/NoPadding algorithm
 * with a stable output format that allows for deduplication.
 */
@Slf4j
public class AesEncryptorGcmStable extends AesEncryptorGcm {

    /**
     * Encrypts a data block using the specified identity keys.
     * Uses a constant IV derived from the data content to ensure stable output.
     *
     * @param storage The storage metadata (required)
     * @param data The data to encrypt
     * @param key The identity keys to use for encryption
     * @return The encrypted data
     * @throws GeneralSecurityException If encryption fails
     * @throws IllegalArgumentException If storage is null
     */
    @Override
    public byte[] encryptBlock(BackupBlockStorage storage, byte[] data, IdentityKeys key) throws GeneralSecurityException {
        if (storage == null) {
            throw new IllegalArgumentException();
        }

        // So we are using a constant IV here which is usually catastrophically bad when using GCM.
        // However, we never ever use the same key to encrypt more than one message so it should be sage.
        byte[] iv = new byte[getIvSize()];

        byte[] combinedKey = createKeySecret(storage, key);

        HashSha3 hashSha3 = new HashSha3();
        hashSha3.addBytes(data);
        byte[] encryptionKey = hashSha3.getHashBytes();

        byte[] keyData = BaseAesEncryptor.applyKeyData(encryptionKey, combinedKey);
        storage.addProperty(KEY_DATA, Hash.encodeBytes(keyData));

        applyAdditionalStorageKeyData(encryptionKey, storage);

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

    /**
     * Creates the encryption key secret from identity keys and stores it in the storage metadata.
     *
     * @param storage The storage metadata
     * @param key The identity keys
     * @return The combined key secret
     * @throws GeneralSecurityException If key creation fails
     */
    protected byte[] createKeySecret(BackupBlockStorage storage, IdentityKeys key) throws GeneralSecurityException {
        IdentityKeys.EncryptionParameters parameters = key.getEncryptionParameters(KEY_TYPES_X25519);
        byte[] combinedKey = parameters.getSecret();

        storage.addProperty(X25519_KEY, Hash.encodeBytes(parameters.getKeys().get(X25519_KEY).getEncapsulation()));
        return combinedKey;
    }

    /**
     * Decodes (decrypts) a data block.
     *
     * @param storage The storage metadata (required)
     * @param encryptedData The encrypted data
     * @param offset The offset in the encrypted data
     * @param key The private keys to use for decryption
     * @return The decrypted data
     * @throws GeneralSecurityException If decryption fails
     * @throws IllegalArgumentException If storage is null
     */
    @Override
    public byte[] decodeBlock(BackupBlockStorage storage, byte[] encryptedData, int offset, IdentityKeys.PrivateKeys key) throws GeneralSecurityException {
        if (storage == null) {
            throw new IllegalArgumentException();
        }
        byte[] iv = new byte[getIvSize()];

        byte[] encryptionKey = recreateKeySecret(storage, key);

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

    /**
     * Recreates the key secret from the storage metadata and private keys.
     *
     * @param storage The storage metadata
     * @param key The private keys
     * @return The recreated key secret
     * @throws GeneralSecurityException If key recreation fails
     */
    protected byte[] recreateKeySecret(BackupBlockStorage storage, IdentityKeys.PrivateKeys key) throws GeneralSecurityException {
        return key.recreateSecret(Map.of(X25519_KEY,
                new PublicKeyMethod.EncapsulatedKey(Hash.decodeBytes(storage.getProperties().get(X25519_KEY)))));
    }

    /**
     * Adjusts the decode length based on the padding format.
     *
     * @param paddingFormat The padding format
     * @param payloadLength The payload length
     * @return The adjusted length
     * @throws IllegalArgumentException If the padding format is unknown
     */
    @Override
    protected int adjustDecodeLength(byte paddingFormat, int payloadLength) {
        switch (paddingFormat) {
            case NON_PADDED_GCM_STABLE -> {
                return payloadLength;
            }
            case PADDED_GCM_STABLE -> {
                return payloadLength - 1;
            }
        }
        throw new IllegalArgumentException("Unknown AES padding format");
    }

    /**
     * Converts the standard GCM format to the stable GCM format.
     *
     * @param format The standard format
     * @return The stable format
     * @throws IllegalArgumentException If the format is unknown
     */
    protected byte convertFormat(byte format) {
        return switch (format) {
            case PADDED_GCM -> PADDED_GCM_STABLE;
            case NON_PADDED_GCM -> NON_PADDED_GCM_STABLE;
            default -> throw new IllegalArgumentException();
        };
    }
}
