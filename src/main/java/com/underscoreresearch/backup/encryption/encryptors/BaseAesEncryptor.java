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
 * Base class for AES encryption and decryption.
 * This class provides methods for encrypting and decrypting data blocks
 * using different AES encryption formats.
 *
 * <p>
 * So this format is a bit of a mess in that I started using CBC encoding and padding and then realized that I really
 * should be using GCM encoding. Unfortunately I left no field for future expansion in the original format but I have
 * figured out a way to be backwards compatible and add future extensibility in case I want to change this again
 * in the future. So here is how the payload works.
 * <p>
 * First byte is a padding version indicator which can currently be 0 for CBC, 1 for GCM and 2 for GCM with a single
 * additional byte for padding its payload to an even length. This byte is missing for all legacy data created before
 * the introduction of the GCM encoding. However, any encrypted block with an even number of bytes in length will
 * be assumed to be of CBC encoding. This is also why the GCM encoding needs format bytes since it can be of uneven size.
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

    /**
     * Constructor for BaseAesEncryptor.
     * Initializes the encryptor and determines whether to use stable deduplication
     * based on configuration settings.
     */
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

    /**
     * Applies key data from storage to an encryption key.
     * This method retrieves key data from storage properties and applies it to the encryption key.
     *
     * @param storage The storage metadata containing key data
     * @param encryptionKey The encryption key to modify
     * @return The modified encryption key, or the original if no key data is available
     */
    public static byte[] applyKeyData(BackupBlockStorage storage, byte[] encryptionKey) {
        if (storage != null && storage.getProperties() != null && storage.getProperties().containsKey(KEY_DATA)) {
            byte[] keyData = Hash.decodeBytes(storage.getProperties().get(KEY_DATA));

            return applyKeyData(keyData, encryptionKey);
        }
        return encryptionKey;
    }

    /**
     * Applies key data to an encryption key using XOR operation.
     * This method combines two byte arrays using XOR to create a derived key.
     *
     * @param keyData The key data to apply
     * @param encryptionKey The encryption key to modify
     * @return The modified encryption key
     */
    public static byte[] applyKeyData(byte[] keyData, byte[] encryptionKey) {
        byte[] ret = new byte[encryptionKey.length];
        for (int i = 0; i < keyData.length; i++) {
            ret[i] = (byte) (encryptionKey[i] ^ keyData[i]);
        }
        return ret;
    }

    /**
     * Determines the appropriate encryptor format based on the encrypted data.
     * This method analyzes the data format to select the correct decryption algorithm.
     *
     * @param data The encrypted data
     * @return The appropriate AesEncryptorFormat implementation
     * @throws IllegalArgumentException If the encryption format is unknown
     */
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

    /**
     * Determines the offset in the encrypted data where the actual payload begins.
     * For legacy formats (even length), the offset is 0. For newer formats, it's 1.
     *
     * @param data The encrypted data
     * @return The offset where the payload begins
     */
    private int getEncryptionFormatOffset(byte[] data) {
        if (data.length % 4 == 0) {
            return 0;
        }
        return 1;
    }

    /**
     * Encrypts a data block using the appropriate encryption format.
     * Uses stable format for deduplication if storage is provided and stableDedupe is enabled.
     *
     * @param storage The storage metadata
     * @param data The data to encrypt
     * @param key The identity keys to use for encryption
     * @return The encrypted data
     * @throws GeneralSecurityException If encryption fails
     */
    @Override
    public byte[] encryptBlock(BackupBlockStorage storage, byte[] data, IdentityKeys key) throws GeneralSecurityException {
        if (storage != null && stableDedupe) {
            return stableFormat.encryptBlock(storage, data, key);
        }
        return defaultFormat.encryptBlock(storage, data, key);
    }

    /**
     * Decodes (decrypts) a data block using the appropriate encryption format.
     * Automatically detects the format from the encrypted data.
     *
     * @param storage The storage metadata
     * @param encryptedData The encrypted data
     * @param key The private keys to use for decryption
     * @return The decrypted data
     * @throws GeneralSecurityException If decryption fails
     */
    @Override
    public byte[] decodeBlock(BackupBlockStorage storage, byte[] encryptedData,
                              IdentityKeys.PrivateKeys key) throws GeneralSecurityException {
        return getEncryptorFormat(encryptedData).decodeBlock(storage, encryptedData,
                getEncryptionFormatOffset(encryptedData), key);
    }

    /**
     * Checks if the storage configuration is valid for this encryptor.
     * Storage is valid if it contains properties with a public key.
     *
     * @param storage The storage metadata to validate
     * @return True if the storage is valid for this encryptor
     */
    @Override
    public boolean validStorage(BackupBlockStorage storage) {
        return storage.getProperties() != null && storage.getProperties().containsKey(PUBLIC_KEY);
    }

    /**
     * Re-encrypts storage metadata with new keys.
     * This method decrypts the storage with the old private key and re-encrypts it with the new public key.
     *
     * @param storage The storage metadata to re-key
     * @param oldPrivateKey The old private keys
     * @param newPublicKey The new public keys
     * @return The updated storage metadata, or null if the storage doesn't contain required properties
     * @throws GeneralSecurityException If re-keying fails
     */
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

    /**
     * Stores encryption parameters in the storage metadata.
     * This method adds the X25519 key encapsulation to the storage properties.
     *
     * @param storage The storage metadata
     * @param ret The encryption parameters to store
     */
    protected void storeEncryptionParameters(BackupBlockStorage storage, IdentityKeys.EncryptionParameters ret) {
        storage.getProperties().put(X25519_KEY, Hash.encodeBytes(ret.getKeys().get(X25519_KEY).getEncapsulation()));
    }

    /**
     * Gets the set of encryption key types used by this encryptor.
     * By default, this returns only the X25519 key type.
     *
     * @return The set of encryption key types
     */
    protected Set<String> getEncryptionKeys() {
        return KEY_TYPES_X25519;
    }

    /**
     * Creates encapsulated keys from storage metadata.
     * This method extracts the public key from storage properties and creates an encapsulated key.
     *
     * @param storage The storage metadata containing the public key
     * @return A map of key types to encapsulated keys
     */
    protected Map<String, PublicKeyMethod.EncapsulatedKey> createEncapsulatedKeys(BackupBlockStorage storage) {
        byte[] blockPublicKey = Hash.decodeBytes(storage.getProperties().get(X25519_KEY));
        return Map.of(X25519_KEY, new PublicKeyMethod.EncapsulatedKey(blockPublicKey));
    }
}
