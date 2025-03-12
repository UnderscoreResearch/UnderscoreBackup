package com.underscoreresearch.backup.encryption.encryptors;

import com.google.common.collect.Maps;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.Encryptor;
import com.underscoreresearch.backup.encryption.Hash;
import com.underscoreresearch.backup.encryption.IdentityKeys;
import com.underscoreresearch.backup.encryption.PublicKeyMethod;
import com.underscoreresearch.backup.model.BackupBlockStorage;
import com.underscoreresearch.backup.model.BackupConfiguration;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.security.GeneralSecurityException;
import java.util.Map;
import java.util.Set;

import static com.underscoreresearch.backup.encryption.IdentityKeys.X25519_KEY;
import static com.underscoreresearch.backup.encryption.encryptors.AesEncryptionFormatTypes.CBC;
import static com.underscoreresearch.backup.encryption.encryptors.AesEncryptionFormatTypes.NON_PADDED_GCM;
import static com.underscoreresearch.backup.encryption.encryptors.AesEncryptionFormatTypes.NON_PADDED_GCM_STABLE;
import static com.underscoreresearch.backup.encryption.encryptors.AesEncryptionFormatTypes.PADDED_GCM;
import static com.underscoreresearch.backup.encryption.encryptors.AesEncryptionFormatTypes.PADDED_GCM_STABLE;
import static com.underscoreresearch.backup.encryption.encryptors.AesEncryptorFormat.KEY_DATA;
import static com.underscoreresearch.backup.encryption.encryptors.AesEncryptorFormat.KEY_TYPES_X25519;
import static com.underscoreresearch.backup.encryption.encryptors.AesEncryptorFormat.PUBLIC_KEY;

/**
 * X25519 encryptor. Called AES for historical reasons (AES is used for the symmetrical cypher)
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
@Slf4j
public class BaseAesEncryptor implements Encryptor {
    private static final AesEncryptorFormat defaultFormat = new AesEncryptorGcm();
    private static final AesEncryptorFormat legacyFormat = new AesEncryptorCbc();
    private static final AesEncryptorFormat stableFormat = new AesEncryptorGcmStable();

    @Getter(AccessLevel.PROTECTED)
    private boolean stableDedupe;

    public BaseAesEncryptor() {

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

    protected AesEncryptorFormat getEncryptorFormat(byte[] data) {
        if (data.length % 4 == 0) {
            return legacyFormat;
        }
        switch (data[0]) {
            case CBC -> {
                return legacyFormat;
            }
            case NON_PADDED_GCM, PADDED_GCM -> {
                return defaultFormat;
            }
            case NON_PADDED_GCM_STABLE, PADDED_GCM_STABLE -> {
                return stableFormat;
            }
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
    public byte[] encryptBlock(BackupBlockStorage storage, byte[] data, IdentityKeys key) throws GeneralSecurityException {
        if (storage != null && stableDedupe) {
            return stableFormat.encryptBlock(storage, data, key);
        }
        return defaultFormat.encryptBlock(storage, data, key);
    }

    @Override
    public byte[] decodeBlock(BackupBlockStorage storage, byte[] encryptedData,
                              IdentityKeys.PrivateKeys key) throws GeneralSecurityException {
        return getEncryptorFormat(encryptedData).decodeBlock(storage, encryptedData,
                getEncryptionFormatOffset(encryptedData), key);
    }

    @Override
    public boolean validStorage(BackupBlockStorage storage) {
        return storage.getProperties() != null && storage.getProperties().containsKey(PUBLIC_KEY);
    }

    @Override
    public BackupBlockStorage reKeyStorage(BackupBlockStorage storage, IdentityKeys.PrivateKeys oldPrivateKey, IdentityKeys newPublicKey) throws GeneralSecurityException {
        if (storage.getProperties() == null || !storage.getProperties().containsKey(PUBLIC_KEY)) {
            return null;
        }

        byte[] encryptionKey = oldPrivateKey.recreateSecret(createEncapsulatedKeys(storage));

        encryptionKey = applyKeyData(storage, encryptionKey);

        IdentityKeys.EncryptionParameters encryptionParameters = newPublicKey.getEncryptionParameters(getEncryptionKeys());
        byte[] newKeyData = applyKeyData(encryptionKey, encryptionParameters.getSecret());

        BackupBlockStorage newStorage = storage.toBuilder()
                .properties(Maps.newHashMap(storage.getProperties()))
                .build();
        newStorage.addProperty(KEY_DATA, Hash.encodeBytes(newKeyData));

        storeEncryptionParameters(newStorage, encryptionParameters);

        return newStorage;
    }

    protected void storeEncryptionParameters(BackupBlockStorage storage, IdentityKeys.EncryptionParameters ret) {
        storage.getProperties().put(X25519_KEY, Hash.encodeBytes(ret.getKeys().get(X25519_KEY).getEncapsulation()));
    }

    protected Set<String> getEncryptionKeys() {
        return KEY_TYPES_X25519;
    }

    protected Map<String, PublicKeyMethod.EncapsulatedKey> createEncapsulatedKeys(BackupBlockStorage storage) {
        byte[] blockPublicKey = Hash.decodeBytes(storage.getProperties().get(X25519_KEY));
        return Map.of(X25519_KEY, new PublicKeyMethod.EncapsulatedKey(blockPublicKey));
    }
}
