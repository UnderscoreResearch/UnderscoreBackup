package com.underscoreresearch.backup.encryption.encryptors;

import static com.underscoreresearch.backup.encryption.IdentityKeys.X25519_KEY;
import static com.underscoreresearch.backup.encryption.encryptors.AesEncryptionFormatTypes.NON_PADDED_GCM;
import static com.underscoreresearch.backup.encryption.encryptors.AesEncryptionFormatTypes.NON_PADDED_GCM_STABLE;
import static com.underscoreresearch.backup.encryption.encryptors.AesEncryptionFormatTypes.PADDED_GCM;
import static com.underscoreresearch.backup.encryption.encryptors.AesEncryptionFormatTypes.PADDED_GCM_STABLE;
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
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import com.underscoreresearch.backup.encryption.Hash;
import com.underscoreresearch.backup.encryption.HashSha3;
import com.underscoreresearch.backup.encryption.IdentityKeys;
import com.underscoreresearch.backup.encryption.PublicKeyMethod;
import com.underscoreresearch.backup.model.BackupBlockStorage;

@Slf4j
public class AesEncryptorGcmStable extends AesEncryptorGcm {

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

    protected byte[] createKeySecret(BackupBlockStorage storage, IdentityKeys key) throws GeneralSecurityException {
        IdentityKeys.EncryptionParameters parameters = key.getEncryptionParameters(KEY_TYPES_X25519);
        byte[] combinedKey = parameters.getSecret();

        storage.addProperty(X25519_KEY, Hash.encodeBytes(parameters.getKeys().get(X25519_KEY).getEncapsulation()));
        return combinedKey;
    }

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

    protected byte[] recreateKeySecret(BackupBlockStorage storage, IdentityKeys.PrivateKeys key) throws GeneralSecurityException {
        return key.recreateSecret(Map.of(X25519_KEY,
                new PublicKeyMethod.EncapsulatedKey(Hash.decodeBytes(storage.getProperties().get(X25519_KEY)))));
    }

    public void backfillEncryption(BackupBlockStorage storage, byte[] encryptedData, int offset) {
    }

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

    protected byte convertFormat(byte format) {
        return switch (format) {
            case PADDED_GCM -> PADDED_GCM_STABLE;
            case NON_PADDED_GCM -> NON_PADDED_GCM_STABLE;
            default -> throw new IllegalArgumentException();
        };
    }
}
