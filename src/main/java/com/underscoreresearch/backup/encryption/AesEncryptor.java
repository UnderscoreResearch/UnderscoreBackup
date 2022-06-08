package com.underscoreresearch.backup.encryption;

import static com.underscoreresearch.backup.encryption.AesEncryptorFormat.PUBLIC_KEY;

import com.google.inject.Inject;
import com.underscoreresearch.backup.model.BackupBlockStorage;

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
 */
@EncryptorPlugin("AES256")
public class AesEncryptor implements Encryptor {
    private PublicKeyEncrypion key;
    private AesEncryptorFormat defaultFormat;
    private AesEncryptorFormat legacyFormat;

    @Inject
    public AesEncryptor(PublicKeyEncrypion key) {
        this.key = key;
        defaultFormat = new AesEncryptorGcm(key);
        legacyFormat = new AesEncryptorCbc(key);
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
        }
        throw new IllegalArgumentException("Unknown AES encryption padding");
    }

    private int getEncryptionFormatOffset(byte[] data) {
        if (data.length % 4 == 0) {
            return 0;
        }
        return 1;
    }

    private AesEncryptorFormat defaultFormat() {
        return defaultFormat;
    }

    @Override
    public byte[] encryptBlock(BackupBlockStorage storage, byte[] data) {
        return defaultFormat.encryptBlock(storage, data);
    }

    @Override
    public byte[] decodeBlock(BackupBlockStorage storage, byte[] encryptedData) {
        return getEncryptorFormat(encryptedData).decodeBlock(storage, encryptedData,
                getEncryptionFormatOffset(encryptedData));
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
}
