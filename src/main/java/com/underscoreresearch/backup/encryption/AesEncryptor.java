package com.underscoreresearch.backup.encryption;

import static com.underscoreresearch.backup.encryption.AesEncryptor.AES_ENCRYPTION;
import static com.underscoreresearch.backup.encryption.AesEncryptorFormat.KEY_DATA;
import static com.underscoreresearch.backup.encryption.AesEncryptorFormat.PUBLIC_KEY;

import lombok.extern.slf4j.Slf4j;

import com.google.common.collect.Maps;
import com.google.inject.Inject;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.model.BackupBlockStorage;
import com.underscoreresearch.backup.model.BackupConfiguration;

/**
 * AES 256 encryptor.
 * <p>
 * So this format is a bit of a mess in that I started using CBC encoding and padding and then realized that I really
 * should be using GCM encoding. Unfortunately I left no field for future expansion in the original format but I have
 * figured out a way to be backwards compatible and add future extensibility in case I want to change this again
 * in the future. So here is how the payload works.
 * <p>
 * First byte is a padding version indicator which can currently be 0 for CBC, 1 for GCM and 2 for GCM with a single
 * additional byte for padding its payload to an even length. This byte is missing for all legacy data created before
 * the introduction of the GCM encoding. However, any encrypted block with an even number of bytes in length will
 * assumed to be of CBC encoding. This is also why the GCM encoding needs format bytes since it can be of uneven size.
 * <p>
 * The next 12 bytes for GCM and 16 bytes for CBC contain the IV vector for the crypto.
 * <p>
 * The next 32 bytes contain the public key used to combine with the private key to create the key used for the AES256
 * algorithm.
 * <p>
 * The entire rest of the data is the encryption payload.
 * <p>
 * There is also another format used when storage is specified by default. In this format only the first byte is used
 * to specify the format and the entire rest of the payload is the encryption. The IV in this case is a 0 array, the
 * encryption key is the SHA3-256 of the payload (Which is different from the SHA-256 used to create the block ID. In
 * this format a block with the same contents will always be encrypted to exactly the same encryption payload allowing
 * for good deduplication of the data without jeopardizing the contents.
 */
@EncryptorPlugin(AES_ENCRYPTION)
@Slf4j
public class AesEncryptor implements Encryptor {
    public static final String AES_ENCRYPTION = "AES256";
    private final AesEncryptorFormat defaultFormat;
    private final AesEncryptorFormat legacyFormat;
    private final AesEncryptorFormat stableFormat;
    private boolean stableDedupe;

    @Inject
    public AesEncryptor() {
        defaultFormat = new AesEncryptorGcm();
        stableFormat = new AesEncryptorGcmStable();
        legacyFormat = new AesEncryptorCbc();

        stableDedupe = true;
        try {
            BackupConfiguration config = InstanceFactory.getInstance(BackupConfiguration.class);
            stableDedupe = !("false".equals(config.getProperty("crossSourceDedupe", "true")));
        } catch (Exception exc) {
            log.warn("Failed to read config for encryption setup");
            stableDedupe = true;
        }
    }

    public static byte[] applyKeyData(BackupBlockStorage storage, byte[] encryptionKey) {
        if (storage != null && storage.getProperties() != null && storage.getProperties().containsKey(KEY_DATA)) {
            byte[] keyData = Hash.decodeBytes(storage.getProperties().get(KEY_DATA));

            return applyKeyData(keyData, encryptionKey);
        }
        return encryptionKey;
    }

    public static byte[] applyKeyData(byte[] keyData, byte[] encryptionKey) {
        byte[] ret = new byte[encryptionKey.length];
        for (int i = 0; i < keyData.length; i++) {
            ret[i] = (byte) (encryptionKey[i] ^ keyData[i]);
        }
        return ret;
    }

    private AesEncryptorFormat getEncryptorFormat(byte[] data) {
        if (data.length % 4 == 0) {
            return legacyFormat;
        }
        switch (data[0]) {
            case AesEncryptorCbc.CBC:
                return legacyFormat;
            case AesEncryptorGcm.NON_PADDED_GCM:
            case AesEncryptorGcm.PADDED_GCM:
                return defaultFormat;
            case AesEncryptorGcmStable.NON_PADDED_GCM_STABLE:
            case AesEncryptorGcmStable.PADDED_GCM_STABLE:
                return stableFormat;
        }
        throw new IllegalArgumentException("Unknown AES encryption padding");
    }

    private int getEncryptionFormatOffset(byte[] data) {
        if (data.length % 4 == 0) {
            return 0;
        }
        return 1;
    }

    @Override
    public byte[] encryptBlock(BackupBlockStorage storage, byte[] data, EncryptionKey key) {
        if (storage != null && stableDedupe) {
            return stableFormat.encryptBlock(storage, data, key);
        }
        return defaultFormat.encryptBlock(storage, data, key);
    }

    @Override
    public byte[] decodeBlock(BackupBlockStorage storage, byte[] encryptedData, EncryptionKey.PrivateKey key) {
        return getEncryptorFormat(encryptedData).decodeBlock(storage, encryptedData,
                getEncryptionFormatOffset(encryptedData), key);
    }

    @Override
    public void backfillEncryption(BackupBlockStorage storage, byte[] encryptedBlob) {
        getEncryptorFormat(encryptedBlob).backfillEncryption(storage, encryptedBlob,
                getEncryptionFormatOffset(encryptedBlob));
    }

    @Override
    public boolean validStorage(BackupBlockStorage storage) {
        return storage.getProperties() != null && storage.getProperties().containsKey(PUBLIC_KEY);
    }

    @Override
    public BackupBlockStorage reKeyStorage(BackupBlockStorage storage, EncryptionKey.PrivateKey oldPrivateKey,
                                           EncryptionKey newPublicKey) {
        if (storage.getProperties() == null || !storage.getProperties().containsKey(PUBLIC_KEY)) {
            return null;
        }
        EncryptionKey blockPublicKey = new EncryptionKey();
        blockPublicKey.setPublicKey(storage.getProperties().get(PUBLIC_KEY));
        byte[] privateKey = EncryptionKey.combinedSecret(oldPrivateKey, blockPublicKey);
        privateKey = applyKeyData(storage, privateKey);

        EncryptionKey newBlockKey = EncryptionKey.generateKeys();
        byte[] newPrivateKey = EncryptionKey.combinedSecret(newBlockKey.getPrivateKey(null), newPublicKey);
        byte[] keyData = applyKeyData(newPrivateKey, privateKey);

        BackupBlockStorage newStorage = storage.toBuilder()
                .properties(Maps.newHashMap(storage.getProperties()))
                .build();
        newStorage.addProperty(PUBLIC_KEY, newBlockKey.getPublicKey());
        newStorage.addProperty(KEY_DATA, Hash.encodeBytes(keyData));

        return newStorage;
    }
}
